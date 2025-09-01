# Apigee Design Driven Deployment System

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

This repository implements a design-driven approach to Apigee X development where developers edit YAML design files and the system automatically generates and deploys Apigee artifacts (shared flows, proxies, API products).

## Working Effectively

**Prerequisites:**
- Java 17 (OpenJDK Temurin) - already available in the environment
- Apache Maven 3.9+ - already available in the environment

**Bootstrap, build, and generate artifacts:**
```bash
# Clean and regenerate all artifacts from design files
mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"
```
- Takes 1-3 seconds to complete. NEVER CANCEL. Set timeout to 60+ seconds.
- This compiles the Java generator and processes all YAML files in `designs/` directory
- Generates artifacts in `generated/` directory with proper Maven structure

**Local testing and validation:**
```bash
# Run comprehensive local validation
./scripts/test-local.sh
```
- Takes 5-10 seconds. NEVER CANCEL. Set timeout to 60+ seconds.
- Validates YAML syntax, generation process, Maven configs, and artifact structure
- Does NOT require Apigee deployment - safe to run anytime

**API deployment verification:**
```bash
# Structure validation mode (local testing)
./scripts/test-deployed-apis.sh

# Full API testing mode (requires deployed APIs)
export APIGEE_ORG=your-org
export APIGEE_ENV=eval
./scripts/test-deployed-apis.sh
```
- Takes 1-3 seconds for structure validation. NEVER CANCEL. Set timeout to 60+ seconds.
- Without env vars: validates generated structure only
- With env vars: makes HTTP requests to deployed endpoints (can take 30-90 seconds)

## Build and Test Commands

**Main project compilation:**
```bash
mvn -q compile
```
- Takes 1-3 seconds. Set timeout to 60+ seconds.

**Generated artifact builds:**
```bash
# Build a specific proxy
cd generated/proxies/[proxy-name]
mvn -q validate package

# Build a specific shared flow  
cd generated/sharedflows/[sharedflow-name]
mvn -q validate package
```
- Each takes 1-3 seconds. Set timeout to 60+ seconds.

## Validation Requirements

**ALWAYS run these validation steps after making changes:**

1. **Generation validation:**
   ```bash
   mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"
   ```

2. **Local testing:**
   ```bash
   ./scripts/test-local.sh
   ```

3. **Structure validation:**
   ```bash
   ./scripts/test-deployed-apis.sh
   ```

**Manual validation scenarios:**
- After modifying design files: Verify generated artifacts match expected structure
- After changing Java code: Test generation process produces valid Maven artifacts  
- Before committing: Ensure `generated/` directory contains proper proxy/sharedflow structure

**Complete workflow validation:**
```bash
# Full validation sequence
mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"
./scripts/test-local.sh
./scripts/test-deployed-apis.sh
```
- Takes 10-15 seconds total. NEVER CANCEL. Set timeout to 120+ seconds.
- This validates the entire generation → testing → verification flow

## Repository Structure

**Key directories:**
- `designs/` - YAML design files (input)
- `src/main/java/` - Java generator code
- `generated/` - Generated Apigee artifacts (output, gitignored)
- `scripts/` - Testing and validation scripts
- `.github/workflows/` - CI/CD pipeline

**Important files:**
- `pom.xml` - Main Maven configuration
- `designs/sample1.yaml` - Example design file
- `scripts/test-local.sh` - Local validation script
- `scripts/test-deployed-apis.sh` - API verification script

## Design File Format

Design files use this structure:
```yaml
apigee:
  name: designname
  sharedFlows:
    - name: flow-name
      policies: [policy definitions]
  proxies:
    - name: proxy-name
      basePath: /v1/path
      target: https://backend.example.com
      policies: [policy definitions]
  apiProducts:
    - name: ProductName
      proxies: [proxy-name]
```

**Common design file operations:**
- Add new proxy: Create entry in `proxies` section
- Add shared flow: Create entry in `sharedFlows` section  
- Reference shared flow: Add to proxy's `dependsOn.sharedFlows`
- Add policies: Include in `policies` array with type and configuration

## Generated Artifact Structure

The generation process creates:
```
generated/
├── proxies/[proxy-name]/
│   ├── pom.xml
│   └── apiproxy/
├── sharedflows/[flow-name]/
│   ├── pom.xml  
│   └── sharedflowbundle/
└── config/
    ├── pom.xml
    └── entities/
```

**Each generated module:**
- Has its own Maven `pom.xml` with Apigee plugin configuration
- Can be built independently: `cd generated/[type]/[name] && mvn package`
- Contains proper Apigee X artifact structure

## CI/CD Pipeline

**GitHub Actions workflow (`.github/workflows/apigee-deploy.yml`):**
1. Generate artifacts from design files (30-60 seconds)
2. Deploy shared flows first (2-5 minutes per flow)
3. Deploy proxies (2-5 minutes per proxy)  
4. Deploy API products (1-3 minutes)
5. Verify API endpoints (1-5 minutes depending on API count)

**Required secrets:**
- `APIGEE_ORG` - Apigee organization name
- `APIGEE_ENV` - Target environment (e.g., eval, prod)
- `APIGEE_SA_KEY_JSON` - Service account key JSON

**Deployment timeouts:** NEVER CANCEL deployment steps. Each Maven deploy can take 5-10 minutes. Set timeouts to 15+ minutes for deployment steps.

## Common Tasks

**Add a new API proxy:**
1. Edit design file in `designs/` directory
2. Add proxy entry with name, basePath, target
3. Run generation: `mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"`
4. Validate: `./scripts/test-local.sh`

**Modify existing proxy:**
1. Edit design file 
2. Regenerate artifacts
3. Test locally before pushing

**Add shared flow:**
1. Add sharedFlow entry to design file
2. Reference in proxy's `dependsOn.sharedFlows` if needed
3. Regenerate and validate

**Troubleshoot generation issues:**
- Check YAML syntax in design files
- Verify Java compilation: `mvn compile`
- Check console output for specific errors
- Validate design file structure matches expected format
- Unknown policy types or fields are handled gracefully but may not work as expected

**Common error patterns:**
- Maven compilation errors: Check Java syntax in `src/main/java/`
- Generation failure: Verify YAML files in `designs/` are valid
- Missing generated artifacts: Ensure design files follow proper structure
- Build failures: Validate generated `pom.xml` files are properly formed

## Time Expectations

- **Generation:** 1-3 seconds  
- **Local testing:** 5-10 seconds
- **Compilation:** 1-3 seconds
- **Structure validation:** 1-3 seconds
- **Full CI/CD pipeline:** 10-20 minutes
- **Individual Maven deploys:** 2-5 minutes each

**CRITICAL:** Never cancel long-running operations. Maven deploys to Apigee can take 5+ minutes per artifact.

## Common Output Reference

**Generation success:**
```
=== Apigee Design Parser ===
Processing design file: sample1.yaml
  [SF] Generating: common-logging
  [PX] Generating: sample-proxy
Generation complete. See 'generated/' directory.
```

**Local test success:**
```
[INFO] ✅ Generation process works
[INFO] ✅ Generated artifacts are valid  
[INFO] ✅ Maven configurations are correct
[INFO] ✅ Design files have valid syntax
[INFO] 🎉 All local tests passed!
```

**Typical generated structure:**
```
generated/
├── config/pom.xml
├── proxies/sample-api/pom.xml
├── proxies/sample-proxy/pom.xml
└── sharedflows/common-logging/pom.xml
```

## Quick Reference Commands

**Essential daily commands:**
```bash
# Generate and validate everything
mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser" && ./scripts/test-local.sh

# Just regenerate from designs
mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"

# Quick validation only
./scripts/test-local.sh

# Structure check only  
./scripts/test-deployed-apis.sh
```

**When things go wrong:**
```bash
# Clean everything and start fresh
rm -rf generated/ target/
mvn clean
mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"
```