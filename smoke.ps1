# Build the smoke copy of Keyward, install it and open its settings, so /finalize can drive it over adb.
#
# Keys Smoke (com.thescarletcoder.keys.smoke) is release-optimized exactly like the public build (R8, not
# debuggable) but has its own app id and the debug key. It installs next to the regular Keys with its own learned words
# and settings, and can never see or change its data. It is never enabled as a keyboard by this script; on personal
# devices it is never enabled at all (typing tests happen on an emulator). Remove the smoke copy when done.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\smoke.ps1                  (the one connected device or emulator)
#   powershell -ExecutionPolicy Bypass -File .\smoke.ps1 -Serial XYZ      (pick one when several are connected)
#   powershell -ExecutionPolicy Bypass -File .\smoke.ps1 -Remove          (uninstall the smoke copy)

param([string]$Serial, [switch]$Remove)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }

# The only package this script ever installs or removes.
$app = 'com.thescarletcoder.keys.smoke'

if (-not $Serial) {
    $serials = @(& $adb devices | Select-String '	device$' | ForEach-Object { ($_ -split '	')[0] })
    if ($serials.Count -eq 0) { Write-Host "No device connected. Plug it in, unlock it, allow USB debugging, or start an emulator."; exit 1 }
    if ($serials.Count -gt 1) { Write-Host "More than one device connected. Run again with -Serial (one of: $($serials -join ', '))."; exit 1 }
    $Serial = $serials[0]
}

if ($Remove) {
    & $adb -s $Serial uninstall $app | Out-Null
    Write-Host "Keys Smoke removed from $Serial."
    exit 0
}

Write-Host "Building Keys Smoke..."
cmd /c ".\gradlew.bat :keys:assembleSmoke --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

Write-Host "Installing Keys Smoke on $Serial..."
& $adb -s $Serial install -r '.\keys\build\outputs\apk\smoke\keys-smoke.apk' | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed."; exit 1 }

& $adb -s $Serial shell monkey -p $app -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Keys Smoke ($app) is open on $Serial. Remove it with -Remove when done."
