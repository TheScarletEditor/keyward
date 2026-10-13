# Build Keyward and install it over USB.
#
# Default is the `personal` build: release-optimized (fast, not debuggable), signed with the owner's personal key
# (Q36): a key only the owner holds, so no other build can replace Keyward on their devices. Asks for the key password
# (hidden); it goes to apksigner through an environment variable only this script's process can see, cleared right
# after. Never stored, never on the command line.
#
# Never a debuggable build on a real device (Q36): -Debug only installs on an emulator.
# Never install a `release` build on the owner's devices: a different key forces an uninstall, which wipes learned words.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\install.ps1                  (the one connected device)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Serial XYZ      (pick one when several are plugged in)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Serial XYZ,ABC  (several devices, one password)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -NewKey          (once per device, when moving to the personal
#                                                                            key: uninstalls first, which wipes its words)
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Debug -Serial emulator-5554   (a debug build, emulators only)
#   ... -NoBuild   (sign and install the last build without running Gradle, e.g. while an emulator runs)

param([string[]]$Serial, [switch]$Debug, [switch]$NewKey, [switch]$NoBuild)

$ErrorActionPreference = 'Stop'
# Through -File, "-Serial A,B" arrives as one string.
$Serial = @($Serial | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
Set-Location $PSScriptRoot
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$keystore = "$env:USERPROFILE\keyward-personal.keystore"
$alias = 'keywardpersonal'
$package = 'com.thescarletcoder.keys'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }

if (-not $Serial) {
    $serials = @(& $adb devices | Select-String '	device$' | ForEach-Object { ($_ -split '	')[0] })
    if ($serials.Count -eq 0) { Write-Host "No device connected. Plug it in, unlock it, allow USB debugging."; exit 1 }
    if ($serials.Count -gt 1) { Write-Host "More than one device connected. Run again with -Serial (one or more of: $($serials -join ', '))."; exit 1 }
    $Serial = @($serials[0])
}

# Every device must be connected before the build and the password prompt.
$connected = @(& $adb devices | Select-String '	device$' | ForEach-Object { ($_ -split '	')[0] })
$missing = @($Serial | Where-Object { $connected -notcontains $_ })
if ($missing.Count -gt 0) { Write-Host "Not connected: $($missing -join ', '). Plug in and unlock, then run again."; exit 1 }

if ($Debug) {
    $real = @($Serial | Where-Object { -not $_.StartsWith('emulator-') })
    if ($real.Count -gt 0) { Write-Host "Debug builds only go on emulators, never a real device ($($real -join ', '))."; exit 1 }
    Write-Host "Building Keyward (debug)..."
    cmd /c ".\gradlew.bat :keys:assembleDebug --console=plain -q"
    if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }
    $apk = ".\keys\build\outputs\apk\debug\keys-debug.apk"
} else {
    if (-not (Test-Path $keystore)) { Write-Host "Personal key not found at $keystore. Create it first (docs/KEYS-DECISIONS.md, Q36)."; exit 1 }
    if (-not $NoBuild) {
        Write-Host "Building Keyward (personal)..."
        cmd /c ".\gradlew.bat :keys:assemblePersonal --console=plain -q"
        if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }
    }
    New-Item -ItemType Directory -Force .\keys\build\personal-signed | Out-Null
    $apk = ".\keys\build\personal-signed\keys-personal.apk"
    $secure = Read-Host "Personal key password" -AsSecureString
    $env:KEYWARD_PERSONAL_PASS = [System.Net.NetworkCredential]::new('', $secure).Password
    try {
        & $apksigner sign --ks $keystore --ks-key-alias $alias --ks-pass env:KEYWARD_PERSONAL_PASS --out $apk ".\keys\build\outputs\apk\personal\keys-personal-unsigned.apk"
        $signed = $LASTEXITCODE -eq 0
    } finally {
        Remove-Item Env:KEYWARD_PERSONAL_PASS -ErrorAction SilentlyContinue
    }
    if (-not $signed) { Write-Host "Signing failed. Wrong password?"; exit 1 }
    Remove-Item "$apk.idsig" -ErrorAction SilentlyContinue
}

foreach ($s in $Serial) {
    if ($NewKey) {
        Write-Host "$s : removing the old Keyward (signed with another key). Its learned words are wiped."
        & $adb -s $s uninstall $package | Out-Null
    }
    Write-Host "$s : installing..."
    # adb reports install failures on stderr; read them instead of stopping on them.
    $ErrorActionPreference = 'Continue'
    $out = & $adb -s $s install -r $apk 2>&1 | Out-String
    $ErrorActionPreference = 'Stop'
    Write-Host $out.Trim()
    if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Write-Host "$s : the installed Keyward is signed with another key. Run again with -NewKey to replace it (wipes its words)."; exit 1
    }
    if ($out -notmatch 'Success') { Write-Host "$s : install failed."; exit 1 }
    & $adb -s $s shell ime enable "$package/.KeysService" | Out-Null
    & $adb -s $s shell ime set "$package/.KeysService" | Out-Null
}
Write-Host "Done. Keyward is installed."
