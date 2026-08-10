# Builds (if needed) and starts Jarvis.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$jar = "target/jarvis-1.0.0.jar"
if (-not (Test-Path $jar)) {
    Write-Host "Building..."
    mvn -q -DskipTests package
}
java -jar $jar @args
