package com.apigee.designparser;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parses YAML design files and generates Apigee bundles.
 */
public class DesignParser {

    private static final String DESIGN_DIR = "designs";
    private static final String GENERATED_DIR = "generated";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get(GENERATED_DIR));

        File[] yamlFiles = new File(DESIGN_DIR).listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (yamlFiles == null) {
            System.out.println("No design files found.");
            return;
        }

        for (File file : yamlFiles) {
            System.out.println("Processing: " + file.getName());
            processYaml(file);
        }
    }

    private static void processYaml(File file) throws Exception {
        Yaml yaml = new Yaml();
        try (FileInputStream input = new FileInputStream(file)) {
            Map<String, Object> data = yaml.load(input);
            if (data.containsKey("apigee")) {
                Map<String, Object> apigee = (Map<String, Object>) data.get("apigee");

                // Process shared flows
                if (apigee.containsKey("sharedFlows")) {
                    List<Map<String, Object>> sharedFlows = (List<Map<String, Object>>) apigee.get("sharedFlows");
                    for (Map<String, Object> sf : sharedFlows) {
                        generateSharedFlow(sf);
                    }
                }

                // Process proxies
                if (apigee.containsKey("proxies")) {
                    List<Map<String, Object>> proxies = (List<Map<String, Object>>) apigee.get("proxies");
                    for (Map<String, Object> proxy : proxies) {
                        generateProxy(proxy);
                    }
                }
            }
        }
    }

    private static void generateSharedFlow(Map<String, Object> sf) throws Exception {
        String name = (String) sf.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shared flow name is required.");
        }
        Path dir = Paths.get(GENERATED_DIR, "sharedFlows", name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("sharedflowbundle.xml"), "Generated shared flow XML...");
        System.out.println("Generated shared flow: " + name);
    }

    private static void generateProxy(Map<String, Object> proxy) throws Exception {
        String name = (String) proxy.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Proxy name is required.");
        }
        Path dir = Paths.get(GENERATED_DIR, "proxies", name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("apiproxy.xml"), "Generated proxy XML...");
        System.out.println("Generated proxy: " + name);
    }
}