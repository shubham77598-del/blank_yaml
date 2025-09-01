package com.example;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main class responsible for parsing YAML design files and orchestrating
 * the generation of Apigee bundles for proxies and shared flows.
 */
public class DesignParser {
    private static final String DESIGNS_DIR = "designs";
    private static final String OUTPUT_DIR = "target";
    private static final String SHARED_FLOWS_DIR = OUTPUT_DIR + "/sharedflows";
    private static final String PROXIES_DIR = OUTPUT_DIR + "/proxies";

    public static void main(String[] args) {
        try {
            // Process specific file if provided, otherwise process all files in designs directory
            if (args.length > 0) {
                processDesignFile(args[0]);
            } else {
                processDesignDirectory();
            }

            System.out.println("Successfully processed all design files.");
        } catch (Exception e) {
            System.err.println("Error processing design files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Process all YAML design files in the designs directory.
     */
    private static void processDesignDirectory() throws IOException {
        File designsDir = new File(DESIGNS_DIR);
        if (!designsDir.exists() || !designsDir.isDirectory()) {
            throw new IOException("Designs directory not found: " + DESIGNS_DIR);
        }

        File[] yamlFiles = designsDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".yaml") || name.toLowerCase().endsWith(".yml"));

        if (yamlFiles == null || yamlFiles.length == 0) {
            System.out.println("No YAML design files found in " + DESIGNS_DIR);
            return;
        }

        for (File file : yamlFiles) {
            processDesignFile(file.getPath());
        }
    }

    /**
     * Process a single YAML design file and generate Apigee bundles.
     */
    private static void processDesignFile(String filePath) {
        System.out.println("Processing design file: " + filePath);
        try {
            // Parse the YAML file
            Map<String, Object> design = loadYamlFile(filePath);
            
            // Ensure output directories exist
            createDirectories();
            
            // Get the Apigee section
            if (!design.containsKey("apigee")) {
                throw new IllegalArgumentException("Design file must contain an 'apigee' section");
            }
            Map<String, Object> apigee = (Map<String, Object>) design.get("apigee");
            
            // Extract design metadata
            String designName = (String) apigee.getOrDefault("name", "default-design");
            String version = (String) apigee.getOrDefault("version", "1.0");
            
            System.out.println("Parsed design: " + designName + " (version " + version + ")");
            
            // Process shared flows first (per requirement)
            List<Map<String, Object>> sharedFlows = new ArrayList<>();
            if (apigee.containsKey("sharedFlows")) {
                sharedFlows = (List<Map<String, Object>>) apigee.get("sharedFlows");
                System.out.println("Found " + sharedFlows.size() + " shared flows");
                
                SharedFlowGenerator sharedFlowGenerator = new SharedFlowGenerator(SHARED_FLOWS_DIR);
                for (Map<String, Object> sharedFlow : sharedFlows) {
                    sharedFlowGenerator.generateSharedFlow(sharedFlow, designName);
                }
            }
            
            // Then process API proxies
            List<Map<String, Object>> proxies = new ArrayList<>();
            if (apigee.containsKey("proxies")) {
                proxies = (List<Map<String, Object>>) apigee.get("proxies");
                System.out.println("Found " + proxies.size() + " API proxies");
                
                ProxyGenerator proxyGenerator = new ProxyGenerator(PROXIES_DIR);
                for (Map<String, Object> proxy : proxies) {
                    proxyGenerator.generateProxy(proxy, designName, sharedFlows);
                }
            }
            
            // Create parent POM file that includes all generated modules
            generateParentPom(sharedFlows, proxies);
            
            System.out.println("Successfully processed design: " + designName);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load and parse a YAML file.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlFile(String filePath) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
            return (Map<String, Object>) yaml.load(fileInputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read YAML file: " + filePath, e);
        }
    }

    /**
     * Create necessary output directories.
     */
    private static void createDirectories() throws IOException {
        Files.createDirectories(Paths.get(SHARED_FLOWS_DIR));
        Files.createDirectories(Paths.get(PROXIES_DIR));
    }

    /**
     * Generate a parent POM file that includes all shared flows and proxies as modules.
     */
    private static void generateParentPom(List<Map<String, Object>> sharedFlows, List<Map<String, Object>> proxies) {
        try {
            StringBuilder pomContent = new StringBuilder();
            pomContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            pomContent.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
            pomContent.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            pomContent.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
            pomContent.append("    <modelVersion>4.0.0</modelVersion>\n\n");
            pomContent.append("    <groupId>com.apigee.edge</groupId>\n");
            pomContent.append("    <artifactId>parent-pom</artifactId>\n");
            pomContent.append("    <version>1.0</version>\n");
            pomContent.append("    <packaging>pom</packaging>\n\n");
            
            pomContent.append("    <modules>\n");
            
            // Add shared flows first (per requirement)
            for (Map<String, Object> sharedFlow : sharedFlows) {
                String name = (String) sharedFlow.get("name");
                pomContent.append("        <module>sharedflows/").append(name).append("</module>\n");
            }
            
            // Then add proxies
            for (Map<String, Object> proxy : proxies) {
                String name = (String) proxy.get("name");
                pomContent.append("        <module>proxies/").append(name).append("</module>\n");
            }
            
            pomContent.append("    </modules>\n");
            pomContent.append("</project>\n");
            
            // Write the POM file
            Path pomPath = Paths.get(OUTPUT_DIR, "pom.xml");
            Files.writeString(pomPath, pomContent.toString());
            
            System.out.println("Generated parent POM at: " + pomPath);
        } catch (IOException e) {
            System.err.println("Error generating parent POM: " + e.getMessage());
            e.printStackTrace();
        }
    }
}