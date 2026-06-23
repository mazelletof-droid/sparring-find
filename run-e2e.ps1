<#
run-e2e.ps1
Simple helper to run E2E locally.
Usage:
  .\run-e2e.ps1            # run with browser install
  .\run-e2e.ps1 -SkipBrowserInstall  # run without installing browsers
#>
param(
    [switch]$SkipBrowserInstall
)

Write-Host "Checking Docker availability..."
try {
    docker info | Out-Null
} catch {
    Write-Error "Docker not available or not running. Please start Docker Desktop or the Docker daemon."
    exit 1
}

if (-not $SkipBrowserInstall) {
    Write-Host "Installing Playwright browsers (this requires network access)..."
    npx -y playwright install --with-deps
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Playwright browser installation failed."
        exit 1
    }
} else {
    Write-Host "Skipping Playwright browser installation as requested."
}

Write-Host "Running mvn verify (unit + integration + E2E)..."
mvn -B -U verify
$exit = $LASTEXITCODE
if ($exit -ne 0) {
    Write-Error "mvn verify failed with exit code $exit"
    exit $exit
}
Write-Host "E2E run completed successfully."