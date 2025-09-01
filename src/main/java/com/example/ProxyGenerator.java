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

    // [Other methods remain unchanged]

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
        
        // Add build configuration with apigee-edge-maven-plugin
        pom.append("    <build>\n");
        pom.append("        <plugins>\n");
        pom.append("            <plugin>\n");
        pom.append("                <groupId>io.apigee.build-tools.enterprise4g</groupId>\n");
        pom.append("                <artifactId>apigee-edge-maven-plugin</artifactId>\n");
        pom.append("                <version>1.2.1</version>\n");
        pom.append("                <executions>\n");
        pom.append("                    <execution>\n");
        pom.append("                        <id>configure-bundle</id>\n");
        pom.append("                        <phase>package</phase>\n");
        pom.append("                        <goals>\n");
        pom.append("                            <goal>configure</goal>\n");
        pom.append("                        </goals>\n");
        pom.append("                    </execution>\n");
        pom.append("                    <execution>\n");
        pom.append("                        <id>deploy-bundle</id>\n");
        pom.append("                        <phase>install</phase>\n");
        pom.append("                        <goals>\n");
        pom.append("                            <goal>deploy</goal>\n");
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