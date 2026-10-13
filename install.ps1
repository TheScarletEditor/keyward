# Build Keyward and install it over USB.
#
# Default is the `personal` build: release-optimized (fast, not debuggable) but signed with this laptop's debug key,
# so it installs over earlier debug installs and keeps learned words. Never install a `release` build on the owner's
# devices: a different key forces an uninstall, which wipes learned words.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\install.ps1              (the one connected device)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Serial XYZ  (pick one when several are plugged in)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Debug       (a debug build instead)

param([string]$Serial, [switch]$Debug)

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

$type = if ($Debug) { 'debug' } else { 'personal' }
$task = if ($Debug) { 'assembleDebug' } else { 'assemblePersonal' }
Write-Host "Building Keyward ($type)..."
cmd /c ".\gradlew.bat :keys:$task --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

Write-Host "Installing..."
& $adb -s $Serial install -r ".\keys\build\outputs\apk\$type\keys-$type.apk"
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed."; exit 1 }
Write-Host "Done. Keyward ($type) is installed."
