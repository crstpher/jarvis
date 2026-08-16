# Lets Jarvis answer general-knowledge questions using Google's Gemini.
#
# The local model is good at deciding what to do but weak on facts. This
# gives it a lookup tool. Only the question text is sent to Google - your
# commands, games and music stay entirely on this machine.
#
# Getting a key takes about a minute and needs no credit card:
#   1. Go to https://aistudio.google.com/apikey and sign in
#   2. Click "Create API key"
#   3. Copy it and paste it below
#
# Free tier as of 2026: gemini-2.5-flash allows 10 requests/minute and
# 250/day, which is far more than a personal assistant will use.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$secretsPath = "config/secrets.json"
$secrets = if (Test-Path $secretsPath) {
    Get-Content $secretsPath -Raw | ConvertFrom-Json
} else {
    [PSCustomObject]@{}
}

Write-Host "Paste your Google AI Studio API key (input is hidden)."
$secure = Read-Host "API key" -AsSecureString
$key = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

if (-not $key) {
    Write-Host "Nothing entered - aborting." -ForegroundColor Yellow
    exit 1
}

$secrets | Add-Member -NotePropertyName geminiApiKey -NotePropertyValue $key.Trim() -Force
New-Item -ItemType Directory -Force config | Out-Null
$secrets | ConvertTo-Json -Depth 10 | Set-Content $secretsPath -Encoding utf8

Write-Host "Saved to $secretsPath (gitignored - it will not be committed)." -ForegroundColor Green
Write-Host ""
Write-Host "Testing the key..."

if (-not (Test-Path "target/jarvis-1.0.0.jar")) {
    mvn -q -DskipTests package
}
java -cp "target/jarvis-1.0.0.jar" jarvis.Main --ask "What is the capital of Ireland?"
