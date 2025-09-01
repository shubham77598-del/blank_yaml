package com.example;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.nio.file.DirectoryStream;

public class DesignParser {
    public static void main(String[] args) throws Exception {
        String designsDir = "designs/";
        Yaml yaml = new Yaml();
        
        try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(Paths.get(designsDir), "*.yaml")) {
            for (java.nio.file.Path entry : stream) {
                System.out.println("Processing YAML file: " + entry.getFileName());
                try (InputStream in = Files.newInputStream(entry)) {
                    Map<String, Object> data = yaml.load(in);
                    if (data == null) continue;
                    
                    // Process shared flows first (due to dependencies)
                    if (data.containsKey("sharedFlows")) {
                        SharedFlowGenerator.generate((List<Map<String, Object>>) data.get("sharedFlows"));
                    }
                    
                    // Then process proxies
                    if (data.containsKey("proxies")) {
                        ProxyGenerator.generate((List<Map<String, Object>>) data.get("proxies"));
                    }
                }
            }
        }
        System.out.println("Bundle generation completed for all YAML files.");
    }
}