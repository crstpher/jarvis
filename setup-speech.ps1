# Downloads the speech recognition models.
#
# Two are used, deliberately:
#   small (40 MB)  - wake word and the fast-path command phrases. Vosk
#                    only supports runtime grammars on the small models,
#                    which is what makes "open steam" snap instantly.
#   large (1.8 GB) - free-form conversation, where accuracy matters far
#                    more than speed. Optional; without it the small
#                    model handles everything and mishears long
#                    sentences badly.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force models | Out-Null

$small = "vosk-model-small-en-us-0.15"
if (Test-Path "models/$small") {
    Write-Host "Small model already present."
} else {
    Write-Host "Downloading $small (~40 MB)..."
    Invoke-WebRequest -Uri "https://alphacephei.com/vosk/models/$small.zip" -OutFile "models/small.zip"
    Expand-Archive "models/small.zip" -DestinationPath models -Force
    Remove-Item "models/small.zip"
}

$large = "vosk-model-en-us-0.22"
if (Test-Path "models/$large") {
    Write-Host "Large model already present."
} else {
    Write-Host "Downloading $large (~1.8 GB) - this takes a while..."
    Invoke-WebRequest -Uri "https://alphacephei.com/vosk/models/$large.zip" -OutFile "models/large.zip"
    Write-Host "Extracting..."
    Expand-Archive "models/large.zip" -DestinationPath models -Force
    Remove-Item "models/large.zip"
}

Write-Host ""
Write-Host "Speech models ready." -ForegroundColor Green
Write-Host "To skip the large model, set freeModelPath to \"\" in config/settings.json."
