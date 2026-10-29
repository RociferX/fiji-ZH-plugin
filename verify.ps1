$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$java = (Get-ChildItem -LiteralPath 'Fiji/java' -Filter java.exe -Recurse | Select-Object -First 1).FullName
$compiler = Join-Path (Split-Path -Parent $java) 'javac.exe'
$ij = (Get-ChildItem -LiteralPath 'Fiji/jars' -Filter 'ij-*.jar' | Select-Object -First 1).FullName
& $compiler --release 11 -encoding UTF-8 -cp "build/agent-classes;$ij" -d build/test-classes tests/MenuPeerSmokeTest.java tests/DialogPeerSmokeTest.java tests/Fiji_Chinese_Smoke.java tests/Fiji_Tooltip_Smoke.java tests/Fiji_Dialog_Smoke.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& $java '-Dfiji.zh.demo=true' "-Dfiji.zh.home=$PSScriptRoot" '-javaagent:build/fiji-zh-display-0.12.jar' -cp 'build/agent-classes;build/test-classes;Fiji/jars/jna-5.14.0.jar;Fiji/jars/jna-platform-5.14.0.jar;Fiji/jars/asm-9.8.jar' org.fiji.zh.MenuPeerSmokeTest
if ($LASTEXITCODE -ne 0) { throw 'Native AWT menu test failed' }
& $java '-Dfiji.zh.demo=true' "-Dfiji.zh.home=$PSScriptRoot" '-javaagent:build/fiji-zh-display-0.12.jar' -cp 'build/agent-classes;build/test-classes;Fiji/jars/jna-5.14.0.jar;Fiji/jars/jna-platform-5.14.0.jar;Fiji/jars/asm-9.8.jar' org.fiji.zh.DialogPeerSmokeTest
if ($LASTEXITCODE -ne 0) { throw 'Native AWT dialog test failed' }
$testStarted = Get-Date
& (Join-Path $PSScriptRoot 'Start-Fiji.ps1') -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes", '-macro', "$PSScriptRoot\tests\verify.ijm")
$result = Join-Path $PSScriptRoot 'build/fiji-smoke-result.txt'
do {
    Start-Sleep -Milliseconds 500
    $complete = (Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTime -ge $testStarted
} while (!$complete -and ((Get-Date) - $testStarted).TotalSeconds -lt 60)
if (!$complete) { throw 'Fiji integration test timed out; inspect build/fiji-stderr.log' }
$report = Get-Content -LiteralPath $result -Encoding UTF8
$report
if ($report -match '^FAIL:') { throw 'Fiji integration test failed' }
$testStarted = Get-Date
& (Join-Path $PSScriptRoot 'Start-Fiji.ps1') -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes", '-macro', "$PSScriptRoot\tests\dialogs.ijm")
$result = Join-Path $PSScriptRoot 'build/dialog-smoke-result.txt'
do {
    Start-Sleep -Milliseconds 500
    $complete = (Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTime -ge $testStarted
} while (!$complete -and ((Get-Date) - $testStarted).TotalSeconds -lt 240)
if (!$complete) { throw 'Fiji parameter dialog test timed out; inspect build/fiji-stderr.log' }
$report = Get-Content -LiteralPath $result -Encoding UTF8
$report
if ($report -match '^FAIL:') { throw 'Fiji parameter dialog test failed' }
& (Join-Path $PSScriptRoot 'verify-expanded.ps1') -NativeInput
python tools/menu_coverage.py
if ($LASTEXITCODE -ne 0) { throw 'Menu dictionary coverage failed' }
& (Join-Path $PSScriptRoot 'verify-core.ps1')
