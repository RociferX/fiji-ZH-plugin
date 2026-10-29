$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
foreach ($required in @('Fiji/jars', 'Fiji/plugins', 'Fiji/jars/jna-5.14.0.jar', 'Fiji/jars/jna-platform-5.14.0.jar', 'Fiji/jars/asm-9.8.jar')) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing $required. See docs/INSTALL.md for the supported Fiji layout." }
}
New-Item -ItemType Directory -Path 'build/agent-classes','build/test-classes' -Force | Out-Null
$fijiJava = Get-ChildItem -LiteralPath 'Fiji' -Filter javac.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
$compiler = if ($fijiJava) { $fijiJava.FullName } else { (Get-Command javac.exe).Source }
if ($fijiJava) {
    $jdkHome = Split-Path -Parent $fijiJava.DirectoryName
} else {
    $javaProperties = & java -XshowSettings:properties -version 2>&1 | Out-String
    $jdkHome = [regex]::Match($javaProperties, 'java.home = (.+)').Groups[1].Value.Trim()
}
$jarTool = if ($fijiJava) { Join-Path $fijiJava.DirectoryName 'jar.exe' } else { Join-Path $jdkHome 'bin/jar.exe' }
$ij = Get-ChildItem -LiteralPath 'Fiji/jars' -Filter 'ij-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$ij) { throw 'Missing Fiji/jars/ij-*.jar. Install the supported Fiji distribution before building.' }
$sources = @('src/MenuDisplayAgent.java', 'src/ToolbarHints.java', 'src/DialogDisplayMapper.java', 'src/NativePaintBridge.java', 'src/DisplayTextPainter.java', 'src/SwingDisplayMapper.java')
$sources += 'src/PluginDisplaySupport.java'
if ($ij) { $sources += 'src/Fiji_Chinese_Display.java' }
& $compiler --release 11 -encoding UTF-8 -cp "$($ij.FullName);Fiji/jars/jna-5.14.0.jar;Fiji/jars/jna-platform-5.14.0.jar;Fiji/jars/asm-9.8.jar" -d build/agent-classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& $compiler --release 11 -encoding UTF-8 -cp build/agent-classes -d build/test-classes tests/MenuPeerDemo.java tests/MenuPeerSmokeTest.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
if ($ij) { Copy-Item -LiteralPath 'src/plugins.config' -Destination 'build/agent-classes/plugins.config' }
@('Manifest-Version: 1.0', 'Premain-Class: org.fiji.zh.MenuDisplayAgent', '') | Set-Content -LiteralPath 'build/MANIFEST.MF' -Encoding ascii
& $jarTool cfm build/fiji-zh-display-0.11.jar build/MANIFEST.MF -C build/agent-classes org
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
Write-Output 'Built build/fiji-zh-display-0.11.jar'
if ($ij) {
    & $jarTool cf build/fiji-zh-plugin.jar -C build/agent-classes Fiji_Chinese_Display.class -C build/agent-classes plugins.config
    if ($LASTEXITCODE -ne 0) { throw 'Plugin packaging failed' }
    try { Copy-Item -LiteralPath 'build/fiji-zh-plugin.jar' -Destination 'Fiji/plugins/Fiji_Chinese_Display.jar' -ErrorAction Stop }
    catch [System.IO.IOException] { Write-Output 'The running Fiji holds the existing language-switch plugin; its current commands remain compatible.' }
}
