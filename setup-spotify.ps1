# Connects Jarvis to your Spotify account.
#
# Spotify requires each app to have its own Client ID, and only you can
# create one for your account — it takes about a minute:
#
#   1. Go to https://developer.spotify.com/dashboard and log in
#   2. Click "Create app"
#      - App name:        Jarvis
#      - Redirect URI:    http://127.0.0.1:8888/callback     <-- must match exactly
#      - APIs used:       tick "Web API"
#   3. Open the app's Settings and copy the Client ID
#   4. Run this script and paste it in when asked
#
# The Client ID is not a secret (it's safe in a config file). Jarvis uses
# the PKCE flow, so there is no client secret to handle at all.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$settingsPath = "config/settings.json"
$settings = Get-Content $settingsPath -Raw | ConvertFrom-Json

if ($settings.spotifyClientId) {
    Write-Host "Client ID already configured ($($settings.spotifyClientId.Substring(0,6))...)."
    $again = Read-Host "Enter a different one, or press Enter to keep it"
    if ($again) { $settings.spotifyClientId = $again.Trim() }
} else {
    Write-Host "Paste your Spotify Client ID (see the instructions at the top of this script)."
    $id = Read-Host "Client ID"
    if (-not $id) { Write-Host "Nothing entered - aborting." -ForegroundColor Yellow; exit 1 }
    $settings.spotifyClientId = $id.Trim()
}

$settings | ConvertTo-Json -Depth 10 | Set-Content $settingsPath -Encoding utf8
Write-Host "Saved to $settingsPath"
Write-Host ""
Write-Host "Opening your browser to authorise Jarvis..."

if (-not (Test-Path "target/jarvis-1.0.0.jar")) {
    Write-Host "Building first..."
    mvn -q -DskipTests package
}
java -cp "target/jarvis-1.0.0.jar" jarvis.Main --spotify-login
