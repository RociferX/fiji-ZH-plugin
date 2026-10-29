param([string[]]$Modes = @('status','workflows','probe','settings','frames','help'))
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler = (Get-ChildItem -LiteralPath 'Fiji/java' -Filter javac.exe -Recurse | Select-Object -First 1).FullName
& $compiler --release 11 -encoding UTF-8 -cp 'build/agent-classes;Fiji/jars/ij-1.54p.jar' -d build/test-classes tests/Fiji_Core_Smoke.java tests/Fiji_Expanded_Smoke.java
if ($LASTEXITCODE -ne 0) { throw 'Core test compilation failed' }
foreach ($mode in $Modes) {
    if ($mode -notin @('status','workflows','probe','settings','frames','help')) { throw "Unknown core test mode: $mode" }
    $started = Get-Date
    & (Join-Path $PSScriptRoot 'Start-Fiji.ps1') -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes", '-macro', "$PSScriptRoot\tests\core-$mode.ijm")
    $reportPath = Join-Path $PSScriptRoot "build/core-$mode-result.txt"
    do {
        Start-Sleep -Milliseconds 500
        $complete = (Test-Path -LiteralPath $reportPath) -and (Get-Item -LiteralPath $reportPath).LastWriteTime -ge $started
    } while (!$complete -and ((Get-Date) - $started).TotalSeconds -lt 240)
    if (!$complete) { throw "Core $mode test timed out; inspect build/fiji-stderr.log" }
    $report = Get-Content -LiteralPath $reportPath -Encoding UTF8
    $report
    if ($report -match '^FAIL:') { throw "Core $mode test failed" }
}
