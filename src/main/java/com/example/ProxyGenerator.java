package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generator class for Apigee API proxies.
 * Creates the bundle structure and related files based on the design YAML.
 */
public class ProxyGenerator {
    private final String outputDir;

    public ProxyGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Generates an Apigee proxy bundle based on YAML configuration.
     * 
     * @param proxyConfig The proxy configuration from YAML
     * @param designName The name of the design (used for grouping)
     * @param sharedFlows Available shared flows for dependency resolution
     */
    public void generateProxy(Map<String, Object> proxyConfig, String designName,
                             List<Map<String, Object>> sharedFlows) throws IOException {
        String name = (String) proxyConfig.get("name");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Proxy must have a name");
        }
        
        System.out.println("Generating proxy: " + name);
        
        // Create proxy directory structure
        String proxyDir = outputDir + "/" + name;
        Files.createDirectories(Paths.get(proxyDir));
        
        // Create standard Apigee proxy bundle structure
        Path apiproxyPath = Paths.get(proxyDir, "apiproxy");
        Files.createDirectories(apiproxyPath);
        
        // Create policies directory
        Path policiesPath = Paths.get(apiproxyPath.toString(), "policies");
        Files.createDirectories(policiesPath);
        
        // Create proxies directory (for ProxyEndpoint configuration)
        Path proxiesPath = Paths.get(apiproxyPath.toString(), "proxies");
        Files.createDirectories(proxiesPath);
        
        // Create targets directory (for TargetEndpoint configuration)
        Path targetsPath = Paths.get(apiproxyPath.toString(), "targets");
        Files.createDirectories(targetsPath);
        
        // Generate policies
        if (proxyConfig.containsKey("policies")) {
            List<Map<String, Object>> policies = (List<Map<String, Object>>) proxyConfig.get("policies");
            generatePolicies(policiesPath, policies);
        }
        
        // Generate proxy endpoint
        generateProxyEndpoint(proxiesPath, name, proxyConfig);
        
        // Generate target endpoint
        generateTargetEndpoint(targetsPath, name, proxyConfig);
        
        // Generate main proxy XML file
        generateProxyXml(apiproxyPath, name, proxyConfig);
        
        // Generate POM file
        generatePom(proxyDir, name, designName, proxyConfig, sharedFlows);
        
        System.out.println("Successfully generated proxy bundle: " + name);
    }

    /**
     * Generates policy XML files.
     */
    private void generatePolicies(Path policiesPath, List<Map<String, Object>> policies) throws IOException {
        for (Map<String, Object> policy : policies) {
            String policyName = (String) policy.get("name");
            String policyType = (String) policy.get("type");
            Map<String, Object> configuration = (Map<String, Object>) policy.getOrDefault("configuration", Map.of());
            
            // Generate policy XML based on type
            String policyXml = generatePolicyXml(policyName, policyType, configuration);
            
            // Write policy XML file
            Path policyFile = Paths.get(policiesPath.toString(), policyName + ".xml");
            Files.writeString(policyFile, policyXml);
        }
    }

    /**
     * Generates policy XML content based on type and configuration.
     */
    private String generatePolicyXml(String policyName, String policyType, Map<String, Object> configuration) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        
        // Generate XML based on policy type
        switch (policyType) {
            case "Quota":
                xml.append("<Quota name=\"").append(policyName).append("\">\n");
                xml.append("    <DisplayName>").append(policyName).append("</DisplayName>\n");
                xml.append("    <Allow count=\"").append(configuration.getOrDefault("limit", 1000)).append("\"/>\n");
                xml.append("    <Interval>").append(1).append("</Interval>\n");
                xml.append("    <TimeUnit>").append(configuration.getOrDefault("timeUnit", "minute")).append("</TimeUnit>\n");
                xml.append("</Quota>\n");
                break;
                
            case "SpikeArrest":
                xml.append("<SpikeArrest name=\"").append(policyName).append("\">\n");
                xml.append("    <DisplayName>").append(policyName).append("</DisplayName>\n");
                xml.append("    <Rate>").append(configuration.getOrDefault("rate", "30ps")).append("</Rate>\n");
                xml.append("</SpikeArrest>\n");
                break;
                
            default:
                xml.append("<!-- Placeholder for ").append(policyType).append(" policy: ").append(policyName).append(" -->\n");
                xml.append("<Policy name=\"").append(policyName).append("\">\n");
                xml.append("    <DisplayName>").append(policyName).append("</DisplayName>\n");
                xml.append("    <!-- Configuration would go here -->\n");
                xml.append("</Policy>\n");
        }
        
        return xml.toString();
    }

    /**
     * Generates the proxy endpoint XML file.
     */
    private void generateProxyEndpoint(Path proxiesPath, String name, Map<String, Object> proxyConfig) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<ProxyEndpoint name=\"default\">\n");
        xml.append("    <Description/>\n");
        xml.append("    <FaultRules/>\n");
        xml.append("    <PreFlow name=\"PreFlow\">\n");
        xml.append("        <Request/>\n");
        xml.append("        <Response/>\n");
        xml.append("    </PreFlow>\n");
        
        // Add flow definitions if provided
        if (proxyConfig.containsKey("flows")) {
            List<Map<String, Object>> flows = (List<Map<String, Object>>) proxyConfig.get("flows");
            for (Map<String, Object> flow : flows) {
                String flowName = (String) flow.get("name");
                String condition = (String) flow.getOrDefault("condition", "true");
                
                xml.append("    <Flow name=\"").append(flowName).append("\">\n");
                xml.append("        <Condition>").append(condition).append("</Condition>\n");
                xml.append("        <Request>\n");
                
                // Add policy references for request flow
                if (flow.containsKey("request")) {
                    List<Map<String, Object>> requestPolicies = (List<Map<String, Object>>) flow.get("request");
                    for (Map<String, Object> policy : requestPolicies) {
                        String policyName = (String) policy.get("policy");
                        xml.append("            <Step>\n");
                        xml.append("                <Name>").append(policyName).append("</Name>\n");
                        xml.append("            </Step>\n");
                    }
                }
                
                xml.append("        </Request>\n");
                xml.append("        <Response>\n");
                
                // Add policy references for response flow
                if (flow.containsKey("response")) {
                    List<Map<String, Object>> responsePolicies = (List<Map<String, Object>>) flow.get("response");
                    for (Map<String, Object> policy : responsePolicies) {
                        String policyName = (String) policy.get("policy");
                        xml.append("            <Step>\n");
                        xml.append("                <Name>").append(policyName).append("</Name>\n");
                        xml.append("            </Step>\n");
                    }
                }
                
                xml.append("        </Response>\n");
                xml.append("    </Flow>\n");
            }
        }
        
        xml.append("    <PostFlow name=\"PostFlow\">\n");
        xml.append("        <Request/>\n");
        xml.append("        <Response/>\n");
        xml.append("    </PostFlow>\n");
        
        // Add HTTP proxy configuration
        xml.append("    <HTTPProxyConnection>\n");
        String basePath = (String) proxyConfig.getOrDefault("basePath", "/" + name);
        xml.append("        <BasePath>").append(basePath).append("</BasePath>\n");
        xml.append("        <Properties/>\n");
        xml.append("    </HTTPProxyConnection>\n");
        
        // Add target endpoint reference
        xml.append("    <RouteRule name=\"default\">\n");
        xml.append("        <TargetEndpoint>default</TargetEndpoint>\n");
        xml.append("    </RouteRule>\n");
        xml.append("</ProxyEndpoint>\n");
        
        // Write ProxyEndpoint XML file
        Path proxyEndpointFile = Paths.get(proxiesPath.toString(), "default.xml");
        Files.writeString(proxyEndpointFile, xml.toString());
    }

    /**
     * Generates the target endpoint XML file.
     */
    private void generateTargetEndpoint(Path targetsPath, String name, Map<String, Object> proxyConfig) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<TargetEndpoint name=\"default\">\n");
        xml.append("    <Description/>\n");
        xml.append("    <FaultRules/>\n");
        xml.append("    <PreFlow name=\"PreFlow\">\n");
        xml.append("        <Request/>\n");
        xml.append("        <Response/>\n");
        xml.append("    </PreFlow>\n");
        xml.append("    <PostFlow name=\"PostFlow\">\n");
        xml.append("        <Request/>\n");
        xml.append("        <Response/>\n");
        xml.append("    </PostFlow>\n");
        
        // Add HTTP target configuration
        xml.append("    <HTTPTargetConnection>\n");
        String target = (String) proxyConfig.getOrDefault("target", "https://mocktarget.apigee.net");
        xml.append("        <URL>").append(target).append("</URL>\n");
        xml.append("    </HTTPTargetConnection>\n");
        xml.append("</TargetEndpoint>\n");
        
        // Write TargetEndpoint XML file
        Path targetEndpointFile = Paths.get(targetsPath.toString(), "default.xml");
        Files.writeString(targetEndpointFile, xml.toString());
    }

    /**
     * Generates the main proxy XML file.
     */
    private void generateProxyXml(Path apiproxyPath, String name, Map<String, Object> proxyConfig) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<APIProxy revision=\"1\" name=\"").append(name).append("\">\n");
        
        // Add description if provided
        String description = (String) proxyConfig.getOrDefault("description", "");
        if (!description.isEmpty()) {
            xml.append("    <Description>").append(description).append("</Description>\n");
        }
        
        // Add display name
        xml.append("    <DisplayName>").append(name).append("</DisplayName>\n");
        
        // Add shared flow reference if specified
        if (proxyConfig.containsKey("dependencies") && 
            ((Map<String, Object>) proxyConfig.get("dependencies")).containsKey("sharedFlows")) {
            
            List<String> sharedFlowRefs = (List<String>) ((Map<String, Object>) proxyConfig.get("dependencies")).get("sharedFlows");
            if (!sharedFlowRefs.isEmpty()) {
                for (String sharedFlowRef : sharedFlowRefs) {
                    xml.append("    <DependsOn>sf:").append(sharedFlowRef).append("</DependsOn>\n");
                }
            }
        }
        
        // Add proxy element references
        xml.append("    <ProxyEndpoints>\n");
        xml.append("        <ProxyEndpoint>default</ProxyEndpoint>\n");
        xml.append("    </ProxyEndpoints>\n");
        
        xml.append("    <TargetEndpoints>\n");
        xml.append("        <TargetEndpoint>default</TargetEndpoint>\n");
        xml.append("    </TargetEndpoints>\n");
        
        xml.append("</APIProxy>\n");
        
        // Write APIProxy XML file
        Path apiProxyFile = Paths.get(apiproxyPath.toString(), name + ".xml");
        Files.writeString(apiProxyFile, xml.toString());
    }

    /**
     * Generates Maven POM file for the proxy.
     */
    private void generatePom(String proxyDir, String name, String designName, Map<String, Object> proxyConfig,
                            List<Map<String, Object>> sharedFlows) throws IOException {
        StringBuilder pom = new StringBuilder();
        pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        pom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        pom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        pom.append("    <modelVersion>4.0.0</modelVersion>\n\n");
        
        pom.append("    <groupId>com.apigee.edge.proxy.").append(designName).append("</groupId>\n");
        pom.append("    <artifactId>").append(name).append("</artifactId>\n");
        pom.append("    <version>1.0</version>\n");
        pom.append("    <packaging>pom</packaging>\n\n");
        
        // Add dependencies for shared flows if specified
        List<String> sharedFlowRefs = new ArrayList<>();
        if (proxyConfig.containsKey("dependencies") && 
            ((Map<String, Object>) proxyConfig.get("dependencies")).containsKey("sharedFlows")) {
            
            sharedFlowRefs = (List<String>) ((Map<String, Object>) proxyConfig.get("dependencies")).get("sharedFlows");
            
            if (!sharedFlowRefs.isEmpty()) {
                pom.append("    <dependencies>\n");
                
                for (String sharedFlowRef : sharedFlowRefs) {
                    // Find the matching shared flow from the list
                    for (Map<String, Object> sharedFlow : sharedFlows) {
                        String sharedFlowName = (String) sharedFlow.get("name");
                        if (sharedFlowName.equals(sharedFlowRef)) {
                            pom.append("        <dependency>\n");
                            pom.append("            <groupId>com.apigee.edge.sharedflow.").append(designName).append("</groupId>\n");
                            pom.append("            <artifactId>").append(sharedFlowName).append("</artifactId>\n");
                            pom.append("            <version>1.0</version>\n");
                            pom.append("        </dependency>\n");
                            break;
                        }
                    }
                }
                
                pom.append("    </dependencies>\n\n");
            }
        }
        
        pom.append("    <properties>\n");
        pom.append("        <apigee.org>${env.APIGEE_ORG}</apigee.org>\n");
        pom.append("        <apigee.env>${env.APIGEE_ENV}</apigee.env>\n");
        pom.append("        <apigee.config.options>update</apigee.config.options>\n");
        pom.append("        <apigee.config.dir>${project.basedir}</apigee.config.dir>\n");
        pom.append("    </properties>\n\n");
        
        // Add build configuration with apigee-config-maven-plugin
        pom.append("    <build>\n");
        pom.append("        <plugins>\n");
        pom.append("            <plugin>\n");
        pom.append("                <groupId>com.apigee.edge.config</groupId>\n");
        pom.append("                <artifactId>apigee-config-maven-plugin</artifactId>\n");
        pom.append("                <version>2.3.0</version>\n");
        pom.append("                <executions>\n");
        pom.append("                    <execution>\n");
        pom.append("                        <id>create-config-proxy</id>\n");
        pom.append("                        <phase>install</phase>\n");
        pom.append("                        <goals>\n");
        pom.append("                            <goal>proxies</goal>\n");
        pom.append("                        </goals>\n");
        pom.append("                    </execution>\n");
        pom.append("                </executions>\n");
        pom.append("            </plugin>\n");
        pom.append("        </plugins>\n");
        pom.append("    </build>\n");
        pom.append("</project>\n");
        
        // Write POM file
        Path pomFile = Paths.get(proxyDir, "pom.xml");
        Files.writeString(pomFile, pom.toString());
    }
}