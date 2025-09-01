# Apigee Design Driven Deployment

This repository demonstrates **single YAML design → generated Apigee X artifacts → automated deploy → API verification**.

## Flow

1. Developer edits `designs/*.yaml`.
2. Push triggers GitHub Action:
   - Runs Java generator (SnakeYAML) to build:
     - `generated/sharedflows/<sf>/sharedflowbundle/...`
     - `generated/proxies/<proxy>/apiproxy/...`
     - `generated/config/entities/*.json` (API Products)
     - Each with its own `pom.xml`
   - Deploy order:
     1. Shared Flows (import + deploy)
     2. Proxies (import + deploy)
     3. Config (API Products via apigee-config-maven-plugin)
   - **API Verification**: Tests deployed APIs to ensure they're actually working
     - Makes HTTP requests to deployed endpoints
     - Verifies responses and connectivity
     - Fails the build if APIs don't respond correctly

## API Verification Process

After deployment, the system automatically:
- Discovers all deployed API proxies from the generated configurations
- Extracts their base paths and endpoint URLs
- Makes HTTP requests to verify each API is responding
- Handles authentication errors appropriately (401/403 are expected for secured endpoints)
- Provides detailed logging and retry logic
- **Fails the entire deployment if any API is not responding**

This ensures that the deployment isn't just passing with placeholder configurations, but that real, functional APIs are actually deployed and accessible.

## Key Plugins

| Purpose              | Plugin Group / Artifact                             |
|----------------------|-----------------------------------------------------|
| Shared Flow / Proxy  | `com.google.cloud.apigee:apigee-maven-plugin`       |
| API Products config  | `com.apigee.edge.config:apigee-config-maven-plugin` |
| Zipping bundles      | `org.apache.maven.plugins:maven-antrun-plugin`      |

We **manually zip** the bundle with AntRun, then the Apigee plugin imports and deploys it. This avoids legacy Edge-specific plugin issues.

## Service Account

GitHub secrets for Apigee deployment are required. The workflow supports multiple configurations:

### Option 1: Individual Secrets (Traditional)
- `APIGEE_ORG`: Your Apigee organization name
- `APIGEE_ENV`: Target environment (e.g., eval, prod)  
- `APIGEE_SA_KEY_JSON`: GCP service account JSON (recommended)
- or `APIGEE_SA_KEY`: GCP service account JSON (legacy name)

### Option 2: Single JSON Secret (Consolidated)
- `apigee`: JSON object containing all configuration:
  ```json
  {
    "APIGEE_ORG": "your-org-name",
    "APIGEE_ENV": "eval", 
    "APIGEE_SA_KEY": "{\"type\":\"service_account\",...}"
  }
  ```

### Option 3: Raw Service Account Key
- `apigee`: Raw GCP service account JSON
- `APIGEE_ORG` and `APIGEE_ENV`: As separate secrets

**Service Account Requirements:**
- `roles/apigee.admin` (or appropriate combination)
- Access to the Apigee X org & environment

The workflow writes the service account key to `sa.json` (ignored by git).

## Example Design (excerpt)

```yaml
apigee:
  name: sample
  sharedFlows:
    - name: common-logging
      policies:
        - name: Verify-API-Key
          type: VerifyAPIKey
  proxies:
    - name: sample-proxy
      basePath: /v1/sample
      target: https://mocktarget.apigee.net
      dependsOn:
        sharedFlows: [ common-logging ]
      policies:
        - name: QuotaPolicy
          type: Quota
      flows:
        - name: default
          condition: true
          request:
            - policy: Verify-API-Key
            - policy: QuotaPolicy
  apiProducts:
    - name: SampleProduct
      proxies: [ sample-proxy ]
      environments: [ eval ]
```

## Local Testing

**Quick validation** (without Apigee deployment):
```bash
./scripts/test-local.sh
```

This validates:
- YAML design file syntax
- Generation process  
- Maven configuration correctness
- Generated artifact structure

**Full API verification** (requires deployed APIs):
```bash
# Set environment variables
export APIGEE_ORG=your-org
export APIGEE_ENV=eval

# Run API verification
./scripts/test-deployed-apis.sh
```

The API verification script will:
- Automatically discover deployed APIs
- Extract endpoint URLs from configurations  
- Test each API endpoint with retries
- Report success/failure status

## Local Usage

```powershell
# Generate
mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"

# Deploy one shared flow
cd generated/sharedflows/common-logging
mvn install -Dapigee.org=YOUR_ORG -Dapigee.env=eval -DserviceAccountFile=PATH\TO\sa.json
```

Or full scripted:

```powershell
pwsh scripts/deploy-local.ps1 -Org YOUR_ORG -Env eval -ServiceAccount C:\path\sa.json -Regenerate
```

## Adding More Policies

Extend `SharedFlowGenerator.renderPolicy()` and `ProxyGenerator.renderPolicy()` with new cases (SpikeArrest, AssignMessage, etc). If complexity grows, refactor into a `PolicyWriter` interface.

## Token Substitution (Optional)

If you want environment-driven tokens (e.g. `${APIGEE_ENV}` inside the YAML) add a simple post-load pass in `DesignParser` to replace placeholders with environment variables.

## Why Not the Old Edge Plugin?

The legacy `io.apigee.build-tools.enterprise4g` plugin has brittle behavior (NullPointerException with Apigee X SAs). This repo uses the modern Google Cloud Apigee plugin for a stable import/deploy lifecycle.

## Troubleshooting API Deployment

If the GitHub Actions workflow fails during the "Verify API Deployments" step:

1. **Check deployment logs**: Review the Maven deployment logs for any errors
2. **Verify credentials**: Ensure service account secret (`APIGEE_SA_KEY_JSON`, `APIGEE_SA_KEY`, or `apigee`), `APIGEE_ORG`, and `APIGEE_ENV` secrets are correctly configured
3. **Network connectivity**: The verification script needs to access `{org}-{env}.apigee.net`
4. **API accessibility**: Some APIs may require authentication (401/403 responses are considered successful)
5. **Custom domains**: If using custom domains, set the `APIGEE_HOST` environment variable

**Common Issues:**
- **"Failed to connect"**: Usually indicates the API wasn't deployed or network issues
- **"HTTP 404"**: API proxy exists but base path is incorrect
- **"HTTP 500"**: API deployed but has runtime errors (check Apigee console logs)

**Recent Fixes (2024-09):**
- ✅ **Java Version Compatibility**: Fixed Maven compiler version mismatch (Java 1.8 → Java 17)
- ✅ **Policy Generation**: Added support for VerifyAPIKey, MessageLogging, and Quota policy types
- ✅ **SharedFlow Dependencies**: Fixed proxy XML to use proper `<SharedFlows>` format
- ✅ **Robust API Verification**: Enhanced test script with local validation mode and better error handling

The verification ensures your APIs are **actually working**, not just deployed as placeholders.

## Future Enhancements

- Validation for missing referenced policies
- Multiple environments deploy loop
- More config entity types (developers, apps)
- Unit tests for YAML parsing
- Property-driven versioning
- ✅ **API endpoint verification after deployment** (COMPLETED)
- Advanced API testing (response validation, performance testing)
- Integration with API monitoring tools
- Automated rollback on verification failures

## License

Internal / sample use.