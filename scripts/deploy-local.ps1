param(
  [string]$Org,
  [string]$Env,
  [string]$ServiceAccount = "$PSScriptRoot\..\sa.json",
  [switch]$Regenerate
)

if (-not $Org) { $Org = $env:APIGEE_ORG }
if (-not $Env) { $Env = $env:APIGEE_ENV }

if (-not $Org -or -not $Env) {
  Write-Host "Provide -Org and -Env or set APIGEE_ORG / APIGEE_ENV env vars." -ForegroundColor Yellow
  exit 1
}

if ($Regenerate) {
  Write-Host "Regenerating bundles..."
  Remove-Item -Recurse -Force ..\generated -ErrorAction SilentlyContinue
  mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"
  if ($LASTEXITCODE -ne 0) { Write-Host "Generation failed." -ForegroundColor Red; exit 1 }
}

function Deploy-Modules($path, $label) {
  if (Test-Path $path) {
    Get-ChildItem $path -Directory | ForEach-Object {
      Write-Host "`n=== Deploy $label $($_.Name) ===" -ForegroundColor Cyan
      Push-Location $_.FullName
      mvn -q install -Dapigee.org=$Org -Dapigee.env=$Env -DserviceAccountFile=$ServiceAccount
      if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED $label $($_.Name)" -ForegroundColor Red
        Pop-Location
        exit 1
      }
      Pop-Location
    }
  } else {
    Write-Host "No $label directory: $path"
  }
}

Deploy-Modules "..\generated\sharedflows" "SharedFlow"
Deploy-Modules "..\generated\proxies" "Proxy"

if (Test-Path ..\generated\config\pom.xml) {
  Write-Host "`n=== Deploy API Products ===" -ForegroundColor Cyan
  Push-Location ..\generated\config
  mvn -q install -Dapigee.org=$Org -Dapigee.env=$Env -DserviceAccountFile=$ServiceAccount
  Pop-Location
} else {
  Write-Host "No config module."
}

Write-Host "`nAll deployments finished." -ForegroundColor Green