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
            throw new IllegalArgumentException("SharedFlow missing name");
        }
        System.out.println("  [SF] Generating: " + name);

        Path moduleRoot = Paths.get(outputDir, name);
        Path sharedFlowBundle = moduleRoot.resolve("sharedflowbundle");
        Path policiesDir = sharedFlowBundle.resolve("policies");
        Files.createDirectories(policiesDir);

        List<Map<String,Object>> policies = listOfMaps(sharedFlowConfig.get("policies"));
        for (Map<String,Object> p : policies) {
            String pName = val(p.get("name"));
            if (pName == null) continue;
            String pType = val(p.get("type"));
            Map<String,Object> cfg = mapOf(p.get("configuration"));
            Files.write(policiesDir.resolve(pName + ".xml"),
                renderPolicy(pName, pType, cfg).getBytes("UTF-8"));
        }

        Files.write(sharedFlowBundle.resolve(name + ".xml"),
            renderDescriptor(name, sharedFlowConfig).getBytes("UTF-8"));

        // Optional metadata
        Files.write(sharedFlowBundle.resolve("config.json"),
            "{\"type\":\"sharedflow\",\"version\":\"1.0\"}\n".getBytes("UTF-8"));

        Files.write(moduleRoot.resolve("pom.xml"),
            buildPom(designName, name).getBytes("UTF-8"));
    }

    private String renderPolicy(String name, String type, Map<String,Object> cfg) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        String displayName = val(cfg.get("displayName"));
        if (displayName == null) displayName = name;
        
        switch (type) {
            case "RateLimiter":
                String limit = val(cfg.get("limit")); if (limit == null) limit = "100";
                String timeUnit = val(cfg.get("timeUnit")); if (timeUnit == null) timeUnit = "minute";
                String interval = val(cfg.get("interval")); if (interval == null) interval = "1";
                sb.append("<RateLimiter name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n")
                  .append("  <Allow count=\"").append(limit).append("\"/>\n")
                  .append("  <Interval>").append(interval).append("</Interval>\n")
                  .append("  <TimeUnit>").append(timeUnit).append("</TimeUnit>\n")
                  .append("</RateLimiter>\n");
                break;
                
            case "VerifyAPIKey":
                String apiKey = val(cfg.get("apiKey"));
                String keyLocation = val(cfg.get("keyLocation"));
                if (apiKey == null && keyLocation == null) keyLocation = "request.header.x-api-key";
                sb.append("<VerifyAPIKey name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n");
                if (apiKey != null) {
                    sb.append("  <APIKey ref=\"").append(apiKey).append("\"/>\n");
                } else if (keyLocation != null) {
                    sb.append("  <APIKey ref=\"").append(keyLocation).append("\"/>\n");
                }
                sb.append("</VerifyAPIKey>\n");
                break;
                
            case "JavaScript":
                String scriptFile = val(cfg.get("scriptFile"));
                String scriptContent = val(cfg.get("scriptContent"));
                sb.append("<Javascript name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n");
                if (scriptFile != null) {
                    sb.append("  <ResourceURL>jsc://").append(scriptFile).append("</ResourceURL>\n");
                } else if (scriptContent != null) {
                    sb.append("  <Source><![CDATA[\n").append(scriptContent).append("\n  ]]></Source>\n");
                }
                sb.append("</Javascript>\n");
                break;
                
            case "AssignMessage":
                String payload = val(cfg.get("payload"));
                String verb = val(cfg.get("verb"));
                sb.append("<AssignMessage name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n");
                if (verb != null) {
                    sb.append("  <Set><Verb>").append(verb).append("</Verb></Set>\n");
                }
                if (payload != null) {
                    sb.append("  <Set><Payload contentType=\"application/json\">").append(payload).append("</Payload></Set>\n");
                }
                sb.append("</AssignMessage>\n");
                break;
                
            case "JSONThreatProtection":
                String arrayLimit = val(cfg.get("arrayElementCount")); if (arrayLimit == null) arrayLimit = "100";
                String objectLimit = val(cfg.get("objectEntryCount")); if (objectLimit == null) objectLimit = "100";
                sb.append("<JSONThreatProtection name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n")
                  .append("  <ArrayElementCount>").append(arrayLimit).append("</ArrayElementCount>\n")
                  .append("  <ObjectEntryCount>").append(objectLimit).append("</ObjectEntryCount>\n")
                  .append("</JSONThreatProtection>\n");
                break;
                
            case "OAuthV2":
                String operation = val(cfg.get("operation")); if (operation == null) operation = "VerifyAccessToken";
                sb.append("<OAuthV2 name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n")
                  .append("  <Operation>").append(operation).append("</Operation>\n")
                  .append("</OAuthV2>\n");
                break;
                
            case "MessageLogging":
                String logLevel = val(cfg.get("logLevel")); if (logLevel == null) logLevel = "INFO";
                boolean logToStdout = "true".equals(val(cfg.get("logToStdout")));
                sb.append("<MessageLogging name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n");
                if (logToStdout) {
                    sb.append("  <Syslog><LogLevel>").append(logLevel).append("</LogLevel></Syslog>\n");
                } else {
                    sb.append("  <Syslog><LogLevel>").append(logLevel).append("</LogLevel></Syslog>\n");
                }
                sb.append("</MessageLogging>\n");
                break;
                
            case "SpikeArrest":
                String rate = val(cfg.get("rate")); if (rate == null) rate = "10ps";
                sb.append("<SpikeArrest name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n")
                  .append("  <Rate>").append(rate).append("</Rate>\n")
                  .append("</SpikeArrest>\n");
                break;
                
            default:
                System.out.println("  [WARNING] Unknown policy type: " + type + " for " + name + ". Creating generic policy.");
                sb.append("<").append(type).append(" name=\"").append(name).append("\">\n")
                  .append("  <DisplayName>").append(displayName).append("</DisplayName>\n")
                  .append("</").append(type).append(">\n");
                break;
        }
        return sb.toString();
    }

    private String renderDescriptor(String name, Map<String,Object> cfg) {
        String desc = val(cfg.get("description"));
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<SharedFlowBundle name=\"").append(name).append("\" revision=\"1\">\n");
        if (desc != null && !desc.trim().isEmpty())
            sb.append("  <Description>").append(desc).append("</Description>\n");

        sb.append("  <Flows>\n");
        List<Map<String,Object>> flows = listOfMaps(cfg.get("flows"));
        for (Map<String,Object> flow : flows) {
            String fname = val(flow.get("name")); if (fname == null) fname = "flow";
            String cond = val(flow.get("condition")); if (cond == null) cond = "true";
            sb.append("    <Flow name=\"").append(fname).append("\">\n")
              .append("      <Condition>").append(cond).append("</Condition>\n")
              .append("      <Request>\n");
            for (Map<String,Object> step : listOfMaps(flow.get("request"))) {
                String ref = val(step.get("policy"));
                if (ref != null) sb.append("        <Step><Name>").append(ref).append("</Name></Step>\n");
            }
            sb.append("      </Request>\n")
              .append("      <Response/>\n")
              .append("    </Flow>\n");
        }
        sb.append("  </Flows>\n");
        sb.append("</SharedFlowBundle>\n");
        return sb.toString();
    }

    private String buildPom(String designName, String name) {
        String groupId = (designName == null || designName.trim().isEmpty())
            ? "com.apigee.sharedflow"
            : "com.apigee.sharedflow." + designName;

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
            "            <id>package-sharedflow</id>\n" +
            "            <phase>package</phase>\n" +
            "            <configuration>\n" +
            "              <target>\n" +
            "                <mkdir dir=\"${project.build.directory}\"/>\n" +
            "                <zip destfile=\"${project.build.directory}/${project.artifactId}-${project.version}.zip\" " +
            "                     basedir=\"${project.basedir}/sharedflowbundle\"/>\n" +
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
            "            <id>import-sharedflow</id>\n" +
            "            <phase>verify</phase>\n" +
            "            <goals><goal>import-shared-flow</goal></goals>\n" +
            "            <configuration>\n" +
            "              <file>${project.build.directory}/${project.artifactId}-${project.version}.zip</file>\n" +
            "              <name>"+name+"</name>\n" +
            "              <override>true</override>\n" +
            "            </configuration>\n" +
            "          </execution>\n" +
            "          <execution>\n" +
            "            <id>deploy-sharedflow</id>\n" +
            "            <phase>install</phase>\n" +
            "            <goals><goal>deploy-shared-flow</goal></goals>\n" +
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