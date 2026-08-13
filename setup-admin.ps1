# One-time (re-run after adding new "close" commands): registers an
# elevated Task Scheduler task for every close command in
# config/commands.json, so Jarvis can close games that run as
# administrator (e.g. Marvel Rivals) without a UAC prompt each time.
#
# Each task is named "Jarvis kill <command-name>", has NO schedule
# (on-demand only), and does exactly one thing: taskkill the game's
# process name.

$ErrorActionPreference = "Stop"
$self = $MyInvocation.MyCommand.Path

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Requesting administrator approval (UAC prompt)..."
    Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$self`""
    exit
}

Set-Location (Split-Path $self)
$json = Get-Content "config/commands.json" -Raw | ConvertFrom-Json

$made = 0
foreach ($c in $json.commands) {
    if ($c.action.type -eq "close") {
        $name = "Jarvis kill $($c.name)"
        $action = New-ScheduledTaskAction -Execute "taskkill" -Argument "/F /IM $($c.action.target)"
        $taskPrincipal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -RunLevel Highest
        Register-ScheduledTask -TaskName $name -Action $action -Principal $taskPrincipal -Force | Out-Null
        Write-Host "  registered: $name  ->  taskkill /F /IM $($c.action.target)"
        $made++
    }
}
Write-Host ""
Write-Host "Done: $made elevated kill tasks registered."
Start-Sleep -Seconds 4
