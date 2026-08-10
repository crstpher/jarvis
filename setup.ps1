# One-time setup: downloads the offline speech model (~40 MB).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$model = "vosk-model-small-en-us-0.15"
if (Test-Path "models/$model") {
    Write-Host "Speech model already present at models/$model - nothing to do."
    exit 0
}

New-Item -ItemType Directory -Force models | Out-Null
$zip = "models/$model.zip"
Write-Host "Downloading $model (~40 MB)..."
Invoke-WebRequest -Uri "https://alphacephei.com/vosk/models/$model.zip" -OutFile $zip
Write-Host "Extracting..."
Expand-Archive $zip -DestinationPath models -Force
Remove-Item $zip
Write-Host "Done. Model installed at models/$model"
