# Installs Jarvis's voice. Safe to re-run - it skips what's already done.
#
# Primary:  Kokoro  - the most natural offline voice available. Needs
#                     Python plus a ~340 MB model.
# Fallback: Piper   - lighter and Python-free, but audibly synthetic.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# --- Kokoro ------------------------------------------------------------
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    Write-Host "Python is not installed - Kokoro needs it. Install with:" -ForegroundColor Yellow
    Write-Host "  winget install --id Python.Python.3.12"
    Write-Host "Then re-run this script. Falling through to Piper for now."
} else {
    Write-Host "Installing Kokoro Python packages..."
    python -m pip install --quiet kokoro-onnx soundfile

    New-Item -ItemType Directory -Force tools/kokoro | Out-Null
    $base = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0"

    if (Test-Path "tools/kokoro/kokoro-v1.0.onnx") {
        Write-Host "Kokoro model already present."
    } else {
        Write-Host "Downloading Kokoro model (~310 MB)..."
        Invoke-WebRequest -Uri "$base/kokoro-v1.0.onnx" -OutFile "tools/kokoro/kokoro-v1.0.onnx"
    }
    if (-not (Test-Path "tools/kokoro/voices-v1.0.bin")) {
        Write-Host "Downloading Kokoro voices (~27 MB)..."
        Invoke-WebRequest -Uri "$base/voices-v1.0.bin" -OutFile "tools/kokoro/voices-v1.0.bin"
    }
    Write-Host "Kokoro ready." -ForegroundColor Green
}

# --- Piper (fallback) --------------------------------------------------
$piperDir = "tools/piper"
$voiceName = "en_GB-alan-medium"

if (Test-Path "$piperDir/piper.exe") {
    Write-Host "Piper fallback already installed."
} else {
    New-Item -ItemType Directory -Force tools | Out-Null
    Write-Host "Downloading Piper (fallback voice)..."
    Invoke-WebRequest -Uri "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip" -OutFile "tools/piper.zip"
    Expand-Archive "tools/piper.zip" -DestinationPath tools -Force
    Remove-Item "tools/piper.zip"
}
if (-not (Test-Path "$piperDir/$voiceName.onnx")) {
    $vb = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alan/medium"
    Invoke-WebRequest -Uri "$vb/$voiceName.onnx" -OutFile "$piperDir/$voiceName.onnx"
    Invoke-WebRequest -Uri "$vb/$voiceName.onnx.json" -OutFile "$piperDir/$voiceName.onnx.json"
}

Write-Host ""
Write-Host "Voice setup complete." -ForegroundColor Green
Write-Host "Pick a voice with 'kokoroVoiceName' in config/settings.json:"
Write-Host "  bm_george  - measured, formal   (default)"
Write-Host "  bm_lewis   - warmer, softer"
Write-Host "  bm_daniel  - brighter, younger"
Write-Host "  bm_fable   - storyteller"
