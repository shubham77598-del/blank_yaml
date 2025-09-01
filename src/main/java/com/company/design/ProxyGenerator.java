package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ProxyGenerator {

    private final String outputDir;

    public ProxyGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    public void generateProxy(Map<String,Object> proxyConfig, String designName) throws IOException {
        String name = val(proxyConfig.get("name"));
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy missing name");
        }
        System.out.println("  [PX] Generating: " + name);

        Path moduleRoot = Paths.get(outputDir, name);
        Path apiproxy = moduleRoot.resolve("apiproxy");
        Path policiesDir = apiproxy.resolve("policies");
        Path proxiesDir = apiproxy.resolve("proxies");
        Path targetsDir = apiproxy.resolve("targets");
        Files.createDirectories(policiesDir);
        Files.createDirectories(proxiesDir);
        Files.createDirectories(targetsDir);

        List<Map<String,Object>> policies = listOfMaps(proxyConfig.get("policies"));
        for (Map<String,Object> p : policies) {
            String pName = val(p.get("name"));
            if (pName == null) continue;
            String pType = val(p.get("type"));
            Map<String,Object> cfg = mapOf(p.get("configuration"));
            Files.write(policiesDir.resolve(pName + ".xml"),
                renderPolicy(pName, pType, cfg).getBytes("UTF-8"));
        }

        Files.write(proxiesDir.resolve("default.xml"),
            renderProxyEndpoint(name, proxyConfig).getBytes("UTF-8"));
        Files.write(targetsDir.resolve("default.xml"),
            renderTargetEndpoint(proxyConfig).getBytes("UTF-8"));
        Files.write(apiproxy.resolve(name + ".xml"),
            renderDescriptor(name, proxyConfig).getBytes("UTF-8"));

        // Optional metadata
        Files.write(apiproxy.resolve("config.json"),
            "{\"type\":\"apiproxy\",\"version\":\"1.0\"}\n".getBytes("UTF-8"));

        Files.write(moduleRoot.resolve("pom.xml"),
            buildPom(designName, name).getBytes("UTF-8"));
    }

    private String renderPolicy(String name, String type, Map<String,Object> cfg) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if ("Quota".equals(type)) {
            String limit = val(cfg.get("limit")); if (limit == null) limit = "1000";
            String timeUnit = val(cfg.get("timeUnit")); if (timeUnit == null) timeUnit = "minute";
            sb.append("<Quota name=\"").append(name).append("\">\n")
              .append("  <DisplayName>").append(name).append("</DisplayName>\n")
              .append("  <Allow count=\"").append(limit).append("\"/>\n")
              .append("  <Interval>1</Interval>\n")
              .append("  <TimeUnit>").append(timeUnit).append("</TimeUnit>\n")
              .append("</Quota>\n");
        } else {
            sb.append("<Policy name=\"").append(name).append("\">\n")
              .append("  <DisplayName>").append(name).append("</DisplayName>\n")
              .append("</Policy>\n");
        }
        return sb.toString();
    }

    private String renderProxyEndpoint(String proxyName, Map<String,Object> cfg) {
        String basePath = val(cfg.get("basePath"));
        if (basePath == null) basePath = "/" + proxyName;
        List<Map<String,Object>> flows = listOfMaps(cfg.get("flows"));

        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ProxyEndpoint name=\"default\">\n")
          .append("  <PreFlow name=\"PreFlow\"><Request/><Response/></PreFlow>\n");
        for (Map<String,Object> flow : flows) {
            String fname = val(flow.get("name")); if (fname == null) fname = "flow";
            String cond = val(flow.get("condition")); if (cond == null) cond = "true";
            sb.append("  <Flow name=\"").append(fname).append("\">\n")
              .append("    <Condition>").append(cond).append("</Condition>\n")
              .append("    <Request>\n");
            for (Map<String,Object> step : listOfMaps(flow.get("request"))) {
                String ref = val(step.get("policy"));
                if (ref != null) sb.append("      <Step><Name>").append(ref).append("</Name></Step>\n");
            }
            sb.append("    </Request>\n")
              .append("    <Response/>\n")
              .append("  </Flow>\n");
        }
        sb.append("  <PostFlow name=\"PostFlow\"><Request/><Response/></PostFlow>\n")
          .append("  <HTTPProxyConnection>\n")
          .append("    <BasePath>").append(basePath).append("</BasePath>\n")
          .append("  </HTTPProxyConnection>\n")
          .append("  <RouteRule name=\"default\"><TargetEndpoint>default</TargetEndpoint></RouteRule>\n")
          .append("</ProxyEndpoint>\n");
        return sb.toString();
    }

    private String renderTargetEndpoint(Map<String,Object> cfg) {
        String target = val(cfg.get("target"));
        if (target == null) target = "https://mocktarget.apigee.net";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<TargetEndpoint name=\"default\">\n" +
            "  <PreFlow name=\"PreFlow\"><Request/><Response/></PreFlow>\n" +
            "  <PostFlow name=\"PostFlow\"><Request/><Response/></PostFlow>\n" +
            "  <HTTPTargetConnection>\n" +
            "    <URL>" + target + "</URL>\n" +
            "  </HTTPTargetConnection>\n" +
            "</TargetEndpoint>\n";
    }

    private String renderDescriptor(String name, Map<String,Object> cfg) {
        String desc = val(cfg.get("description"));
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<APIProxy name=\"").append(name).append("\" revision=\"1\">\n");
        if (desc != null && !desc.trim().isEmpty())
            sb.append("  <Description>").append(desc).append("</Description>\n");

        Map<String,Object> depends = mapOf(cfg.get("dependsOn"));
        Object sfs = depends.get("sharedFlows");
        if (sfs instanceof List) {
            for (Object sf : (List)sfs) {
                if (sf != null) sb.append("  <DependsOn>sf:").append(sf).append("</DependsOn>\n");
            }
        }
        sb.append("  <ProxyEndpoints><ProxyEndpoint>default</ProxyEndpoint></ProxyEndpoints>\n")
          .append("  <TargetEndpoints><TargetEndpoint>default</TargetEndpoint></TargetEndpoints>\n")
          .append("</APIProxy>\n");
        return sb.toString();
    }

    private String buildPom(String designName, String name) {
        String groupId = (designName == null || designName.trim().isEmpty())
            ? "com.apigee.proxy"
            : "com.apigee.proxy." + designName;

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>"+groupId+"</groupId>\n" +
            "  <artifactId>"+name+"</artifactId>\n" +
            "  <version>1.0</version>\n" +
            "  <packaging>pom</packaging>\n" +
            "  <name>"+name+"</name>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>org.apache.maven.plugins</groupId>\n" +
            "        <artifactId>maven-antrun-plugin</artifactId>\n" +
            "        <version>3.1.0</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>package-proxy</id>\n" +
            "            <phase>package</phase>\n" +
            "            <configuration>\n" +
            "              <target>\n" +
            "                <mkdir dir=\"${project.build.directory}\"/>\n" +
            "                <zip destfile=\"${project.build.directory}/${project.artifactId}-${project.version}.zip\" " +
            "                     basedir=\"${project.basedir}/apiproxy\"/>\n" +
            "              </target>\n" +
            "            </configuration>\n" +
            "            <goals><goal>run</goal></goals>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "      </plugin>\n" +
            "      <plugin>\n" +
            "        <groupId>com.google.cloud.apigee</groupId>\n" +
            "        <artifactId>apigee-maven-plugin</artifactId>\n" +
            "        <version>1.0.0</version>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <id>import-proxy</id>\n" +
            "            <phase>verify</phase>\n" +
            "            <goals><goal>import-proxy</goal></goals>\n" +
            "            <configuration>\n" +
            "              <file>${project.build.directory}/${project.artifactId}-${project.version}.zip</file>\n" +
            "              <name>"+name+"</name>\n" +
            "              <override>true</override>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "          <execution>\n" +
            "            <id>deploy-proxy</id>\n" +
            "            <phase>install</phase>\n" +
            "            <goals><goal>deploy-proxy</goal></goals>\n" +
            "            <configuration>\n" +
            "              <name>"+name+"</name>\n" +
            "              <environment>${apigee.env}</environment>\n" +
            "              <override>true</override>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "        <configuration>\n" +
            "          <organization>${apigee.org}</organization>\n" +
            "          <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    private List<Map<String,Object>> listOfMaps(Object o) {
        List<Map<String,Object>> out = new ArrayList<>();
        if (o instanceof List)
            for (Object i : (List)o)
                if (i instanceof Map) out.add((Map<String,Object>) i);
        return out;
    }

    private Map<String,Object> mapOf(Object o) {
        if (o instanceof Map) return (Map<String,Object>) o;
        return new HashMap<>();
    }

    private String val(Object o){ return o==null?null:String.valueOf(o); }
}