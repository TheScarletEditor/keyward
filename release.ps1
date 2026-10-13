# Build the release APK and sign it with The Scarlet Coder's app key.
# apksigner itself asks for the key password (hidden): it never passes through this script, an environment variable,
# a file or a command line. The build checks every dependency's checksum and uses no build cache.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\release.ps1
#
# Output: dist\keyward-<version>.apk and its .sha256, ready for a GitHub release.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$keystore = "$env:USERPROFILE\scarlet-apps.keystore"
$alias = 'scarletapps'
if (-not (Test-Path $keystore)) { Write-Host "Signing key not found at $keystore."; exit 1 }

$version = [regex]::Match((Get-Content .\keys\build.gradle.kts -Raw), 'versionName = "([^"]+)"').Groups[1].Value
Write-Host "Building Keyward $version (release)..."
cmd /c ".\gradlew.bat :keys:assembleRelease --dependency-verification=strict --no-build-cache --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

New-Item -ItemType Directory -Force dist | Out-Null
$out = ".\dist\keyward-$version.apk"
Write-Host "apksigner asks for the key password next."
& $apksigner sign --ks $keystore --ks-key-alias $alias --out $out ".\keys\build\outputs\apk\release\keys-release-unsigned.apk"
if ($LASTEXITCODE -ne 0) { Write-Host "Signing failed. Wrong password?"; exit 1 }
Remove-Item "$out.idsig" -ErrorAction SilentlyContinue

& $apksigner verify --print-certs $out | Select-String 'SHA-256'
if ($LASTEXITCODE -ne 0) { Write-Host "Signature check failed."; exit 1 }
$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
"$hash  keyward-$version.apk" | Set-Content -Encoding ascii "$out.sha256"
Write-Host "Done: $out"
Write-Host "SHA-256: $hash"
