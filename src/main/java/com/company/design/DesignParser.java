package com.company.design;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DesignParser {

    private static final String DESIGNS_DIR = "designs";
    private static final String GENERATED_ROOT = "generated";
    private static final String SHARED_OUT = GENERATED_ROOT + "/sharedflows";
    private static final String PROXY_OUT = GENERATED_ROOT + "/proxies";
    private static final String CONFIG_OUT = GENERATED_ROOT + "/config/entities";

    public static void main(String[] args) throws Exception {
        ensureDirs();
        List<File> designFiles = findDesignFiles();
        if (designFiles.isEmpty()) {
            System.out.println("No design YAML files found in /" + DESIGNS_DIR);
            return;
        }

        SharedFlowGenerator sfg = new SharedFlowGenerator(SHARED_OUT);
        ProxyGenerator pg = new ProxyGenerator(PROXY_OUT);

        for (File f : designFiles) {
            System.out.println("Processing design file: " + f.getName());
            Map<String, Object> root = loadYaml(f);
            if (root == null) {
                System.out.println("  Skipping (empty): " + f.getName());
                continue;
            }
            Object apigeeObj = root.get("apigee");
            if (!(apigeeObj instanceof Map)) {
                System.out.println("  Skipping (missing 'apigee' root): " + f.getName());
                continue;
            }
            Map apigee = (Map) apigeeObj;
            String designName = safeString(apigee.get("name"));
            if (designName == null || designName.trim().isEmpty()) {
                designName = stripExt(f.getName());
            }

            // Shared Flows first
            List<Map<String, Object>> sharedFlows = castList(apigee.get("sharedFlows"));
            if (sharedFlows != null) {
                for (Map<String, Object> sf : sharedFlows) {
                    try {
                        sfg.generateSharedFlow(sf, designName);
                    } catch (Exception ex) {
                        System.err.println("  ERROR generating shared flow: " + ex.getMessage());
                        throw ex;
                    }
                }
            }

            // Proxies
            List<Map<String, Object>> proxies = castList(apigee.get("proxies"));
            if (proxies != null) {
                for (Map<String, Object> px : proxies) {
                    try {
                        pg.generateProxy(px, designName);
                    } catch (Exception ex) {
                        System.err.println("  ERROR generating proxy: " + ex.getMessage());
                        throw ex;
                    }
                }
            }

            // API Products (config)
            List<Map<String, Object>> products = castList(apigee.get("apiProducts"));
            if (products != null && !products.isEmpty()) {
                ConfigGenerator.generateApiProducts(products, CONFIG_OUT);
                ConfigGenerator.finalizeConfigModule("generated/config");
            }
        }
        System.out.println("Done.");
    }

    private static void ensureDirs() throws Exception {
        Files.createDirectories(Paths.get(SHARED_OUT));
        Files.createDirectories(Paths.get(PROXY_OUT));
        Files.createDirectories(Paths.get(CONFIG_OUT));
    }

    private static List<File> findDesignFiles() {
        File dir = new File(DESIGNS_DIR);
        if (!dir.isDirectory()) return Collections.emptyList();
        File[] arr = dir.listFiles(functionalYamlFilter());
        if (arr == null || arr.length == 0) return Collections.emptyList();
        return Arrays.asList(arr);
    }

    private static java.io.FilenameFilter functionalYamlFilter() {
        return new java.io.FilenameFilter() {
            public boolean accept(File d, String n) {
                return n.endsWith(".yaml") || n.endsWith(".yml");
            }
        };
    }

    private static Map<String, Object> loadYaml(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            Yaml yaml = new Yaml();
            Object o = yaml.load(in);
            if (o instanceof Map) {
                return (Map<String, Object>) o;
            }
            return null;
        } finally {
            in.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        if (o instanceof List) {
            return (List<Map<String, Object>>) o;
        }
        return null;
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i == -1 ? n : n.substring(0, i);
    }

    private static String safeString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}