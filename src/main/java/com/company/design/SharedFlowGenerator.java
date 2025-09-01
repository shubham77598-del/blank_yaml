package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SharedFlowGenerator {
    
    private final String outputDir;
    
    public SharedFlowGenerator(String outputDir) {
        this.outputDir = outputDir;
    }
    
    public void generateSharedFlow(Map<String,Object> sharedFlowConfig, String designName) throws IOException {
        String name = val(sharedFlowConfig.get("name"));
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Shared flow missing name");
        }
        System.out.println("  [SF] Generating: " + name);
        
        Path moduleRoot = Paths.get(outputDir, name);
        Path sharedflowbundle = moduleRoot.resolve("sharedflowbundle");
        Path policiesDir = sharedflowbundle.resolve("policies");
        Files.createDirectories(policiesDir);
        
        // Generate policies
        List<Map<String,Object>> policies = listOfMaps(sharedFlowConfig.get("policies"));
        for (Map<String,Object> p : policies) {
            String pName = val(p.get("name"));
            if (pName == null) continue;
            String pType = val(p.get("type"));
            Map<String,Object> cfg = mapOf(p.get("configuration"));
            Files.write(policiesDir.resolve(pName + ".xml"),
                renderPolicy(pName, pType, cfg).getBytes("UTF-8"));
        }
        
        // Generate shared flow bundle descriptor
        Files.write(sharedflowbundle.resolve(name + ".xml"),
            renderSharedFlowDescriptor(name, sharedFlowConfig).getBytes("UTF-8"));
        
        // Optional metadata
        Files.write(sharedflowbundle.resolve("config.json"),
            "{\"type\":\"sharedflowbundle\",\"version\":\"1.0\"}\n".getBytes("UTF-8"));
        
        // Create Maven POM for deployment
        createSharedFlowPom(moduleRoot, name);
    }
    
    private String renderPolicy(String name, String type, Map<String,Object> config) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<").append(type).append(" name=\"").append(name).append("\">\n");
        
        switch (type) {
            case "VerifyAPIKey":
                String keyLocation = val(config.get("keyLocation"));
                if (keyLocation == null) keyLocation = "request.header.x-api-key";
                sb.append("  <APIKey ref=\"").append(keyLocation).append("\"/>\n");
                break;
            case "OAuthV2":
                String operation = val(config.get("operation"));
                if (operation == null) operation = "VerifyAccessToken";
                sb.append("  <Operation>").append(operation).append("</Operation>\n");
                break;
            case "JSONThreatProtection":
                sb.append("  <ArrayElementCount>20</ArrayElementCount>\n");
                sb.append("  <ContainerDepth>10</ContainerDepth>\n");
                sb.append("  <ObjectEntryCount>15</ObjectEntryCount>\n");
                sb.append("  <ObjectEntryNameLength>50</ObjectEntryNameLength>\n");
                sb.append("  <StringValueLength>500</StringValueLength>\n");
                break;
        }
        
        sb.append("</").append(type).append(">\n");
        return sb.toString();
    }
    
    private String renderSharedFlowDescriptor(String name, Map<String,Object> config) {
        String description = val(config.get("description"));
        if (description == null) description = "Generated shared flow: " + name;
        
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<SharedFlow name=\"").append(name).append("\">\n");
        sb.append("  <DisplayName>").append(name).append("</DisplayName>\n");
        sb.append("  <Description>").append(description).append("</Description>\n");
        sb.append("</SharedFlow>\n");
        return sb.toString();
    }
    
    private void createSharedFlowPom(Path moduleRoot, String name) throws IOException {
        String xml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.apigee.sharedflows</groupId>\n" +
            "  <artifactId>" + name + "</artifactId>\n" +
            "  <version>1.0</version>\n" +
            "  <packaging>pom</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>org.apache.maven.plugins</groupId>\n" +
            "        <artifactId>maven-antrun-plugin</artifactId>\n" +
            "        <version>3.0.0</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>zip-bundle</id>\n" +
            "            <phase>compile</phase>\n" +
            "            <goals><goal>run</goal></goals>\n" +
            "            <configuration>\n" +
            "              <target>\n" +
            "                <zip destfile=\"${project.build.directory}/" + name + ".zip\">\n" +
            "                  <zipfileset dir=\"${project.basedir}/sharedflowbundle\" prefix=\"sharedflowbundle\"/>\n" +
            "                </zip>\n" +
            "              </target>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <groupId>com.google.cloud.apigee</groupId>\n" +
            "        <artifactId>apigee-maven-plugin</artifactId>\n" +
            "        <version>1.0.0</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>import-sharedflow</id>\n" +
            "            <phase>install</phase>\n" +
            "            <goals><goal>importSharedflow</goal></goals>\n" +
            "            <configuration>\n" +
            "              <org>${apigee.org}</org>\n" +
            "              <file>${project.build.directory}/" + name + ".zip</file>\n" +
            "              <name>" + name + "</name>\n" +
            "              <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "          <execution>\n" +
            "            <id>deploy-sharedflow</id>\n" +
            "            <phase>install</phase>\n" +
            "            <goals><goal>deploySharedflow</goal></goals>\n" +
            "            <configuration>\n" +
            "              <org>${apigee.org}</org>\n" +
            "              <env>${apigee.env}</env>\n" +
            "              <name>" + name + "</name>\n" +
            "              <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
        
        Files.write(moduleRoot.resolve("pom.xml"), xml.getBytes("UTF-8"));
    }
    
    private static String val(Object o) { 
        return o == null ? null : String.valueOf(o); 
    }
    
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> listOfMaps(Object o) {
        List<Map<String,Object>> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List)o) {
                if (item instanceof Map) {
                    out.add((Map<String,Object>) item);
                }
            }
        }
        return out;
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String,Object> mapOf(Object o) {
        if (o instanceof Map) return (Map<String,Object>) o;
        return new HashMap<>();
    }
}