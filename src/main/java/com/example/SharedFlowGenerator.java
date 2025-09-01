package com.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SharedFlowGenerator {
    public static void generate(List<Map<String, Object>> sharedFlows) throws Exception {
        if (sharedFlows == null) return;
        
        for (Map<String, Object> sf : sharedFlows) {
            String name = (String) sf.get("name");
            String description = sf.containsKey("description") ? 
                (String) sf.get("description") : "Generated Shared Flow";
            
            System.out.println("Generating shared flow bundle for: " + name);
            
            // Create shared flow directory structure
            String sfDir = "sharedflows/" + name;
            Files.createDirectories(Paths.get(sfDir));
            Files.createDirectories(Paths.get(sfDir + "/policies"));
            Files.createDirectories(Paths.get(sfDir + "/sharedflows"));
            Files.createDirectories(Paths.get(sfDir + "/resources/jsc"));
            
            // Generate main shared flow XML
            StringBuilder sfXml = new StringBuilder();
            sfXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sfXml.append("<SharedFlowBundle name=\"").append(name).append("\">\n");
            sfXml.append("    <Description>").append(description).append("</Description>\n");
            
            // Add policies section if policies exist
            if (sf.containsKey("policies")) {
                List<Map<String, Object>> policies = (List<Map<String, Object>>) sf.get("policies");
                generatePolicies(sfDir, policies);
                
                sfXml.append("    <Policies>\n");
                for (Map<String, Object> policy : policies) {
                    String policyName = (String) policy.get("name");
                    sfXml.append("        <Policy>").append(policyName).append("</Policy>\n");
                }
                sfXml.append("    </Policies>\n");
            }
            
            // Add shared flow definition
            sfXml.append("    <SharedFlows>\n");
            sfXml.append("        <SharedFlow>default</SharedFlow>\n");
            sfXml.append("    </SharedFlows>\n");
            sfXml.append("</SharedFlowBundle>\n");
            
            // Write main shared flow XML file
            Files.write(Paths.get(sfDir + "/" + name + ".xml"), sfXml.toString().getBytes());
            
            // Generate default shared flow XML
            generateDefaultSharedFlow(sfDir, sf);
            
            // Create a zip bundle for deployment
            createZipBundle(sfDir, name);
        }
    }
    
    private static void generatePolicies(String sfDir, List<Map<String, Object>> policies) throws Exception {
        for (Map<String, Object> policy : policies) {
            String name = (String) policy.get("name");
            String type = (String) policy.get("type");
            String displayName = policy.containsKey("displayName") ? 
                (String) policy.get("displayName") : name;
            
            StringBuilder policyXml = new StringBuilder();
            policyXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            
            switch (type) {
                case "AssignMessage":
                    Map<String, Object> amConfig = (Map<String, Object>) policy.get("configuration");
                    policyXml.append("<AssignMessage name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    
                    if (amConfig.containsKey("assignTo")) {
                        Map<String, String> assignTo = (Map<String, String>) amConfig.get("assignTo");
                        policyXml.append("    <AssignTo");
                        if (assignTo.containsKey("name")) {
                            policyXml.append(" name=\"").append(assignTo.get("name")).append("\"");
                        }
                        policyXml.append("></AssignTo>\n");
                    }
                    
                    if (amConfig.containsKey("content")) {
                        String content = (String) amConfig.get("content");
                        policyXml.append("    <Set>\n");
                        policyXml.append("        <Payload contentType=\"");
                        if (amConfig.containsKey("contentType")) {
                            policyXml.append(amConfig.get("contentType"));
                        } else {
                            policyXml.append("application/json");
                        }
                        policyXml.append("\">").append(content).append("</Payload>\n");
                        policyXml.append("    </Set>\n");
                    }
                    
                    policyXml.append("</AssignMessage>\n");
                    break;
                
                case "JavaScript":
                    Map<String, Object> jsConfig = (Map<String, Object>) policy.get("configuration");
                    policyXml.append("<Javascript name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    
                    if (jsConfig.containsKey("timeoutInMillis")) {
                        policyXml.append("    <TimeoutInMillis>").append(jsConfig.get("timeoutInMillis")).append("</TimeoutInMillis>\n");
                    }
                    
                    // Handle script file
                    if (jsConfig.containsKey("scriptFile")) {
                        String scriptFile = (String) jsConfig.get("scriptFile");
                        policyXml.append("    <ResourceURL>jsc://").append(scriptFile).append("</ResourceURL>\n");
                        
                        // If script content is provided, write it to a file
                        if (jsConfig.containsKey("scriptContent")) {
                            String scriptContent = (String) jsConfig.get("scriptContent");
                            Files.write(Paths.get(sfDir + "/resources/jsc/" + scriptFile), 
                                       scriptContent.getBytes());
                        }
                    }
                    
                    policyXml.append("</Javascript>\n");
                    break;
                    
                default:
                    // Generic policy template for other types
                    policyXml.append("<").append(type).append(" name=\"").append(name).append("\">\n");
                    policyXml.append("    <DisplayName>").append(displayName).append("</DisplayName>\n");
                    policyXml.append("</").append(type).append(">\n");
            }
            
            Files.write(Paths.get(sfDir + "/policies/" + name + ".xml"), policyXml.toString().getBytes());
        }
    }
    
    private static void generateDefaultSharedFlow(String sfDir, Map<String, Object> sf) throws Exception {
        StringBuilder flowXml = new StringBuilder();
        flowXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        flowXml.append("<SharedFlow name=\"default\">\n");
        
        if (sf.containsKey("flows")) {
            List<Map<String, Object>> flows = (List<Map<String, Object>>) sf.get("flows");
            for (Map<String, Object> flow : flows) {
                if (flow.containsKey("request")) {
                    List<Map<String, Object>> requestSteps = (List<Map<String, Object>>) flow.get("request");
                    for (Map<String, Object> step : requestSteps) {
                        if (step.containsKey("policy")) {
                            String policy = (String) step.get("policy");
                            flowXml.append("    <Step>\n");
                            flowXml.append("        <Name>").append(policy).append("</Name>\n");
                            flowXml.append("    </Step>\n");
                        }
                    }
                }
            }
        } else if (sf.containsKey("policies") && !((List<Map<String, Object>>) sf.get("policies")).isEmpty()) {
            // If no flows defined but policies exist, include the first policy
            String firstPolicy = (String) ((List<Map<String, Object>>) sf.get("policies")).get(0).get("name");
            flowXml.append("    <Step>\n");
            flowXml.append("        <Name>").append(firstPolicy).append("</Name>\n");
            flowXml.append("    </Step>\n");
        }
        
        flowXml.append("</SharedFlow>\n");
        
        Files.write(Paths.get(sfDir + "/sharedflows/default.xml"), flowXml.toString().getBytes());
    }
    
    private static void createZipBundle(String sfDir, String name) throws Exception {
        String zipFileName = sfDir + "/" + name + ".zip";
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            File sfDirFile = new File(sfDir);
            zipDirectory(sfDirFile, name, zipOut);
        }
        System.out.println("Created ZIP bundle: " + zipFileName);
    }
    
    private static void zipDirectory(File folder, String parentFolder, ZipOutputStream zipOut) throws Exception {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zipOut);
                continue;
            }
            
            // Skip the zip file itself
            if (file.getName().endsWith(".zip")) continue;
            
            FileInputStream fis = new FileInputStream(file);
            String entryPath = parentFolder + "/" + file.getName();
            ZipEntry zipEntry = new ZipEntry(entryPath);
            zipOut.putNextEntry(zipEntry);
            
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }
    }
}