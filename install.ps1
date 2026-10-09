# Build the debug APK and install it over USB.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\install.ps1              (the one connected device)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Serial XYZ  (pick one when several are plugged in)

param([string]$Serial)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }

if (-not $Serial) {
    $serials = @(& $adb devices | Select-String '	device$' | ForEach-Object { ($_ -split '	')[0] })
    if ($serials.Count -eq 0) { Write-Host "No device connected. Plug it in, unlock it, allow USB debugging."; exit 1 }
    if ($serials.Count -gt 1) { Write-Host "More than one device connected. Run again with -Serial (one of: $($serials -join ', '))."; exit 1 }
    $Serial = $serials[0]
}

Write-Host "Building Scarlet Keys..."
cmd /c ".\gradlew.bat :keys:assembleDebug --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

Write-Host "Installing..."
& $adb -s $Serial install -r ".${app}\build\outputs\apk\debug${app}-debug.apk"
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed."; exit 1 }
Write-Host "Done. Scarlet Keys is installed."
