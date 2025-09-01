package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Generates configuration entity JSON for apigee-config-maven-plugin.
 * Currently supports: API Products (extend as needed).
 */
public class ConfigGenerator {

    public static void generateApiProducts(List<Map<String, Object>> products, String outDir) throws IOException {
        Path dir = Paths.get(outDir);
        Files.createDirectories(dir);
        for (Map<String, Object> product : products) {
            String name = (String) product.get("name");
            if (name == null || name.isBlank()) continue;

            String json = buildApiProductJson(product);
            Files.writeString(dir.resolve(name + ".json"), json);
        }
        // Ensure config module POM exists
        createConfigPomIfMissing("generated/config");
    }

    private static String buildApiProductJson(Map<String, Object> product) {
        // Minimal JSON; you can enrich mapping as needed
        String name = (String) product.get("name");
        String displayName = (String) product.getOrDefault("displayName", name);
        String approvalType = String.valueOf(product.getOrDefault("approvalType", "AUTO"));
        @SuppressWarnings("unchecked")
        List<String> proxies = (List<String>) product.getOrDefault("proxies", List.of());
        @SuppressWarnings("unchecked")
        List<String> envs = (List<String>) product.getOrDefault("environments", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> quota = (Map<String, Object>) product.getOrDefault("quota", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) product.getOrDefault("attributes", Map.of());

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"displayName\": \"").append(displayName).append("\",\n");
        sb.append("  \"approvalType\": \"").append(approvalType).append("\",\n");
        sb.append("  \"proxies\": ").append(asJsonArray(proxies)).append(",\n");
        sb.append("  \"environments\": ").append(asJsonArray(envs)).append(",\n");

        if (!quota.isEmpty()) {
            sb.append("  \"quota\": \"").append(quota.getOrDefault("limit", 1000)).append("\",\n");
            sb.append("  \"quotaInterval\": \"").append(quota.getOrDefault("interval", 1)).append("\",\n");
            sb.append("  \"quotaTimeUnit\": \"").append(quota.getOrDefault("timeUnit", "minute")).append("\",\n");
        }

        if (!attributes.isEmpty()) {
            sb.append("  \"attributes\": [\n");
            int i = 0;
            for (Map.Entry<String, Object> e : attributes.entrySet()) {
                sb.append("    {\"name\": \"").append(e.getKey()).append("\", \"value\": \"")
                  .append(String.valueOf(e.getValue())).append("\"}");
                if (i < attributes.size() - 1) sb.append(",");
                sb.append("\n");
                i++;
            }
            sb.append("  ]\n");
        } else {
            // Remove trailing comma if attributes not present; simple approach
            if (sb.charAt(sb.length() - 2) == ',') {
                // Not handling an exhaustive cleanup; safe path is simpler: ensure last line replaced, but for brevity we keep it simple
            }
            sb.append("  \"attributes\": []\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String asJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append("\"").append(items.get(i)).append("\"");
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    public static void finalizeConfigModule(String configRoot) throws IOException {
        Path pom = Paths.get(configRoot, "pom.xml");
        if (!Files.exists(pom)) {
            // Only create if entities folder exists & not empty
            Path entities = Paths.get(configRoot, "entities");
            if (!Files.exists(entities)) return;
            if (Files.list(entities).findAny().isEmpty()) return;
            createConfigPomIfMissing(configRoot);
        }
    }

    private static void createConfigPomIfMissing(String configRoot) throws IOException {
        Path pom = Paths.get(configRoot, "pom.xml");
        if (Files.exists(pom)) return;

        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.apigee.edge.config</groupId>
                  <artifactId>apigee-config-entities</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>com.apigee.edge.config</groupId>
                        <artifactId>apigee-config-maven-plugin</artifactId>
                        <version>2.3.0</version>
                        <executions>
                          <execution>
                            <id>apiproducts</id>
                            <phase>install</phase>
                            <goals>
                              <goal>apiproducts</goal>
                            </goals>
                          </execution>
                        </executions>
                        <configuration>
                          <org>${env.APIGEE_ORG}</org>
                          <env>${env.APIGEE_ENV}</env>
                          <apigee.config.dir>${project.basedir}/entities</apigee.config.dir>
                          <apigee.config.options>update</apigee.config.options>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """;
        Files.writeString(pom, pomContent);
    }
}