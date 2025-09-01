package com.company.design;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parses YAML design files under designs/ and generates Apigee bundles + config.
 */
public class DesignParser {

    private static final String DESIGNS_DIR = "designs";
    private static final String OUT_BASE = "generated";
    private static final String OUT_SHARED = OUT_BASE + "/sharedflows";
    private static final String OUT_PROXIES = OUT_BASE + "/proxies";
    private static final String OUT_CONFIG = OUT_BASE + "/config";

    public static void main(String[] args) throws Exception {
        ensureDirs();
        List<File> yamlFiles = discoverDesignFiles();
        if (yamlFiles.isEmpty()) {
            System.out.println("No design files found.");
            return;
        }
        for (File f : yamlFiles) {
            processDesignFile(f);
        }
        // After generating flows/proxies, generate config module if needed
        ConfigGenerator.finalizeConfigModule(OUT_CONFIG);
        System.out.println("All designs processed.");
    }

    private static void ensureDirs() throws Exception {
        Files.createDirectories(Paths.get(OUT_SHARED));
        Files.createDirectories(Paths.get(OUT_PROXIES));
        Files.createDirectories(Paths.get(OUT_CONFIG));
    }

    private static List<File> discoverDesignFiles() {
        File dir = new File(DESIGNS_DIR);
        if (!dir.isDirectory()) return List.of();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yaml") || n.endsWith(".yml"));
        if (files == null) return List.of();
        return Arrays.asList(files);
    }

    @SuppressWarnings("unchecked")
    private static void processDesignFile(File file) throws Exception {
        System.out.println("Processing design: " + file.getName());
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (FileInputStream in = new FileInputStream(file)) {
            root = yaml.load(in);
        }
        if (root == null || !root.containsKey("apigee")) {
            System.out.println("Skipping file (missing apigee root): " + file.getName());
            return;
        }
        Map<String, Object> apigee = (Map<String, Object>) root.get("apigee");
        String designName = (String) apigee.getOrDefault("name", stripExt(file.getName()));

        List<Map<String, Object>> sharedFlows =
                (List<Map<String, Object>>) apigee.getOrDefault("sharedFlows", List.of());
        List<Map<String, Object>> proxies =
                (List<Map<String, Object>>) apigee.getOrDefault("proxies", List.of());
        List<Map<String, Object>> apiProducts =
                (List<Map<String, Object>>) apigee.getOrDefault("apiProducts", List.of());

        SharedFlowGenerator sfg = new SharedFlowGenerator(OUT_SHARED);
        for (Map<String, Object> sf : sharedFlows) {
            sfg.generateSharedFlow(sf, designName);
        }

        ProxyGenerator pg = new ProxyGenerator(OUT_PROXIES);
        for (Map<String, Object> px : proxies) {
            pg.generateProxy(px, designName);
        }

        // Config entities
        if (!apiProducts.isEmpty()) {
            ConfigGenerator.generateApiProducts(apiProducts, OUT_CONFIG + "/entities/apiproducts");
        }
    }

    private static String stripExt(String name) {
        int idx = name.lastIndexOf('.');
        return (idx == -1) ? name : name.substring(0, idx);
    }
}