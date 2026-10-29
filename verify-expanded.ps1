param([switch]$NativeInput, [switch]$Probe)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$java = (Get-ChildItem -LiteralPath 'Fiji/java' -Filter java.exe -Recurse | Select-Object -First 1).FullName
$compiler = Join-Path (Split-Path -Parent $java) 'javac.exe'
& $compiler --release 11 -encoding UTF-8 -cp 'build/agent-classes;Fiji/jars/ij-1.54p.jar' -d build/test-classes tests/Fiji_Expanded_Smoke.java
if ($LASTEXITCODE -ne 0) { throw 'Expanded test compilation failed' }
$macro = if ($Probe) { 'expanded-probe.ijm' } elseif ($NativeInput) { 'expanded-native.ijm' } else { 'expanded.ijm' }
$testStarted = Get-Date
& (Join-Path $PSScriptRoot 'Start-Fiji.ps1') -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes", '-macro', "$PSScriptRoot\tests\$macro")
$resultName = if ($Probe) { 'build/expanded-probe-result.txt' } else { 'build/expanded-smoke-result.txt' }
$result = Join-Path $PSScriptRoot $resultName
do {
    Start-Sleep -Milliseconds 500
    $complete = (Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTime -ge $testStarted
} while (!$complete -and ((Get-Date) - $testStarted).TotalSeconds -lt 240)
if (!$complete) { throw 'Expanded Fiji test timed out; inspect build/fiji-stderr.log' }
$report = Get-Content -LiteralPath $result -Encoding UTF8
$report
if ($report -match '^FAIL:') { throw 'Expanded Fiji test failed' }
