package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConfigGenerator {

    public static void generateApiProducts(List<Map<String,Object>> products, String entitiesDir) throws IOException {
        Path dir = Paths.get(entitiesDir);
        Files.createDirectories(dir);
        for (Map<String,Object> product : products) {
            String name = val(product.get("name"));
            if (name == null || name.trim().isEmpty()) continue;
            String json = buildApiProductJson(product);
            Files.write(dir.resolve(name + ".json"), json.getBytes("UTF-8"));
        }
        createConfigPomIfMissing("generated/config");
    }

    public static void finalizeConfigModule(String configRoot) throws IOException {
        Path entities = Paths.get(configRoot, "entities");
        if (!Files.exists(entities)) return;
        if (Files.list(entities).findAny().isPresent()) {
            createConfigPomIfMissing(configRoot);
        }
    }

    private static String buildApiProductJson(Map<String,Object> product) {
        String name = val(product.get("name"));
        String display = val(product.get("displayName"));
        if (display == null) display = name;
        String approval = val(product.get("approvalType"));
        if (approval == null) approval = "AUTO";
        List<String> proxies = listOf(product.get("proxies"));
        List<String> envs = listOf(product.get("environments"));
        Map<String,Object> quota = mapOf(product.get("quota"));

        String quotaLimit = val(quota.get("limit")) == null ? "1000" : val(quota.get("limit"));
        String quotaInterval = val(quota.get("interval")) == null ? "1" : val(quota.get("interval"));
        String quotaTimeUnit = val(quota.get("timeUnit")) == null ? "minute" : val(quota.get("timeUnit"));

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        field(sb, "name", name, true);
        field(sb, "displayName", display, true);
        field(sb, "approvalType", approval, true);
        array(sb, "proxies", proxies, true);
        array(sb, "environments", envs, true);
        field(sb, "quota", quotaLimit, true);
        field(sb, "quotaInterval", quotaInterval, true);
        field(sb, "quotaTimeUnit", quotaTimeUnit, true);
        sb.append("  \"attributes\": []\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void field(StringBuilder sb, String k, String v, boolean comma) {
        sb.append("  \"").append(k).append("\": \"").append(v).append("\"");
        if (comma) sb.append(",");
        sb.append("\n");
    }

    private static void array(StringBuilder sb, String k, List<String> vals, boolean comma) {
        sb.append("  \"").append(k).append("\": [");
        for (int i = 0; i < vals.size(); i++) {
            sb.append("\"").append(vals.get(i)).append("\"");
            if (i < vals.size() - 1) sb.append(", ");
        }
        sb.append("]");
        if (comma) sb.append(",");
        sb.append("\n");
    }

    private static void createConfigPomIfMissing(String configRoot) throws IOException {
        Path pom = Paths.get(configRoot, "pom.xml");
        if (Files.exists(pom)) return;

        String xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.apigee.config</groupId>\n" +
            "  <artifactId>apigee-config-entities</artifactId>\n" +
            "  <version>1.0</version>\n" +
            "  <packaging>pom</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>com.apigee.edge.config</groupId>\n" +
            "        <artifactId>apigee-config-maven-plugin</artifactId>\n" +
            "        <version>2.3.0</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>apiproducts</id>\n" +
            "            <phase>install</phase>\n" +
            "            <goals><goal>apiproducts</goal></goals>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "        <configuration>\n" +
            "          <org>${apigee.org}</org>\n" +
            "          <env>${apigee.env}</env>\n" +
            "          <apigee.config.dir>${project.basedir}/entities</apigee.config.dir>\n" +
            "          <apigee.config.options>update</apigee.config.options>\n" +
            "          <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";

        Files.createDirectories(Paths.get(configRoot));
        Files.write(pom, xml.getBytes("UTF-8"));
    }

    private static String val(Object o){ return o==null?null:String.valueOf(o); }

    private static List<String> listOf(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List)o) {
                if (item != null) out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static Map<String,Object> mapOf(Object o) {
        if (o instanceof Map) return (Map<String,Object>) o;
        return new HashMap<>();
    }
}