package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ProxyGenerator {

    private final String outputDir;

    public ProxyGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    public void generateProxy(Map<String, Object> proxyConfig, String designName) throws IOException {
        String name = str(proxyConfig.get("name"));
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy missing name");
        }
        System.out.println("  Generating proxy: " + name);

        String dir = outputDir + "/" + name;
        Path apiproxy = Paths.get(dir, "apiproxy");
        Path policiesPath = apiproxy.resolve("policies");
        Path proxiesPath = apiproxy.resolve("proxies");
        Path targetsPath = apiproxy.resolve("targets");
        Files.createDirectories(policiesPath);
        Files.createDirectories(proxiesPath);
        Files.createDirectories(targetsPath);

        List<Map<String, Object>> policies = asList(proxyConfig.get("policies"));
        for (Map<String, Object> p : policies) {
            String pName = str(p.get("name"));
            if (pName == null) continue;
            String pType = str(p.get("type"));
            Map<String, Object> cfg = asMap(p.get("configuration"));
            Files.write(policiesPath.resolve(pName + ".xml"),
                    renderPolicy(pName, pType, cfg).getBytes("UTF-8"));
        }

        Files.write(proxiesPath.resolve("default.xml"),
                renderProxyEndpoint(name, proxyConfig).getBytes("UTF-8"));
        Files.write(targetsPath.resolve("default.xml"),
                renderTargetEndpoint(proxyConfig).getBytes("UTF-8"));
        Files.write(apiproxy.resolve(name + ".xml"),
                renderProxyDescriptor(name, proxyConfig).getBytes("UTF-8"));

        String pom = buildPom(designName, name);
        Files.write(Paths.get(dir, "pom.xml"), pom.getBytes("UTF-8"));
    }

    private String renderPolicy(String name, String type, Map<String, Object> cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if ("Quota".equals(type)) {
            String limit = str(cfg.get("limit"));
            if (limit == null) limit = "1000";
            String timeUnit = str(cfg.get("timeUnit"));
            if (timeUnit == null) timeUnit = "minute";
            sb.append("<Quota name=\"").append(name).append("\">\n");
            sb.append("  <DisplayName>").append(name).append("</DisplayName>\n");
            sb.append("  <Allow count=\"").append(limit).append("\"/>\n");
            sb.append("  <Interval>1</Interval>\n");
            sb.append("  <TimeUnit>").append(timeUnit).append("</TimeUnit>\n");
            sb.append("</Quota>\n");
        } else {
            sb.append("<Policy name=\"").append(name).append("\">\n");
            sb.append("  <DisplayName>").append(name).append("</DisplayName>\n");
            sb.append("</Policy>\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String renderProxyEndpoint(String proxyName, Map<String, Object> proxyConfig) {
        String basePath = str(proxyConfig.get("basePath"));
        if (basePath == null) basePath = "/" + proxyName;
        List<Map<String, Object>> flows = asList(proxyConfig.get("flows"));
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ProxyEndpoint name=\"default\">\n");
        sb.append("  <PreFlow name=\"PreFlow\"><Request/><Response/></PreFlow>\n");
        for (int i = 0; i < flows.size(); i++) {
            Map<String, Object> f = flows.get(i);
            String fname = str(f.get("name"));
            if (fname == null) fname = "flow" + (i + 1);
            String condition = str(f.get("condition"));
            if (condition == null) condition = "true";
            sb.append("  <Flow name=\"").append(fname).append("\">\n");
            sb.append("    <Condition>").append(condition).append("</Condition>\n");
            sb.append("    <Request>\n");
            List<Map<String, Object>> rq = asList(f.get("request"));
            for (Map<String, Object> step : rq) {
                String policyRef = str(step.get("policy"));
                if (policyRef != null) {
                    sb.append("      <Step><Name>").append(policyRef).append("</Name></Step>\n");
                }
            }
            sb.append("    </Request>\n");
            sb.append("    <Response/>\n");
            sb.append("  </Flow>\n");
        }
        sb.append("  <PostFlow name=\"PostFlow\"><Request/><Response/></PostFlow>\n");
        sb.append("  <HTTPProxyConnection>\n");
        sb.append("    <BasePath>").append(basePath).append("</BasePath>\n");
        sb.append("  </HTTPProxyConnection>\n");
        sb.append("  <RouteRule name=\"default\"><TargetEndpoint>default</TargetEndpoint></RouteRule>\n");
        sb.append("</ProxyEndpoint>\n");
        return sb.toString();
    }

    private String renderTargetEndpoint(Map<String, Object> proxyConfig) {
        String target = str(proxyConfig.get("target"));
        if (target == null) target = "https://mocktarget.apigee.net";
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<TargetEndpoint name=\"default\">\n");
        sb.append("  <PreFlow name=\"PreFlow\"><Request/><Response/></PreFlow>\n");
        sb.append("  <PostFlow name=\"PostFlow\"><Request/><Response/></PostFlow>\n");
        sb.append("  <HTTPTargetConnection>\n");
        sb.append("    <URL>").append(target).append("</URL>\n");
        sb.append("  </HTTPTargetConnection>\n");
        sb.append("</TargetEndpoint>\n");
        return sb.toString();
    }

    private String renderProxyDescriptor(String name, Map<String, Object> proxyConfig) {
        String desc = str(proxyConfig.get("description"));
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<APIProxy name=\"").append(name).append("\" revision=\"1\">\n");
        if (desc != null && !desc.trim().isEmpty()) {
            sb.append("  <Description>").append(desc).append("</Description>\n");
        }
        Map<String, Object> dependsOn = asMap(proxyConfig.get("dependsOn"));
        if (dependsOn.containsKey("sharedFlows")) {
            for (String sf : listOfStrings(dependsOn.get("sharedFlows"))) {
                sb.append("  <DependsOn>sf:").append(sf).append("</DependsOn>\n");
            }
        }
        sb.append("  <ProxyEndpoints><ProxyEndpoint>default</ProxyEndpoint></ProxyEndpoints>\n");
        sb.append("  <TargetEndpoints><TargetEndpoint>default</TargetEndpoint></TargetEndpoints>\n");
        sb.append("</APIProxy>\n");
        return sb.toString();
    }

    private String buildPom(String designName, String name) {
        String group = (designName == null || designName.trim().isEmpty())
                ? "com.apigee.proxy"
                : "com.apigee.proxy." + designName;
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
        sb.append("  <packaging>apiproxy</packaging>\n"); // IMPORTANT
        sb.append("  <name>").append(name).append("</name>\n");
        sb.append("  <build>\n");
        sb.append("    <plugins>\n");
        sb.append("      <plugin>\n");
        sb.append("        <groupId>io.apigee.build-tools.enterprise4g</groupId>\n");
        sb.append("        <artifactId>apigee-edge-maven-plugin</artifactId>\n");
        sb.append("        <version>1.2.1</version>\n");
        sb.append("        <extensions>true</extensions>\n");
        sb.append("        <configuration>\n");
        sb.append("          <org>${apigee.org}</org>\n");
        sb.append("          <env>${apigee.env}</env>\n");
        sb.append("          <bundleType>apiproxy</bundleType>\n");
        sb.append("          <cleanDeployment>true</cleanDeployment>\n");
        sb.append("          <override>true</override>\n");
        sb.append("          <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n");
        sb.append("        </configuration>\n");
        sb.append("        <executions>\n");
        sb.append("          <execution>\n");
        sb.append("            <id>deploy-proxy</id>\n");
        sb.append("            <phase>install</phase>\n");
        sb.append("            <goals>\n");
        sb.append("              <goal>configure</goal>\n");
        sb.append("              <goal>deploy</goal>\n");
        sb.append("            </goals>\n");
        sb.append("          </execution>\n");
        sb.append("        </executions>\n");
        sb.append("      </plugin>\n");
        sb.append("    </plugins>\n");
        sb.append("  </build>\n");
        sb.append("</project>\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object o) {
        if (o instanceof List) return (List<Map<String, Object>>) o;
        return new ArrayList<Map<String, Object>>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return new HashMap<String, Object>();
    }

    private List<String> listOfStrings(Object o) {
        List<String> out = new ArrayList<String>();
        if (o instanceof List) {
            List raw = (List) o;
            for (Object item : raw) {
                if (item != null) out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
}