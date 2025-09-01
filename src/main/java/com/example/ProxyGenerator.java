package com.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProxyGenerator {
    public static void generate(List<Map<String, Object>> proxies) throws Exception {
        if (proxies == null) return;
        
        for (Map<String, Object> proxy : proxies) {
            String name = (String) proxy.get("name");
            String basePath = (String) proxy.get("basePath");
            String description = proxy.containsKey("description") ? 
                (String) proxy.get("description") : "Generated API Proxy";
            
            System.out.println("Generating proxy bundle for: " + name);
            
            // Create proxy directory structure
            String proxyDir = "apiproxies/" + name;
            Files.createDirectories(Paths.get(proxyDir));
            Files.createDirectories(Paths.get(proxyDir + "/policies"));
            Files.createDirectories(Paths.get(proxyDir + "/proxies"));
            Files.createDirectories(Paths.get(proxyDir + "/targets"));
            Files.createDirectories(Paths.get(proxyDir + "/resources/jsc"));
            
            // Generate main proxy XML
            StringBuilder proxyXml = new StringBuilder();
            proxyXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            proxyXml.append("<APIProxy name=\"").append(name).append("\">\n");
            proxyXml.append("    <Description>").append(description).append("</Description>\n");
            proxyXml.append("    <BasePaths>\n");
            proxyXml.append("        <BasePath>").append(basePath).append("</BasePath>\n");
            proxyXml.append("    </BasePaths>\n");
            
            // Add policies section if policies exist
            if (proxy.containsKey("policies")) {
                List<Map<String, Object>> policies = (List<Map<String, Object>>) proxy.get("policies");
                generatePolicies(proxyDir, policies);
                
                proxyXml.append("    <Policies>\n");
                for (Map<String, Object> policy : policies) {
                    String policyName = (String) policy.get("name");
                    proxyXml.append("        <Policy>").append(policyName).append("</Policy>\n");
                }
                proxyXml.append("    </Policies>\n");
            }
            
            // Handle dependencies
            if (proxy.containsKey("dependencies")) {
                List<Map<String, Object>> dependencies = (List<Map<String, Object>>) proxy.get("dependencies");
                for (Map<String, Object> dep : dependencies) {
                    if (dep.containsKey("sharedFlow")) {
                        String sharedFlow = (String) dep.get("sharedFlow");
                        // Create FlowCallout policy for the shared flow
                        String flowCalloutName = "flowCallout-" + sharedFlow;
                        StringBuilder fcPolicy = new StringBuilder();
                        fcPolicy.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
                        fcPolicy.append("<FlowCallout name=\"").append(flowCalloutName).append("\">\n");
                        fcPolicy.append("    <DisplayName>Call ").append(sharedFlow).append("</DisplayName>\n");
                        fcPolicy.append("    <SharedFlowBundle>").append(sharedFlow).append("</SharedFlowBundle>\n");
                        fcPolicy.append("</FlowCallout>\n");
                        
                        Files.write(Paths.get(proxyDir + "/policies/" + flowCalloutName + ".xml"), 
                                   fcPolicy.toString().getBytes());
                        
                        // Add the policy to the main proxy XML
                        proxyXml.append("    <Policies>\n");
                        proxyXml.append("        <Policy>").append(flowCalloutName).append("</Policy>\n");
                        proxyXml.append("    </Policies>\n");
                    }
                }
            }
            
            // Add proxy endpoints
            proxyXml.append("    <ProxyEndpoints>\n");
            if (proxy.containsKey("endpoints")) {
                List<Map<String, Object>> endpoints = (List<Map<String, Object>>) proxy.get("endpoints");
                for (Map<String, Object> endpoint : endpoints) {
                    String endpointName = (String) endpoint.get("name");
                    proxyXml.append("        <ProxyEndpoint>").append(endpointName).append("</ProxyEndpoint>\n");
                    generateProxyEndpoint(proxyDir, endpoint);
                }
            } else {
                proxyXml.append("        <ProxyEndpoint>default</ProxyEndpoint>\n");
                generateDefaultProxyEndpoint(proxyDir, basePath);
            }
            proxyXml.append("    </ProxyEndpoints>\n");
            
            // Add target endpoints
            proxyXml.append("    <TargetEndpoints>\n");
            if (proxy.containsKey("targetEndpoints")) {
                List<Map<String, Object>> targets = (List<Map<String, Object>>) proxy.get("targetEndpoints");
                for (Map<String, Object> target : targets) {
                    String targetName = (String) target.get("name");
                    proxyXml.append("        <TargetEndpoint>").append(targetName).append("</TargetEndpoint>\n");
                    generateTargetEndpoint(proxyDir, target);
                }
            } else {
                proxyXml.append("        <TargetEndpoint>default</TargetEndpoint>\n");
                generateDefaultTargetEndpoint(proxyDir);
            }
            proxyXml.append("    </TargetEndpoints>\n");
            
            proxyXml.append("</APIProxy>\n");
            
            // Write main proxy XML file
            Files.write(Paths.get(proxyDir + "/" + name + ".xml"), proxyXml.toString().getBytes());
            
            // Create a zip bundle for deployment
            createZipBundle(proxyDir, name);
        }
    }
    
    private static void generatePolicies(String proxyDir, List<Map<String, Object>> policies) throws Exception {
        for (Map<String, Object> policy : policies) {
            String name = (String) policy.get("name");
            String type = (String) policy.get("type");
            String displayName = policy.containsKey("displayName") ? 
                (String) policy.get("displayName") : name;
            
            StringBuilder policyXml = new StringBuilder();
            policyXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            
            switch (type) {
                case "VerifyAPIKey":
                    Map<String, Object> config = (Map<String, Object>) policy.get("configuration");
                    String apiKey = (String) config.get("apiKey");
                    policyXml.append("<VerifyAPIKey name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    policyXml.append("    <APIKey ref=\"").append(apiKey).append("\"/>\n");
                    policyXml.append("</VerifyAPIKey>\n");
                    break;
                
                case "Quota":
                    Map<String, Object> quotaConfig = (Map<String, Object>) policy.get("configuration");
                    Integer limit = (Integer) quotaConfig.get("limit");
                    String timeUnit = (String) quotaConfig.get("timeUnit");
                    policyXml.append("<Quota name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    policyXml.append("    <Allow>").append(limit).append("</Allow>\n");
                    policyXml.append("    <Interval>1</Interval>\n");
                    policyXml.append("    <TimeUnit>").append(timeUnit).append("</TimeUnit>\n");
                    policyXml.append("</Quota>\n");
                    break;
                
                case "JavaScript":
                    Map<String, Object> jsConfig = (Map<String, Object>) policy.get("configuration");
                    policyXml.append("<Javascript name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    
                    if (jsConfig.containsKey("timeoutInMillis")) {
                        policyXml.append("    <TimeoutInMillis>").append(jsConfig.get("timeoutInMillis")).append("</TimeoutInMillis>\n");
                    }
                    
                    // Handle script file
                    if (jsConfig.containsKey("scriptFile")) {
                        String scriptFile = (String) jsConfig.get("scriptFile");
                        policyXml.append("    <ResourceURL>jsc://").append(scriptFile).append("</ResourceURL>\n");
                        
                        // If script content is provided, write it to a file
                        if (jsConfig.containsKey("scriptContent")) {
                            String scriptContent = (String) jsConfig.get("scriptContent");
                            Files.write(Paths.get(proxyDir + "/resources/jsc/" + scriptFile), 
                                       scriptContent.getBytes());
                        }
                    }
                    
                    policyXml.append("</Javascript>\n");
                    break;
                
                // Add more policy types as needed
                    
                default:
                    // Generic policy template for other types
                    policyXml.append("<").append(type).append(" name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    policyXml.append("</").append(type).append(">\n");
            }
            
            Files.write(Paths.get(proxyDir + "/policies/" + name + ".xml"), policyXml.toString().getBytes());
        }
    }
    
    private static void generateProxyEndpoint(String proxyDir, Map<String, Object> endpoint) throws Exception {
        String name = (String) endpoint.get("name");
        String basePath = endpoint.containsKey("basePath") ? 
            (String) endpoint.get("basePath") : "/";
        
        StringBuilder endpointXml = new StringBuilder();
        endpointXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        endpointXml.append("<ProxyEndpoint name=\"").append(name).append("\">\n");
        endpointXml.append("    <PreFlow name=\"PreFlow\">\n");
        endpointXml.append("        <Request/>\n");
        endpointXml.append("        <Response/>\n");
        endpointXml.append("    </PreFlow>\n");
        
        // Add flows if they exist
        if (endpoint.containsKey("flows")) {
            endpointXml.append("    <Flows>\n");
            List<Map<String, Object>> flows = (List<Map<String, Object>>) endpoint.get("flows");
            for (Map<String, Object> flow : flows) {
                String flowName = (String) flow.get("name");
                String condition = flow.containsKey("condition") ? 
                    (String) flow.get("condition") : "";
                
                endpointXml.append("        <Flow name=\"").append(flowName).append("\">\n");
                if (!condition.isEmpty()) {
                    endpointXml.append("            <Condition>").append(condition).append("</Condition>\n");
                }
                
                // Request policies
                endpointXml.append("            <Request>\n");
                if (flow.containsKey("request")) {
                    List<Map<String, Object>> requestSteps = (List<Map<String, Object>>) flow.get("request");
                    for (Map<String, Object> step : requestSteps) {
                        if (step.containsKey("policy")) {
                            String policy = (String) step.get("policy");
                            endpointXml.append("                <Step>\n");
                            endpointXml.append("                    <Name>").append(policy).append("</Name>\n");
                            endpointXml.append("                </Step>\n");
                        } else if (step.containsKey("sharedFlow")) {
                            String sharedFlow = (String) step.get("sharedFlow");
                            endpointXml.append("                <Step>\n");
                            endpointXml.append("                    <Name>flowCallout-").append(sharedFlow).append("</Name>\n");
                            endpointXml.append("                </Step>\n");
                        }
                    }
                }
                endpointXml.append("            </Request>\n");
                
                // Response policies
                endpointXml.append("            <Response>\n");
                if (flow.containsKey("response")) {
                    List<Map<String, Object>> responseSteps = (List<Map<String, Object>>) flow.get("response");
                    for (Map<String, Object> step : responseSteps) {
                        if (step.containsKey("policy")) {
                            String policy = (String) step.get("policy");
                            endpointXml.append("                <Step>\n");
                            endpointXml.append("                    <Name>").append(policy).append("</Name>\n");
                            endpointXml.append("                </Step>\n");
                        }
                    }
                }
                endpointXml.append("            </Response>\n");
                
                endpointXml.append("        </Flow>\n");
            }
            endpointXml.append("    </Flows>\n");
        }
        
        endpointXml.append("    <PostFlow name=\"PostFlow\">\n");
        endpointXml.append("        <Request/>\n");
        endpointXml.append("        <Response/>\n");
        endpointXml.append("    </PostFlow>\n");
        
        endpointXml.append("    <HTTPProxyConnection>\n");
        endpointXml.append("        <BasePath>").append(basePath).append("</BasePath>\n");
        endpointXml.append("    </HTTPProxyConnection>\n");
        
        endpointXml.append("    <RouteRule name=\"default\">\n");
        endpointXml.append("        <TargetEndpoint>default</TargetEndpoint>\n");
        endpointXml.append("    </RouteRule>\n");
        
        endpointXml.append("</ProxyEndpoint>\n");
        
        Files.write(Paths.get(proxyDir + "/proxies/" + name + ".xml"), endpointXml.toString().getBytes());
    }
    
    private static void generateDefaultProxyEndpoint(String proxyDir, String basePath) throws Exception {
        StringBuilder endpointXml = new StringBuilder();
        endpointXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        endpointXml.append("<ProxyEndpoint name=\"default\">\n");
        endpointXml.append("    <PreFlow name=\"PreFlow\">\n");
        endpointXml.append("        <Request/>\n");
        endpointXml.append("        <Response/>\n");
        endpointXml.append("    </PreFlow>\n");
        endpointXml.append("    <Flows/>\n");
        endpointXml.append("    <PostFlow name=\"PostFlow\">\n");
        endpointXml.append("        <Request/>\n");
        endpointXml.append("        <Response/>\n");
        endpointXml.append("    </PostFlow>\n");
        endpointXml.append("    <HTTPProxyConnection>\n");
        endpointXml.append("        <BasePath>").append(basePath).append("</BasePath>\n");
        endpointXml.append("    </HTTPProxyConnection>\n");
        endpointXml.append("    <RouteRule name=\"default\">\n");
        endpointXml.append("        <TargetEndpoint>default</TargetEndpoint>\n");
        endpointXml.append("    </RouteRule>\n");
        endpointXml.append("</ProxyEndpoint>\n");
        
        Files.write(Paths.get(proxyDir + "/proxies/default.xml"), endpointXml.toString().getBytes());
    }
    
    private static void generateTargetEndpoint(String proxyDir, Map<String, Object> target) throws Exception {
        String name = (String) target.get("name");
        String url = target.containsKey("url") ? 
            (String) target.get("url") : "https://example.com";
        
        StringBuilder targetXml = new StringBuilder();
        targetXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        targetXml.append("<TargetEndpoint name=\"").append(name).append("\">\n");
        targetXml.append("    <PreFlow name=\"PreFlow\">\n");
        targetXml.append("        <Request/>\n");
        targetXml.append("        <Response/>\n");
        targetXml.append("    </PreFlow>\n");
        targetXml.append("    <Flows/>\n");
        targetXml.append("    <PostFlow name=\"PostFlow\">\n");
        targetXml.append("        <Request/>\n");
        targetXml.append("        <Response/>\n");
        targetXml.append("    </PostFlow>\n");
        targetXml.append("    <HTTPTargetConnection>\n");
        targetXml.append("        <URL>").append(url).append("</URL>\n");
        targetXml.append("    </HTTPTargetConnection>\n");
        targetXml.append("</TargetEndpoint>\n");
        
        Files.write(Paths.get(proxyDir + "/targets/" + name + ".xml"), targetXml.toString().getBytes());
    }
    
    private static void generateDefaultTargetEndpoint(String proxyDir) throws Exception {
        StringBuilder targetXml = new StringBuilder();
        targetXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        targetXml.append("<TargetEndpoint name=\"default\">\n");
        targetXml.append("    <PreFlow name=\"PreFlow\">\n");
        targetXml.append("        <Request/>\n");
        targetXml.append("        <Response/>\n");
        targetXml.append("    </PreFlow>\n");
        targetXml.append("    <Flows/>\n");
        targetXml.append("    <PostFlow name=\"PostFlow\">\n");
        targetXml.append("        <Request/>\n");
        targetXml.append("        <Response/>\n");
        targetXml.append("    </PostFlow>\n");
        targetXml.append("    <HTTPTargetConnection>\n");
        targetXml.append("        <URL>https://api.example.com</URL>\n");
        targetXml.append("    </HTTPTargetConnection>\n");
        targetXml.append("</TargetEndpoint>\n");
        
        Files.write(Paths.get(proxyDir + "/targets/default.xml"), targetXml.toString().getBytes());
    }
    
    private static void createZipBundle(String proxyDir, String name) throws Exception {
        String zipFileName = proxyDir + "/" + name + ".zip";
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            File proxyDirFile = new File(proxyDir);
            zipDirectory(proxyDirFile, name, zipOut);
        }
        System.out.println("Created ZIP bundle: " + zipFileName);
    }
    
    private static void zipDirectory(File folder, String parentFolder, ZipOutputStream zipOut) throws Exception {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zipOut);
                continue;
            }
            
            // Skip the zip file itself
            if (file.getName().endsWith(".zip")) continue;
            
            FileInputStream fis = new FileInputStream(file);
            String entryPath = parentFolder + "/" + file.getName();
            ZipEntry zipEntry = new ZipEntry(entryPath);
            zipOut.putNextEntry(zipEntry);
            
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }
    }
}