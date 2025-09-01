# Apigee Design-Driven Deployment System

This repository implements a design-driven approach for Apigee X: write YAML design files, automatically generate Apigee shared flows, proxies, and config entities, then deploy them via GitHub Actions or local scripts.

**ALWAYS** reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Bootstrap, Build, and Test Process

**NEVER CANCEL any build or generation commands.** All operations complete within 3 minutes.

### Prerequisites
- Java 17+ (OpenJDK Temurin recommended)
- Maven 3.9+
- PowerShell 7+ (for local deployment scripts)

### Bootstrap and Build Commands
Run these commands in order from the repository root:

1. **Check Java version**: `java -version` (must be 17+)
2. **Check Maven version**: `mvn --version` 
3. **Clean compile**: `mvn clean compile` (takes ~3 seconds, timeout: 60s)
4. **Generate Apigee bundles**: `mvn -q -DcleanGenerated=true compile exec:java "-Dexec.mainClass=com.company.design.DesignParser"` (takes ~2 seconds, timeout: 60s)
5. **Test generated module builds**: 
   - `cd generated/sharedflows/common-logging && mvn clean compile` (takes ~5 seconds, timeout: 60s)
   - `cd ../../proxies/sample-proxy && mvn clean compile` (takes ~1 second, timeout: 30s)
   - `cd ../../config && mvn clean compile` (takes ~1 second, timeout: 30s)

**NEVER CANCEL**: Even though these commands are fast, always set appropriate timeouts (60+ seconds for build commands, 30+ seconds for simple operations).

### Running Tests
- `mvn test` - No unit tests exist in this repository. The command completes successfully but reports "No tests to run."

### Validation After Changes
Always run these validation steps after making code changes:

1. **Regenerate and test**: `mvn -q -DcleanGenerated=true compile exec:java "-Dexec.mainClass=com.company.design.DesignParser"`
2. **Verify generation output**: Check that `generated/` directory contains:
   - `sharedflows/*/pom.xml` and `sharedflows/*/sharedflowbundle/` directories
   - `proxies/*/pom.xml` and `proxies/*/apiproxy/` directories  
   - `config/pom.xml` and `config/entities/*.json` files
3. **Test at least one generated module builds**: `cd generated/sharedflows/common-logging && mvn clean compile`

## Local Deployment (Requires Apigee Credentials)

The deployment process requires a valid GCP service account with Apigee admin permissions.

### PowerShell Script (Windows/Linux/macOS)
From the `scripts/` directory:

```powershell
# Set environment variables or pass as parameters
$env:APIGEE_ORG = "your-apigee-org"
$env:APIGEE_ENV = "eval"  # or "prod", "test", etc.

# Full regeneration and deployment
pwsh ./deploy-local.ps1 -Regenerate -ServiceAccount /path/to/service-account.json

# Deploy existing generated modules without regeneration
pwsh ./deploy-local.ps1 -ServiceAccount /path/to/service-account.json
```

**Expected deployment failure without real credentials**: The script will fail at the deployment phase with Maven plugin resolution errors, but generation should complete successfully.

### Manual Deployment Commands
From repository root:

```bash
# 1. Generate bundles
mvn -q -DcleanGenerated=true compile exec:java "-Dexec.mainClass=com.company.design.DesignParser"

# 2. Deploy shared flows (in order)
cd generated/sharedflows/common-logging
mvn install -Dapigee.org=YOUR_ORG -Dapigee.env=eval -DserviceAccountFile=/path/to/sa.json

# 3. Deploy proxies
cd ../../proxies/sample-proxy  
mvn install -Dapigee.org=YOUR_ORG -Dapigee.env=eval -DserviceAccountFile=/path/to/sa.json

# 4. Deploy config (API Products)
cd ../../config
mvn install -Dapigee.org=YOUR_ORG -Dapigee.env=eval -DserviceAccountFile=/path/to/sa.json
```

## GitHub Actions Deployment

The workflow `.github/workflows/apigee-deploy.yml` automatically:
1. Triggers on changes to `designs/`, `src/`, or `pom.xml`
2. Generates bundles from YAML designs
3. Deploys in correct order: SharedFlows → Proxies → Config

**Required GitHub Secrets**:
- `APIGEE_ORG`: Your Apigee X organization name
- `APIGEE_ENV`: Target environment (eval, prod, etc.)
- `APIGEE_SA_KEY_JSON`: Complete JSON content of GCP service account key

## Repository Structure and Key Files

### Design Files (`designs/`)
- `sample.yaml`: Basic example with shared flow, proxy, and API product
- `proxy-with-sharedflow.yaml`: Complex example showing shared flow dependencies

### Source Code (`src/main/java/com/company/design/`)
- `DesignParser.java`: Main entry point, reads YAML files and orchestrates generation
- `SharedFlowGenerator.java`: Generates Apigee shared flow bundles
- `ProxyGenerator.java`: Generates Apigee proxy bundles  
- `ConfigGenerator.java`: Generates API product configuration

### Generated Output (`generated/` - ignored by git)
After running the generator:
- `sharedflows/*/`: Individual shared flow modules with Maven POMs
- `proxies/*/`: Individual proxy modules with Maven POMs
- `config/`: API Products configuration module with Maven POM

### Sample Output Structure
```
generated/
├── sharedflows/
│   ├── common-logging/
│   │   ├── pom.xml
│   │   └── sharedflowbundle/
│   │       ├── common-logging.xml
│   │       ├── config.json
│   │       └── policies/
└── proxies/
    ├── sample-proxy/
    │   ├── pom.xml
    │   └── apiproxy/
    │       ├── sample-proxy.xml
    │       ├── config.json
    │       ├── policies/
    │       ├── proxies/
    │       └── targets/
```

## Common Tasks and Troubleshooting

### Adding New Policies
To support new policy types, extend the `renderPolicy()` method in both `SharedFlowGenerator.java` and `ProxyGenerator.java`. Common policies already supported:
- VerifyAPIKey
- OAuthV2  
- JSONThreatProtection
- Quota
- JavaScript

### Debugging Generation Issues
1. **Check YAML syntax**: Ensure `designs/*.yaml` files have valid YAML structure
2. **Check required fields**: Each proxy/shared flow needs a `name` field
3. **Review console output**: Generator prints detailed processing information
4. **Examine generated files**: Check `generated/` directory structure matches expected layout

### Maven Plugin Resolution Issues
If you see "Plugin com.google.cloud.apigee:apigee-maven-plugin:1.0.0 or one of its dependencies could not be resolved":
- This is expected without proper Apigee X credentials configured
- The generation phase should complete successfully  
- Only deployment phase fails without real service account

### PowerShell Script Issues
- Ensure parameters are properly quoted: `"-Dapigee.org=$Org"`
- Run from `scripts/` directory, not repository root
- Check environment variables are set: `$env:APIGEE_ORG`, `$env:APIGEE_ENV`

## Architecture Overview

**Flow**: YAML Design → Java Generator → Apigee X Bundles → Maven Deployment

1. **Design Phase**: Edit `designs/*.yaml` files with shared flows, proxies, and API products
2. **Generation Phase**: Java code reads YAML, generates proper Apigee bundle structures
3. **Build Phase**: Each generated module has its own Maven POM for zipping and deployment
4. **Deploy Phase**: Maven plugins import/deploy to Apigee X in correct dependency order

The system uses modern Google Cloud Apigee Maven plugins instead of legacy Edge plugins for Apigee X compatibility.

## Known Limitations

- No validation for missing referenced policies between shared flows and proxies
- Single environment deployment (no multi-environment loop) 
- Limited policy types supported (extend generators as needed)
- No unit tests for YAML parsing logic
- PowerShell script only supports basic deployment scenarios

## When Adding New Features

1. **Always run the full validation sequence** after code changes
2. **Test generation with sample YAML files** before and after changes  
3. **Check generated Maven POMs** are valid and have correct plugin configurations
4. **Verify build process** for at least one generated module
5. **Update these instructions** if you add new commands or change the workflow