package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

public class ProxyGenerator {

    private final String outputDir;

    public ProxyGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    public void generateProxy(Map<String, Object> proxyConfig, String designName) throws IOException {
        String name = (String) proxyConfig.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Proxy missing name");
        }
        System.out.println("Generating proxy: " + name);

        String dir = outputDir + "/" + name;
        Path apiproxy = Paths.get(dir, "apiproxy");
        Path policiesPath = apiproxy.resolve("policies");
        Path proxiesPath = apiproxy.resolve("proxies");
        Path targetsPath = apiproxy.resolve("targets");

        Files.createDirectories(policiesPath);
        Files.createDirectories(proxiesPath);
        Files.createDirectories(targetsPath);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies =
                (List<Map<String, Object>>) proxyConfig.getOrDefault("policies", List.of());
        for (Map<String, Object> p : policies) {
            String pName = (String) p.get("name");
            String pType = (String) p.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = (Map<String, Object>) p.getOrDefault("configuration", Map.of());
            Files.writeString(policiesPath.resolve(pName + ".xml"), renderPolicy(pName, pType, cfg));
        }

        Files.writeString(proxiesPath.resolve("default.xml"),
                renderProxyEndpoint(name, proxyConfig));
        Files.writeString(targetsPath.resolve("default.xml"),
                renderTargetEndpoint(proxyConfig));
        Files.writeString(apiproxy.resolve(name + ".xml"),
                renderProxyDescriptor(name, proxyConfig));
        Files.writeString(Paths.get(dir, "pom.xml"),
                renderPom(designName, name));

        System.out.println("Proxy bundle generated: " + name);
    }

    private String renderPolicy(String name, String type, Map<String, Object> cfg) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        switch (type) {
            case "Quota" -> sb.append("<Quota name=\"").append(name).append("\">\n")
                    .append("  <DisplayName>").append(name).append("</DisplayName>\n")
                    .append("  <Allow count=\"").append(cfg.getOrDefault("limit", 1000)).append("\"/>\n")
                    .append("  <Interval>1</Interval>\n")
                    .append("  <TimeUnit>").append(cfg.getOrDefault("timeUnit", "minute")).append("</TimeUnit>\n")
                    .append("</Quota>\n");
            default -> sb.append("<Policy name=\"").append(name).append("\">\n")
                    .append("  <DisplayName>").append(name).append("</DisplayName>\n")
                    .append("</Policy>\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String renderProxyEndpoint(String proxyName, Map<String, Object> proxyConfig) {
        String basePath = (String) proxyConfig.getOrDefault("basePath", "/" + proxyName);
        List<Map<String, Object>> flows = (List<Map<String, Object>>) proxyConfig.getOrDefault("flows", List.of());
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ProxyEndpoint name=\"default\">\n")
          .append("  <PreFlow name=\"PreFlow\"><Request/><Response/></PreFlow>\n");
        for (Map<String, Object> f : flows) {
            String fname = (String) f.getOrDefault("name", "default");
            String condition = String.valueOf(f.getOrDefault("condition", "true"));
            sb.append("  <Flow name=\"").append(fname).append("\">\n")
              .append("    <Condition>").append(condition).append("</Condition>\n")
              .append("    <Request>\n");
            List<Map<String, Object>> rq = (List<Map<String, Object>>) f.getOrDefault("request", List.of());
            for (Map<String, Object> step : rq) {
                sb.append("      <Step><Name>").append(step.get("policy")).append("</Name></Step>\n");
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

    private String renderTargetEndpoint(Map<String, Object> proxyConfig) {
        String target = (String) proxyConfig.getOrDefault("target", "https://mocktarget.apigee.net");
        return """
               <?xml version="1.0" encoding="UTF-8"?>
               <TargetEndpoint name="default">
                 <PreFlow name="PreFlow"><Request/><Response/></PreFlow>
                 <PostFlow name="PostFlow"><Request/><Response/></PostFlow>
                 <HTTPTargetConnection>
                   <URL>%s</URL>
                 </HTTPTargetConnection>
               </TargetEndpoint>
               """.formatted(target);
    }

    @SuppressWarnings("unchecked")
    private String renderProxyDescriptor(String name, Map<String, Object> proxyConfig) {
        String desc = (String) proxyConfig.getOrDefault("description", "");
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<APIProxy name=\"").append(name).append("\" revision=\"1\">\n");
        if (!desc.isBlank()) {
            sb.append("  <Description>").append(desc).append("</Description>\n");
        }
        Map<String, Object> dependsOn = (Map<String, Object>) proxyConfig.getOrDefault("dependsOn", Map.of());
        List<String> sfs = (List<String>) dependsOn.getOrDefault("sharedFlows", List.of());
        for (String sf : sfs) {
            sb.append("  <DependsOn>sf:").append(sf).append("</DependsOn>\n");
        }
        sb.append("  <ProxyEndpoints><ProxyEndpoint>default</ProxyEndpoint></ProxyEndpoints>\n")
          .append("  <TargetEndpoints><TargetEndpoint>default</TargetEndpoint></TargetEndpoints>\n")
          .append("</APIProxy>\n");
        return sb.toString();
    }

    private String renderPom(String designName, String name) {
        return """
               <?xml version="1.0" encoding="UTF-8"?>
               <project xmlns="http://maven.apache.org/POM/4.0.0"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>com.apigee.edge.proxy.%s</groupId>
                 <artifactId>%s</artifactId>
                 <version>1.0</version>
                 <packaging>pom</packaging>
                 <build>
                   <plugins>
                     <plugin>
                       <groupId>io.apigee.build-tools.enterprise4g</groupId>
                       <artifactId>apigee-edge-maven-plugin</artifactId>
                       <version>1.2.1</version>
                       <executions>
                         <execution>
                           <id>configure</id>
                           <phase>package</phase>
                           <goals><goal>configure</goal></goals>
                         </execution>
                         <execution>
                           <id>deploy</id>
                           <phase>install</phase>
                           <goals><goal>deploy</goal></goals>
                         </execution>
                       </executions>
                       <configuration>
                         <org>${apigee.org}</org>
                         <env>${apigee.env}</env>
                         <bundleType>apiproxy</bundleType>
                         <cleanDeployment>true</cleanDeployment>
                         <override>true</override>
                         <serviceAccountFile>${serviceAccountFile}</serviceAccountFile>
                       </configuration>
                     </plugin>
                   </plugins>
                 </build>
               </project>
               """.formatted(designName, name);
    }
}