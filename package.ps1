param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
$version = '0.12'
$name = "fiji-zh-plugin-$version-windows-x64"
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Path $dist -Force | Out-Null
$stage = Join-Path $dist ([guid]::NewGuid().ToString('N'))
$root = Join-Path $stage $name
New-Item -ItemType Directory -Path $root -Force | Out-Null
foreach ($file in @('README.md', 'CHANGELOG.md')) {
    Copy-Item -LiteralPath $file -Destination $root
}
Copy-Item -LiteralPath 'docs' -Destination $root -Recurse
$jar = Join-Path $dist 'Fiji_Chinese_Display.jar'
Copy-Item -LiteralPath 'build/fiji-zh-plugin.jar' -Destination $jar -Force
Copy-Item -LiteralPath $jar -Destination $root
$zip = Join-Path $dist "$name.zip"
Compress-Archive -LiteralPath $root -DestinationPath $zip -Force
foreach ($file in @($jar, $zip)) {
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $([IO.Path]::GetFileName($file))" | Set-Content -LiteralPath "$file.sha256" -Encoding ascii
    Write-Output "Package: $file"
    Write-Output "SHA-256: $hash"
}
