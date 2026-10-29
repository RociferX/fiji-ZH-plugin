param([ValidateSet('all','help','native-help')][string]$Phase='all')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler = (Get-ChildItem -LiteralPath 'Fiji/java' -Filter javac.exe -Recurse | Select-Object -First 1).FullName
& $compiler --release 11 -encoding UTF-8 -cp 'build/agent-classes;Fiji/jars/ij-1.54p.jar' -d build/test-classes tests/Fiji_Message_Smoke.java
if ($LASTEXITCODE -ne 0) { throw 'Message test compilation failed' }
$started = Get-Date
& (Join-Path $PSScriptRoot 'Start-Fiji.ps1') -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes", '-macro', "$PSScriptRoot\tests\messages.ijm", $Phase)
$reportPath = Join-Path $PSScriptRoot "build/messages-$Phase-result.txt"
do {
    Start-Sleep -Milliseconds 500
    $complete = (Test-Path -LiteralPath $reportPath) -and (Get-Item -LiteralPath $reportPath).LastWriteTime -ge $started
} while (!$complete -and ((Get-Date) - $started).TotalSeconds -lt 120)
if (!$complete) { throw 'Message test timed out; inspect build/fiji-stderr.log' }
$report = Get-Content -LiteralPath $reportPath -Encoding UTF8
$report
if ($report -match '^FAIL:') { throw 'Message test failed' }
