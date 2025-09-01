package com.company.design;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

public class SharedFlowGenerator {

    private final String outputDir;

    public SharedFlowGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    public void generateSharedFlow(Map<String, Object> sfConfig, String designName) throws IOException {
        String name = (String) sfConfig.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shared flow missing name");
        }
        System.out.println("Generating shared flow: " + name);

        String dir = outputDir + "/" + name;
        Path bundle = Paths.get(dir, "sharedflowbundle");
        Path policiesPath = bundle.resolve("policies");
        Files.createDirectories(policiesPath);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies =
                (List<Map<String, Object>>) sfConfig.getOrDefault("policies", List.of());
        for (Map<String, Object> p : policies) {
            String pName = (String) p.get("name");
            String pType = (String) p.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = (Map<String, Object>) p.getOrDefault("configuration", Map.of());
            Files.writeString(policiesPath.resolve(pName + ".xml"), renderPolicy(pName, pType, cfg));
        }

        Files.writeString(bundle.resolve("sharedflowbundle.xml"),
                renderSharedFlowXml(name, policies));

        Files.writeString(Paths.get(dir, "pom.xml"),
                renderPom(designName, name));

        System.out.println("Shared flow bundle generated: " + name);
    }

    private String renderPolicy(String name, String type, Map<String, Object> cfg) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        switch (type) {
            case "VerifyAPIKey" -> sb.append("<VerifyAPIKey name=\"").append(name).append("\">\n")
                    .append("  <DisplayName>").append(name).append("</DisplayName>\n")
                    .append("  <APIKey ref=\"")
                    .append(cfg.getOrDefault("keyLocation", "request.header.x-api-key"))
                    .append("\"/>\n")
                    .append("</VerifyAPIKey>\n");
            default -> sb.append("<Policy name=\"").append(name).append("\">\n")
                    .append("  <DisplayName>").append(name).append("</DisplayName>\n")
                    .append("</Policy>\n");
        }
        return sb.toString();
    }

    private String renderSharedFlowXml(String name, List<Map<String, Object>> policies) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<SharedFlowBundle name=\"").append(name).append("\" revision=\"1\">\n")
          .append("  <SharedFlows>\n")
          .append("    <SharedFlow name=\"default\">\n");
        for (Map<String, Object> p : policies) {
            sb.append("      <Step><Name>").append(p.get("name")).append("</Name></Step>\n");
        }
        sb.append("    </SharedFlow>\n")
          .append("  </SharedFlows>\n")
          .append("</SharedFlowBundle>\n");
        return sb.toString();
    }

    private String renderPom(String designName, String name) {
        return """
               <?xml version="1.0" encoding="UTF-8"?>
               <project xmlns="http://maven.apache.org/POM/4.0.0"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>com.apigee.edge.sharedflow.%s</groupId>
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
                         <org>${env.APIGEE_ORG}</org>
                         <env>${env.APIGEE_ENV}</env>
                         <bundleType>sharedflow</bundleType>
                         <cleanDeployment>true</cleanDeployment>
                         <override>true</override>
                         <serviceAccountFile>${env.APIGEE_SA_KEY_FILE}</serviceAccountFile>
                       </configuration>
                     </plugin>
                   </plugins>
                 </build>
               </project>
               """.formatted(designName, name);
    }
}