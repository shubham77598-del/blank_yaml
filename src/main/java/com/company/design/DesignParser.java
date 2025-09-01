package com.company.design;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.*;
import java.util.*;

/**
 * Reads all YAML design files in designs/ and generates:
 *  - Shared Flow modules
 *  - Proxy modules
 *  - Config (API Products) module
 * Then CI will deploy them in the correct order.
 */
public class DesignParser {

    private static final String DESIGNS_DIR = "designs";
    private static final String GENERATED_ROOT = "generated";
    private static final String SHARED_OUT = GENERATED_ROOT + "/sharedflows";
    private static final String PROXY_OUT = GENERATED_ROOT + "/proxies";
    private static final String CONFIG_ROOT = GENERATED_ROOT + "/config";
    private static final String CONFIG_ENTITIES = CONFIG_ROOT + "/entities";
    
    private static final Set<String> knownPolicyTypes = new HashSet<>(Arrays.asList(
        "Quota", "VerifyAPIKey", "JavaScript", "AssignMessage", "SpikeArrest", "JSONThreatProtection",
        "OAuthV2", "ServiceCallout", "CORS", "MessageLogging", "RateLimiter"
    ));

    public static void main(String[] args) throws Exception {
        System.out.println("=== Apigee Design Parser ===");
        cleanGeneratedIfRequested();
        prepareDirs();

        List<File> designFiles = findDesignFiles();
        if (designFiles.isEmpty()) {
            System.out.println("No design YAML files found in " + DESIGNS_DIR + "/");
            return;
        }

        SharedFlowGenerator sfGen = new SharedFlowGenerator(SHARED_OUT);
        ProxyGenerator proxyGen = new ProxyGenerator(PROXY_OUT);
        
        // Track all shared flows and policies for validation
        Set<String> allSharedFlows = new HashSet<>();
        Set<String> allPolicies = new HashSet<>();

        for (File f : designFiles) {
            System.out.println("Processing design file: " + f.getName());
            Map<String,Object> root = loadYaml(f);
            if (root == null) continue;

            // Support environment variable substitution
            root = substituteEnvironmentVariables(root);

            Object apigeeObj = root.get("apigee");
            if (!(apigeeObj instanceof Map)) {
                System.out.println("  Skipping: 'apigee' root not found.");
                continue;
            }
            Map apigee = (Map) apigeeObj;

            String designName = str(apigee.get("name"));
            if (designName == null || designName.trim().isEmpty()) {
                designName = stripExt(f.getName());
            }
            
            // Validate structure
            if (!validateDesignStructure(apigee, f.getName())) {
                System.out.println("  [ERROR] Design file " + f.getName() + " has validation errors. Skipping.");
                continue;
            }

            // Shared Flows first
            for (Map<String,Object> sf : listOfMaps(apigee.get("sharedFlows"))) {
                String sfName = str(sf.get("name"));
                if (sfName != null) {
                    allSharedFlows.add(sfName);
                    // Track policies in this shared flow
                    for (Map<String,Object> policy : listOfMaps(sf.get("policies"))) {
                        String policyName = str(policy.get("name"));
                        if (policyName != null) allPolicies.add(policyName);
                    }
                }
                sfGen.generateSharedFlow(sf, designName);
            }

            // Proxies
            for (Map<String,Object> px : listOfMaps(apigee.get("proxies"))) {
                String pxName = str(px.get("name"));
                if (pxName != null) {
                    // Validate proxy dependencies
                    if (!validateProxyDependencies(px, allSharedFlows, pxName)) {
                        System.out.println("  [ERROR] Proxy " + pxName + " has missing shared flow dependencies. Skipping.");
                        continue;
                    }
                    // Track policies in this proxy
                    for (Map<String,Object> policy : listOfMaps(px.get("policies"))) {
                        String policyName = str(policy.get("name"));
                        if (policyName != null) allPolicies.add(policyName);
                    }
                }
                proxyGen.generateProxy(px, designName);
            }

            // API Products (config)
            List<Map<String,Object>> products = listOfMaps(apigee.get("apiProducts"));
            if (!products.isEmpty()) {
                ConfigGenerator.generateApiProducts(products, CONFIG_ENTITIES);
                ConfigGenerator.finalizeConfigModule(CONFIG_ROOT);
            }
            
            // Developers (config)
            List<Map<String,Object>> developers = listOfMaps(apigee.get("developers"));
            if (!developers.isEmpty()) {
                ConfigGenerator.generateDevelopers(developers, CONFIG_ENTITIES);
                ConfigGenerator.finalizeConfigModule(CONFIG_ROOT);
            }
        }

        System.out.println("Generation complete. See 'generated/' directory.");
        System.out.println("Generated shared flows: " + allSharedFlows.size());
        System.out.println("Total policies: " + allPolicies.size());
    }

    private static void cleanGeneratedIfRequested() throws Exception {
        String flag = System.getProperty("cleanGenerated");
        if ("true".equalsIgnoreCase(flag)) {
            Path g = Paths.get(GENERATED_ROOT);
            if (Files.exists(g)) {
                System.out.println("Cleaning existing generated/ directory...");
                deleteRecursively(g);
            }
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        Files.walk(root)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
    }

    private static void prepareDirs() throws Exception {
        Files.createDirectories(Paths.get(SHARED_OUT));
        Files.createDirectories(Paths.get(PROXY_OUT));
        Files.createDirectories(Paths.get(CONFIG_ENTITIES));
    }

    private static List<File> findDesignFiles() {
        File d = new File(DESIGNS_DIR);
        if (!d.isDirectory()) return Collections.emptyList();
        File[] arr = d.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ENGLISH);
            return lower.endsWith(".yaml") || lower.endsWith(".yml");
        });
        if (arr == null) return Collections.emptyList();
        return Arrays.asList(arr);
    }

    private static Map<String,Object> loadYaml(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            Object o = new Yaml().load(in);
            if (o instanceof Map) return (Map<String,Object>) o;
            System.out.println("  Skipping: YAML root is not a map.");
            return null;
        } finally { in.close(); }
    }

    private static List<Map<String,Object>> listOfMaps(Object o) {
        List<Map<String,Object>> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List)o) {
                if (item instanceof Map) out.add((Map<String,Object>) item);
            }
        }
        return out;
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i == -1 ? n : n.substring(0, i);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    
    /**
     * Validates the overall structure of a design file
     */
    private static boolean validateDesignStructure(Map<String,Object> apigee, String fileName) {
        boolean valid = true;
        
        // Validate shared flows
        for (Map<String,Object> sf : listOfMaps(apigee.get("sharedFlows"))) {
            String name = str(sf.get("name"));
            if (name == null || name.trim().isEmpty()) {
                System.out.println("  [ERROR] SharedFlow missing name in " + fileName);
                valid = false;
                continue;
            }
            
            // Validate policies in shared flow
            for (Map<String,Object> policy : listOfMaps(sf.get("policies"))) {
                if (!validatePolicy(policy, name, fileName)) {
                    valid = false;
                }
            }
        }
        
        // Validate proxies
        for (Map<String,Object> px : listOfMaps(apigee.get("proxies"))) {
            String name = str(px.get("name"));
            if (name == null || name.trim().isEmpty()) {
                System.out.println("  [ERROR] Proxy missing name in " + fileName);
                valid = false;
                continue;
            }
            
            // Validate policies in proxy
            for (Map<String,Object> policy : listOfMaps(px.get("policies"))) {
                if (!validatePolicy(policy, name, fileName)) {
                    valid = false;
                }
            }
        }
        
        return valid;
    }
    
    /**
     * Validates a policy configuration
     */
    private static boolean validatePolicy(Map<String,Object> policy, String parentName, String fileName) {
        String policyName = str(policy.get("name"));
        String policyType = str(policy.get("type"));
        
        if (policyName == null || policyName.trim().isEmpty()) {
            System.out.println("  [ERROR] Policy missing name in " + parentName + " (" + fileName + ")");
            return false;
        }
        
        if (policyType == null || policyType.trim().isEmpty()) {
            System.out.println("  [ERROR] Policy " + policyName + " missing type in " + parentName + " (" + fileName + ")");
            return false;
        }
        
        // Warn about unknown policy types
        if (!knownPolicyTypes.contains(policyType)) {
            System.out.println("  [WARNING] Unknown policy type: " + policyType + " for policy " + policyName + " in " + parentName + " (" + fileName + ")");
        }
        
        return true;
    }
    
    /**
     * Validates proxy dependencies on shared flows
     */
    private static boolean validateProxyDependencies(Map<String,Object> proxy, Set<String> availableSharedFlows, String proxyName) {
        Map<String,Object> dependsOn = mapOf(proxy.get("dependsOn"));
        Object sharedFlows = dependsOn.get("sharedFlows");
        
        if (sharedFlows instanceof List) {
            for (Object sf : (List) sharedFlows) {
                String sfName = str(sf);
                if (sfName != null && !availableSharedFlows.contains(sfName)) {
                    System.out.println("  [ERROR] Proxy " + proxyName + " depends on shared flow '" + sfName + "' which is not defined");
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Substitutes environment variables in the YAML structure
     */
    @SuppressWarnings("unchecked")
    private static Map<String,Object> substituteEnvironmentVariables(Map<String,Object> root) {
        try {
            return (Map<String,Object>) substituteInObject(root);
        } catch (Exception e) {
            System.out.println("  [WARNING] Error during environment variable substitution: " + e.getMessage());
            return root;
        }
    }
    
    private static Object substituteInObject(Object obj) {
        if (obj instanceof Map) {
            Map<String,Object> map = (Map<String,Object>) obj;
            Map<String,Object> result = new HashMap<>();
            for (Map.Entry<String,Object> entry : map.entrySet()) {
                result.put(entry.getKey(), substituteInObject(entry.getValue()));
            }
            return result;
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(substituteInObject(item));
            }
            return result;
        } else if (obj instanceof String) {
            return substituteEnvironmentVariables((String) obj);
        }
        return obj;
    }
    
    private static String substituteEnvironmentVariables(String value) {
        if (value == null) return null;
        
        // Replace ${VAR_NAME} patterns with environment variables
        String result = value;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String envValue = System.getenv(envVar);
            if (envValue != null) {
                result = result.replace("${" + envVar + "}", envValue);
            } else {
                System.out.println("  [WARNING] Environment variable ${" + envVar + "} not found, leaving as-is");
            }
        }
        
        return result;
    }
    
    private static Map<String,Object> mapOf(Object o) {
        if (o instanceof Map) return (Map<String,Object>) o;
        return new HashMap<>();
    }
}