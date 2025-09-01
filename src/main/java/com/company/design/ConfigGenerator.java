package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConfigGenerator {

    public static void generateApiProducts(List<Map<String, Object>> products, String entitiesDir) throws IOException {
        Path dir = Paths.get(entitiesDir);
        Files.createDirectories(dir);
        for (int i = 0; i < products.size(); i++) {
            Map<String, Object> product = products.get(i);
            String name = val(product.get("name"));
            if (name == null || name.trim().isEmpty()) continue;
            String json = buildApiProductJson(product);
            Files.write(dir.resolve(name + ".json"), json.getBytes("UTF-8"));
        }
        createConfigPomIfMissing("generated/config");
    }

    private static String buildApiProductJson(Map<String, Object> product) {
        String name = val(product.get("name"));
        String displayName = val(product.get("displayName"));
        if (displayName == null) displayName = name;
        String approvalType = val(product.get("approvalType"));
        if (approvalType == null) approvalType = "AUTO";

        List<String> proxies = listOf(product.get("proxies"));
        List<String> envs = listOf(product.get("environments"));

        Map<String, Object> quota = mapOf(product.get("quota"));

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, "name", name, true);
        appendJsonField(sb, "displayName", displayName, true);
        appendJsonField(sb, "approvalType", approvalType, true);
        appendJsonArray(sb, "proxies", proxies, true);
        appendJsonArray(sb, "environments", envs, true);

        if (!quota.isEmpty()) {
            appendJsonField(sb, "quota", String.valueOf(quota.get("limit") == null ? 1000 : quota.get("limit")), true);
            appendJsonField(sb, "quotaInterval", String.valueOf(quota.get("interval") == null ? 1 : quota.get("interval")), true);
            appendJsonField(sb, "quotaTimeUnit", String.valueOf(quota.get("timeUnit") == null ? "minute" : quota.get("timeUnit")), true);
        }

        // attributes empty
        sb.append("  \"attributes\": []\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendJsonField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append("  \"").append(key).append("\": \"").append(value).append("\"");
        if (comma) sb.append(",");
        sb.append("\n");
    }

    private static void appendJsonArray(StringBuilder sb, String key, List<String> values, boolean comma) {
        sb.append("  \"").append(key).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            sb.append("\"").append(values.get(i)).append("\"");
            if (i < values.size() - 1) sb.append(", ");
        }
        sb.append("]");
        if (comma) sb.append(",");
        sb.append("\n");
    }

    public static void finalizeConfigModule(String configRoot) throws IOException {
        Path entities = Paths.get(configRoot, "entities");
        if (!Files.exists(entities)) return;
        if (Files.list(entities).findAny().isPresent()) {
            createConfigPomIfMissing(configRoot);
        }
    }

    private static void createConfigPomIfMissing(String configRoot) throws IOException {
        Path pom = Paths.get(configRoot, "pom.xml");
        if (Files.exists(pom)) return;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" ")
          .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
          .append("xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 ")
          .append("http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("  <modelVersion>4.0.0</modelVersion>\n");
        sb.append("  <groupId>com.apigee.config</groupId>\n");
        sb.append("  <artifactId>apigee-config-entities</artifactId>\n");
        sb.append("  <version>1.0</version>\n");
        sb.append("  <packaging>pom</packaging>\n");
        sb.append("  <build>\n");
        sb.append("    <plugins>\n");
        sb.append("      <plugin>\n");
        sb.append("        <groupId>com.apigee.edge.config</groupId>\n");
        sb.append("        <artifactId>apigee-config-maven-plugin</artifactId>\n");
        sb.append("        <version>2.3.0</version>\n");
        sb.append("        <executions>\n");
        sb.append("          <execution>\n");
        sb.append("            <id>apiproducts</id>\n");
        sb.append("            <phase>install</phase>\n");
        sb.append("            <goals><goal>apiproducts</goal></goals>\n");
        sb.append("          </execution>\n");
        sb.append("        </executions>\n");
        sb.append("        <configuration>\n");
        sb.append("          <org>${apigee.org}</org>\n");
        sb.append("          <env>${apigee.env}</env>\n");
        sb.append("          <apigee.config.dir>${project.basedir}/entities</apigee.config.dir>\n");
        sb.append("          <apigee.config.options>update</apigee.config.options>\n");
        sb.append("          <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n");
        sb.append("        </configuration>\n");
        sb.append("      </plugin>\n");
        sb.append("    </plugins>\n");
        sb.append("  </build>\n");
        sb.append("</project>\n");

        Files.createDirectories(Paths.get(configRoot));
        Files.write(pom, sb.toString().getBytes("UTF-8"));
    }

    private static String val(Object o) { return o == null ? null : String.valueOf(o); }

    private static List<String> listOf(Object o) {
        List<String> r = new ArrayList<String>();
        if (o instanceof List) {
            List raw = (List) o;
            for (int i = 0; i < raw.size(); i++) {
                Object item = raw.get(i);
                if (item != null) r.add(String.valueOf(item));
            }
        }
        return r;
    }

    private static Map<String, Object> mapOf(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return new HashMap<String, Object>();
    }
}