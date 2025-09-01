package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Generator class for Apigee shared flows.
 * Creates the bundle structure and related files based on the design YAML.
 */
public class SharedFlowGenerator {
    private final String outputDir;

    public SharedFlowGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Generates an Apigee shared flow bundle based on YAML configuration.
     */
    public void generateSharedFlow(Map<String, Object> sharedFlowConfig, String designName) throws IOException {
        String name = (String) sharedFlowConfig.get("name");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Shared flow must have a name");
        }
        
        System.out.println("Generating shared flow: " + name);
        
        // Create shared flow directory structure
        String sharedFlowDir = outputDir + "/" + name;
        Files.createDirectories(Paths.get(sharedFlowDir));
        
        // Create standard Apigee shared flow bundle structure
        Path sharedflowsPath = Paths.get(sharedFlowDir, "sharedflowbundle");
        Files.createDirectories(sharedflowsPath);
        
        // Create policies directory
        Path policiesPath = Paths.get(sharedflowsPath.toString(), "policies");
        Files.createDirectories(policiesPath);
        
        // Generate policies
        if (sharedFlowConfig.containsKey("policies")) {
            List<Map<String, Object>> policies = (List<Map<String, Object>>) sharedFlowConfig.get("policies");
            generatePolicies(policiesPath, policies);
        }
        
        // Generate shared flow XML file
        generateSharedFlowXml(sharedflowsPath, name, sharedFlowConfig);
        
        // Generate POM file
        generatePom(sharedFlowDir, name, designName);
        
        System.out.println("Successfully generated shared flow bundle: " + name);
    }

    /**
     * Generates policy XML files.
     */
    private void generatePolicies(Path policiesPath, List<Map<String, Object>> policies) throws IOException {
        // [Policy generation code remains the same]
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
        // [Policy XML generation code remains the same]
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        
        // Generate XML based on policy type
        switch (policyType) {
            case "VerifyAPIKey":
                xml.append("<VerifyAPIKey name=\"").append(policyName).append("\">\n");
                xml.append("    <DisplayName>").append(policyName).append("</DisplayName>\n");
                xml.append("    <Properties/>\n");
                
                // Add configuration options
                xml.append("    <APIKey ref=\"");
                xml.append(configuration.getOrDefault("keyLocation", "request.header.x-api-key"));
                xml.append("\"/>\n");
                
                xml.append("</VerifyAPIKey>\n");
                break;
                
            case "MessageLogging":
                xml.append("<MessageLogging name=\"").append(policyName).append("\">\n");
                xml.append("    <DisplayName>").append(policyName).append("</DisplayName>\n");
                xml.append("    <Loggers>\n");
                xml.append("        <Logger name=\"MessageLogger\">\n");
                xml.append("            <LogLevel>").append(configuration.getOrDefault("logLevel", "INFO")).append("</LogLevel>\n");
                boolean logToStdout = (Boolean) configuration.getOrDefault("logToStdout", true);
                if (logToStdout) {
                    xml.append("            <Source>stdout</Source>\n");
                }
                xml.append("            <Message>Message content: {request.content}</Message>\n");
                xml.append("        </Logger>\n");
                xml.append("    </Loggers>\n");
                xml.append("</MessageLogging>\n");
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
     * Generates the main shared flow XML file.
     */
    private void generateSharedFlowXml(Path sharedflowsPath, String name, Map<String, Object> sharedFlowConfig) throws IOException {
        // [Shared flow XML generation code remains the same]
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<SharedFlowBundle revision=\"1\" name=\"").append(name).append("\">\n");
        
        // Add description if provided
        String description = (String) sharedFlowConfig.getOrDefault("description", "");
        if (!description.isEmpty()) {
            xml.append("    <Description>").append(description).append("</Description>\n");
        }
        
        // Create basic shared flow configuration
        xml.append("    <SharedFlows>\n");
        xml.append("        <SharedFlow name=\"default\">\n");
        xml.append("            <Step>\n");
        
        // Add policy references
        if (sharedFlowConfig.containsKey("policies")) {
            List<Map<String, Object>> policies = (List<Map<String, Object>>) sharedFlowConfig.get("policies");
            for (Map<String, Object> policy : policies) {
                String policyName = (String) policy.get("name");
                xml.append("                <Name>").append(policyName).append("</Name>\n");
            }
        }
        
        xml.append("            </Step>\n");
        xml.append("        </SharedFlow>\n");
        xml.append("    </SharedFlows>\n");
        xml.append("</SharedFlowBundle>\n");
        
        // Write shared flow XML file
        Path sharedFlowFile = Paths.get(sharedflowsPath.toString(), "sharedflowbundle.xml");
        Files.writeString(sharedFlowFile, xml.toString());
    }

    /**
     * Generates Maven POM file for the shared flow.
     */
    private void generatePom(String sharedFlowDir, String name, String designName) throws IOException {
        StringBuilder pom = new StringBuilder();
        pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        pom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        pom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        pom.append("    <modelVersion>4.0.0</modelVersion>\n\n");
        
        pom.append("    <groupId>com.apigee.edge.sharedflow.").append(designName).append("</groupId>\n");
        pom.append("    <artifactId>").append(name).append("</artifactId>\n");
        pom.append("    <version>1.0</version>\n");
        pom.append("    <packaging>pom</packaging>\n\n");
        
        pom.append("    <properties>\n");
        pom.append("        <apigee.org>${env.APIGEE_ORG}</apigee.org>\n");
        pom.append("        <apigee.env>${env.APIGEE_ENV}</apigee.env>\n");
        pom.append("        <apigee.config.options>update</apigee.config.options>\n");
        pom.append("        <apigee.config.dir>${project.basedir}</apigee.config.dir>\n");
        pom.append("    </properties>\n\n");
        
        // Add build configuration with maven-deploy-plugin for shared flows
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
        pom.append("                <configuration>\n");
        pom.append("                    <bundleType>sharedflow</bundleType>\n");
        pom.append("                </configuration>\n");
        pom.append("            </plugin>\n");
        pom.append("        </plugins>\n");
        pom.append("    </build>\n");
        pom.append("</project>\n");
        
        // Write POM file
        Path pomFile = Paths.get(sharedFlowDir, "pom.xml");
        Files.writeString(pomFile, pom.toString());
    }
}