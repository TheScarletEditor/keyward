# Build the release APK and sign it with The Scarlet Coder's app key.
# Asks for the key password (hidden). It goes to apksigner through an environment variable that only
# this script's process can see, and is cleared right after. Never stored, never on the command line.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\release.ps1
#
# Output: dist\scarlet-keys-<version>.apk and its .sha256, ready for a GitHub release.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$keystore = "$env:USERPROFILE\scarlet-apps.keystore"
$alias = 'scarletapps'
if (-not (Test-Path $keystore)) { Write-Host "Signing key not found at $keystore."; exit 1 }

$version = [regex]::Match((Get-Content .\keys\build.gradle.kts -Raw), 'versionName = "([^"]+)"').Groups[1].Value
Write-Host "Building Scarlet Keys $version (release)..."
cmd /c ".\gradlew.bat :keys:assembleRelease --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

New-Item -ItemType Directory -Force dist | Out-Null
$out = ".\dist\scarlet-keys-$version.apk"
$secure = Read-Host "Key password" -AsSecureString
$env:SCARLET_KS_PASS = [System.Net.NetworkCredential]::new('', $secure).Password
try {
    & $apksigner sign --ks $keystore --ks-key-alias $alias --ks-pass env:SCARLET_KS_PASS --out $out ".\keys\build\outputs\apk\release\keys-release-unsigned.apk"
    $signed = $LASTEXITCODE -eq 0
} finally {
    Remove-Item Env:SCARLET_KS_PASS -ErrorAction SilentlyContinue
}
if (-not $signed) { Write-Host "Signing failed. Wrong password?"; exit 1 }
Remove-Item "$out.idsig" -ErrorAction SilentlyContinue

& $apksigner verify --print-certs $out | Select-String 'SHA-256'
if ($LASTEXITCODE -ne 0) { Write-Host "Signature check failed."; exit 1 }
$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
"$hash  scarlet-keys-$version.apk" | Set-Content -Encoding ascii "$out.sha256"
Write-Host "Done: $out"
Write-Host "SHA-256: $hash"
