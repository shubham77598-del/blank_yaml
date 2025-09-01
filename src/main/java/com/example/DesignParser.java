package com.example;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Reads every *.yml / *.yaml in designs/ and generates bundles into generated/.
 */
public class DesignParser {

    private static final String DESIGNS_DIR = "designs";
    private static final String OUT_BASE = "generated";
    private static final String OUT_SHARED = OUT_BASE + "/sharedflows";
    private static final String OUT_PROXIES = OUT_BASE + "/proxies";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get(OUT_SHARED));
        Files.createDirectories(Paths.get(OUT_PROXIES));
        List<File> yamlFiles = discoverDesignFiles();
        if (yamlFiles.isEmpty()) {
            System.out.println("No design files found in " + DESIGNS_DIR);
            return;
        }
        for (File f : yamlFiles) {
            processDesignFile(f);
        }
        System.out.println("Design processing complete.");
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
        System.out.println("Parsing design file: " + file.getName());
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (FileInputStream in = new FileInputStream(file)) {
            root = yaml.load(in);
        }
        if (root == null || !root.containsKey("apigee")) {
            System.out.println("Skipping: no 'apigee' root section.");
            return;
        }
        Map<String, Object> apigee = (Map<String, Object>) root.get("apigee");
        String designName = (String) apigee.getOrDefault("name", stripExt(file.getName()));
        List<Map<String, Object>> sharedFlows =
                (List<Map<String, Object>>) apigee.getOrDefault("sharedFlows", List.of());
        List<Map<String, Object>> proxies =
                (List<Map<String, Object>>) apigee.getOrDefault("proxies", List.of());

        SharedFlowGenerator sfg = new SharedFlowGenerator(OUT_SHARED);
        for (Map<String, Object> sf : sharedFlows) {
            sfg.generateSharedFlow(sf, designName);
        }

        ProxyGenerator pg = new ProxyGenerator(OUT_PROXIES);
        for (Map<String, Object> px : proxies) {
            pg.generateProxy(px, designName, sharedFlows);
        }
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i == -1) ? name : name.substring(0, i);
    }
}