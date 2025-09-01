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
            if (name == null) continue;
            Files.write(dir.resolve(name + ".json"),
                    productJson(product).getBytes("UTF-8"));
        }
        createConfigPom("generated/config");
    }
    
    public static void generateDevelopers(List<Map<String,Object>> developers, String entitiesDir) throws IOException {
        Path dir = Paths.get(entitiesDir);
        Files.createDirectories(dir);
        for (Map<String,Object> developer : developers) {
            String email = val(developer.get("name"));
            if (email == null) continue;
            Files.write(dir.resolve(email + ".json"),
                    developerJson(developer).getBytes("UTF-8"));
        }
    }

    public static void finalizeConfigModule(String configRoot) throws IOException {
        Path entities = Paths.get(configRoot, "entities");
        if (Files.exists(entities) && Files.list(entities).findAny().isPresent()) {
            createConfigPom(configRoot);
        }
    }

    private static String productJson(Map<String,Object> p) {
        String name = val(p.get("name"));
        String display = val(p.get("displayName"));
        String description = val(p.get("description"));
        if (display == null) display = name;
        if (description == null) description = "";
        String approval = val(p.get("approvalType")); if (approval == null) approval="AUTO";
        Map<String,Object> quota = mapOf(p.get("quota"));
        List<String> proxies = listOf(p.get("proxies"));
        List<String> envs = listOf(p.get("environments"));
        List<String> scopes = listOf(p.get("scopes"));

        StringBuilder sb = new StringBuilder("{\n");
        kv(sb,"name",name); comma(sb);
        kv(sb,"displayName",display); comma(sb);
        kv(sb,"description",description); comma(sb);
        kv(sb,"approvalType",approval); comma(sb);
        arr(sb,"proxies",proxies); comma(sb);
        arr(sb,"environments",envs);

        if (!quota.isEmpty()) {
            sb.append(",\n  \"quota\":\"").append(val(quota.get("limit"))==null?"1000":val(quota.get("limit"))).append("\",");
            sb.append("\n  \"quotaInterval\":\"").append(val(quota.get("interval"))==null?"1":val(quota.get("interval"))).append("\",");
            sb.append("\n  \"quotaTimeUnit\":\"").append(val(quota.get("timeUnit"))==null?"minute":val(quota.get("timeUnit"))).append("\"");
        }
        
        if (!scopes.isEmpty()) {
            sb.append(",\n  \"scopes\": [");
            for (int i = 0; i < scopes.size(); i++) {
                sb.append("\"").append(scopes.get(i)).append("\"");
                if (i < scopes.size() - 1) sb.append(", ");
            }
            sb.append("]");
        }
        
        sb.append(",\n  \"attributes\": []\n}\n");
        return sb.toString();
    }
    
    private static String developerJson(Map<String,Object> d) {
        String email = val(d.get("name"));
        String firstName = val(d.get("firstName"));
        String lastName = val(d.get("lastName"));
        if (firstName == null) firstName = "Developer";
        if (lastName == null) lastName = "User";
        
        StringBuilder sb = new StringBuilder("{\n");
        kv(sb, "email", email); comma(sb);
        kv(sb, "firstName", firstName); comma(sb);
        kv(sb, "lastName", lastName); comma(sb);
        kv(sb, "userName", email);
        sb.append(",\n  \"attributes\": []\n}\n");
        return sb.toString();
    }

    private static void kv(StringBuilder sb, String k, String v){
        sb.append("  \"").append(k).append("\": \"").append(v).append("\"");
    }
    private static void arr(StringBuilder sb, String k, List<String> vals){
        sb.append("  \"").append(k).append("\": [");
        for (int i=0;i<vals.size();i++){
            sb.append("\"").append(vals.get(i)).append("\"");
            if(i<vals.size()-1) sb.append(", ");
        }
        sb.append("]");
    }
    private static void comma(StringBuilder sb){ sb.append(",\n"); }

    private static void createConfigPom(String configRoot) throws IOException {
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
    private static List<String> listOf(Object o){
        List<String> out = new ArrayList<>();
        if (o instanceof List) for(Object i:(List)o) if(i!=null) out.add(String.valueOf(i));
        return out;
    }
    private static Map<String,Object> mapOf(Object o){ return (o instanceof Map)?(Map<String,Object>)o:new HashMap<>(); }
}