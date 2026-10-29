param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
$version = '0.11'
$name = "fiji-zh-plugin-$version-windows-x64"
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Path $dist -Force | Out-Null
# Use a new staging directory so stale local build files never enter the ZIP.
$stage = Join-Path $dist ([guid]::NewGuid().ToString('N'))
$root = Join-Path $stage $name
New-Item -ItemType Directory -Path "$root/build", "$root/plugin" -Force | Out-Null
foreach ($file in @('README.md', 'CHANGELOG.md', 'Start-Fiji.ps1', 'Fiji-Chinese.cmd', 'Fiji-English.cmd', 'build.ps1', 'package.ps1')) {
    Copy-Item -LiteralPath $file -Destination $root
}
foreach ($directory in @('locales', 'src', 'docs', 'tests')) {
    Copy-Item -LiteralPath $directory -Destination $root -Recurse
}
Get-ChildItem -File -Filter 'verify*.ps1' | Copy-Item -Destination $root
New-Item -ItemType Directory -Path "$root/tools" -Force | Out-Null
foreach ($file in @('download_fiji.py', 'menu_coverage.py', 'MessageInventory.java', 'PluginStrings.java', 'UiStringInventory.java')) {
    Copy-Item -LiteralPath "tools/$file" -Destination "$root/tools"
}
Copy-Item -LiteralPath "build/fiji-zh-display-$version.jar" -Destination "$root/build"
Copy-Item -LiteralPath 'build/fiji-zh-plugin.jar' -Destination "$root/plugin"
$zip = Join-Path $dist "$name.zip"
Compress-Archive -LiteralPath $root -DestinationPath $zip -Force
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $name.zip" | Set-Content -LiteralPath "$zip.sha256" -Encoding ascii
Write-Output "Package: $zip"
Write-Output "SHA-256: $hash"
