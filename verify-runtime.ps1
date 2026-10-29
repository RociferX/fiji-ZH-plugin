$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler = (Get-ChildItem Fiji/java -Recurse -Filter javac.exe | Select-Object -First 1).FullName
& $compiler -proc:none --release 11 -encoding UTF-8 -cp 'Fiji/jars/*;Fiji/plugins/*' -d build/test-classes tests/Fiji_Runtime_Smoke.java
if ($LASTEXITCODE -ne 0) { throw 'Runtime test compilation failed' }
$started = Get-Date
$process = Start-Process -FilePath "$PSScriptRoot/Fiji/fiji-windows-x64.exe" -ArgumentList @('--no-splash', '--allow-multiple', ('"--class-path={0}/build/test-classes"' -f $PSScriptRoot), '-macro', ('"{0}/tests/runtime.ijm"' -f $PSScriptRoot), ('"{0}"' -f $PSScriptRoot)) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput "$PSScriptRoot/build/runtime-stdout.log" -RedirectStandardError "$PSScriptRoot/build/runtime-stderr.log" -PassThru
$result = Join-Path $PSScriptRoot 'build/runtime-result.txt'
do {
    Start-Sleep -Milliseconds 500
    $done = (Test-Path $result) -and (Get-Item $result).LastWriteTime -ge $started
} while (!$done -and ((Get-Date) - $started).TotalSeconds -lt 90)
if (!$done) { throw "Runtime test timed out (launcher PID $($process.Id)); inspect build/runtime-stderr.log" }
$report = Get-Content -LiteralPath $result -Encoding UTF8
$report
if ($report -match '^FAIL:') { throw 'Runtime activation test failed' }
