# One-time setup for the AI layer: the neural voice (Piper) and the
# local language model (Ollama). Safe to re-run — it skips what's done.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# --- Piper neural voice ------------------------------------------------
$piperDir = "tools/piper"
$voiceName = "en_GB-alan-medium"

if (Test-Path "$piperDir/piper.exe") {
    Write-Host "Piper already installed."
} else {
    New-Item -ItemType Directory -Force tools | Out-Null
    $zip = "tools/piper.zip"
    Write-Host "Downloading Piper (neural text-to-speech)..."
    Invoke-WebRequest -Uri "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip" -OutFile $zip
    Write-Host "Extracting..."
    Expand-Archive $zip -DestinationPath tools -Force
    Remove-Item $zip
}

if (Test-Path "$piperDir/$voiceName.onnx") {
    Write-Host "Voice model already present."
} else {
    Write-Host "Downloading the $voiceName voice (~60 MB)..."
    $base = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alan/medium"
    Invoke-WebRequest -Uri "$base/$voiceName.onnx" -OutFile "$piperDir/$voiceName.onnx"
    Invoke-WebRequest -Uri "$base/$voiceName.onnx.json" -OutFile "$piperDir/$voiceName.onnx.json"
}

# --- Ollama local model ------------------------------------------------
$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) {
    Write-Host ""
    Write-Host "Ollama is not installed. Install it with:" -ForegroundColor Yellow
    Write-Host "  winget install --id Ollama.Ollama"
} else {
    $model = "qwen2.5:7b"
    if ((ollama list) -match [regex]::Escape($model)) {
        Write-Host "Model $model already downloaded."
    } else {
        Write-Host "Pulling $model (~4.7 GB, one time)..."
        ollama pull $model
    }
}

Write-Host ""
Write-Host "AI setup complete." -ForegroundColor Green
Write-Host "For Spotify control, run ./setup-spotify.ps1 next."
