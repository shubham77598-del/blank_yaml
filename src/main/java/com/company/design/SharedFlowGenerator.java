package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SharedFlowGenerator {

    private final String outputDir;

    public SharedFlowGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    public void generateSharedFlow(Map<String, Object> sfConfig, String designName) throws IOException {
        String name = value(sfConfig.get("name"));
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Shared flow missing name");
        }
        System.out.println("  Generating shared flow: " + name);

        String dir = outputDir + "/" + name;
        Path bundle = Paths.get(dir, "sharedflowbundle");
        Path policiesPath = bundle.resolve("policies");
        Files.createDirectories(policiesPath);

        List<Map<String, Object>> policies = safeList(sfConfig.get("policies"));
        for (int i = 0; i < policies.size(); i++) {
            Map<String, Object> p = policies.get(i);
            String pName = value(p.get("name"));
            if (pName == null || pName.trim().isEmpty()) continue;
            String pType = value(p.get("type"));
            Map<String, Object> cfg = safeMap(p.get("configuration"));
            String policyXml = renderPolicy(pName, pType, cfg);
            Files.write(policiesPath.resolve(pName + ".xml"), policyXml.getBytes("UTF-8"));
        }

        String sfXml = renderSharedFlowXml(name, policies);
        Files.write(bundle.resolve("sharedflowbundle.xml"), sfXml.getBytes("UTF-8"));

        String pom = buildPom(designName, name);
        Files.write(Paths.get(dir, "pom.xml"), pom.getBytes("UTF-8"));
    }

    private String renderPolicy(String name, String type, Map<String, Object> cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if ("VerifyAPIKey".equals(type)) {
            String ref = value(cfg.get("keyLocation"));
            if (ref == null || ref.trim().isEmpty()) {
                ref = "request.header.x-api-key";
            }
            sb.append("<VerifyAPIKey name=\"").append(name).append("\">\n");
            sb.append("  <DisplayName>").append(name).append("</DisplayName>\n");
            sb.append("  <APIKey ref=\"").append(ref).append("\"/>\n");
            sb.append("</VerifyAPIKey>\n");
        } else if ("MessageLogging".equals(type)) {
            String lvl = value(cfg.get("logLevel"));
            if (lvl == null) lvl = "INFO";
            sb.append("<MessageLogging name=\"").append(name).append("\">\n");
            sb.append("  <DisplayName>").append(name).append("</DisplayName>\n");
            sb.append("  <LogLevel>").append(lvl).append("</LogLevel>\n");
            sb.append("</MessageLogging>\n");
        } else {
            sb.append("<Policy name=\"").append(name).append("\">\n");
            sb.append("  <DisplayName>").append(name).append("</DisplayName>\n");
            sb.append("</Policy>\n");
        }
        return sb.toString();
    }

    private String renderSharedFlowXml(String name, List<Map<String, Object>> policies) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<SharedFlowBundle name=\"").append(name).append("\" revision=\"1\">\n");
        sb.append("  <SharedFlows>\n");
        sb.append("    <SharedFlow name=\"default\">\n");
        for (int i = 0; i < policies.size(); i++) {
            Map<String, Object> p = policies.get(i);
            String pName = value(p.get("name"));
            if (pName != null) {
                sb.append("      <Step><Name>").append(pName).append("</Name></Step>\n");
            }
        }
        sb.append("    </SharedFlow>\n");
        sb.append("  </SharedFlows>\n");
        sb.append("</SharedFlowBundle>\n");
        return sb.toString();
    }

    private String buildPom(String designName, String name) {
        String group = (designName == null || designName.trim().isEmpty())
                ? "com.apigee.sharedflow"
                : "com.apigee.sharedflow." + designName;
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" ")
          .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
          .append("xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 ")
          .append("http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("  <modelVersion>4.0.0</modelVersion>\n");
        sb.append("  <groupId>").append(group).append("</groupId>\n");
        sb.append("  <artifactId>").append(name).append("</artifactId>\n");
        sb.append("  <version>1.0</version>\n");
        sb.append("  <packaging>pom</packaging>\n");
        sb.append("  <build>\n");
        sb.append("    <plugins>\n");
        sb.append("      <plugin>\n");
        sb.append("        <groupId>io.apigee.build-tools.enterprise4g</groupId>\n");
        sb.append("        <artifactId>apigee-edge-maven-plugin</artifactId>\n");
        sb.append("        <version>1.2.1</version>\n");
        sb.append("        <executions>\n");
        sb.append("          <execution>\n");
        sb.append("            <id>configure</id>\n");
        sb.append("            <phase>package</phase>\n");
        sb.append("            <goals><goal>configure</goal></goals>\n");
        sb.append("          </execution>\n");
        sb.append("          <execution>\n");
        sb.append("            <id>deploy</id>\n");
        sb.append("            <phase>install</phase>\n");
        sb.append("            <goals><goal>deploy</goal></goals>\n");
        sb.append("          </execution>\n");
        sb.append("        </executions>\n");
        sb.append("        <configuration>\n");
        sb.append("          <org>${apigee.org}</org>\n");
        sb.append("          <env>${apigee.env}</env>\n");
        sb.append("          <bundleType>sharedflow</bundleType>\n");
        sb.append("          <cleanDeployment>true</cleanDeployment>\n");
        sb.append("          <override>true</override>\n");
        sb.append("          <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n");
        sb.append("        </configuration>\n");
        sb.append("      </plugin>\n");
        sb.append("    </plugins>\n");
        sb.append("  </build>\n");
        sb.append("</project>\n");
        return sb.toString();
    }

    private List<Map<String, Object>> safeList(Object o) {
        if (o instanceof List) {
            return (List<Map<String, Object>>) o;
        }
        return new ArrayList<Map<String, Object>>();
    }

    private Map<String, Object> safeMap(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return new HashMap<String, Object>();
    }

    private String value(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}