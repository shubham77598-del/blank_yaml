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

        for (File f : designFiles) {
            System.out.println("Processing design file: " + f.getName());
            Map<String,Object> root = loadYaml(f);
            if (root == null) continue;

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

            // Shared Flows first
            for (Map<String,Object> sf : listOfMaps(apigee.get("sharedFlows"))) {
                sfGen.generateSharedFlow(sf, designName);
            }

            // Proxies
            for (Map<String,Object> px : listOfMaps(apigee.get("proxies"))) {
                proxyGen.generateProxy(px, designName);
            }

            // API Products (config)
            List<Map<String,Object>> products = listOfMaps(apigee.get("apiProducts"));
            if (!products.isEmpty()) {
                ConfigGenerator.generateApiProducts(products, CONFIG_ENTITIES);
                ConfigGenerator.finalizeConfigModule(CONFIG_ROOT);
            }
        }

        System.out.println("Generation complete. See 'generated/' directory.");
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
}