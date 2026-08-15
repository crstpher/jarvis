# One-time setup for the local language model that powers conversation.
# The voice is installed separately by ./setup-voice.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$model = "qwen2.5:7b"

$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) {
    Write-Host "Ollama is not installed. Install it with:" -ForegroundColor Yellow
    Write-Host "  winget install --id Ollama.Ollama"
    Write-Host "Then re-run this script."
    exit 1
}

if ((ollama list) -match [regex]::Escape($model)) {
    Write-Host "Model $model already downloaded."
} else {
    Write-Host "Pulling $model (~4.7 GB, one time)..."
    ollama pull $model
}

Write-Host ""
Write-Host "AI setup complete." -ForegroundColor Green
Write-Host "Next: ./setup-voice.ps1  (voice)  and  ./setup-spotify.ps1  (music)"
