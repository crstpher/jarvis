# Stops any running Jarvis instance (and its text-to-speech helper).
# Handy when Jarvis was started in the background, or a run.ps1 window
# was closed without saying "goodbye".
$ErrorActionPreference = "SilentlyContinue"

$killed = 0

# The assistant itself: java running the Jarvis jar.
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -like "*jarvis-1.0.0.jar*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
        Write-Host "Stopped Jarvis (pid $($_.ProcessId))"
        $killed++
    }

# Its speech helper, identified by the SAPI script we launch it with.
Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" |
    Where-Object { $_.CommandLine -like "*SpeechSynthesizer*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
        Write-Host "Stopped speech helper (pid $($_.ProcessId))"
        $killed++
    }

if ($killed -eq 0) { Write-Host "Jarvis wasn't running." }
