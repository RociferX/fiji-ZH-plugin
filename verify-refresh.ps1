param([ValidatePattern('^[a-zA-Z0-9-]+$')][string]$Label = 'current')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler = (Get-ChildItem Fiji/java -Recurse -Filter javac.exe | Select-Object -First 1).FullName
& $compiler -proc:none --release 11 -encoding UTF-8 -cp 'Fiji/jars/*;Fiji/plugins/*' -d build/test-classes tests/Fiji_Refresh_Benchmark.java
if ($LASTEXITCODE -ne 0) { throw 'Refresh benchmark compilation failed' }
$settings = Join-Path $PSScriptRoot 'Fiji/plugins/Fiji_Chinese_Display/settings.properties'
$previous = if (Test-Path -LiteralPath $settings) { [IO.File]::ReadAllBytes($settings) } else { $null }
try {
    New-Item -ItemType Directory -Path (Split-Path -Parent $settings) -Force | Out-Null
    'language=en' | Set-Content -LiteralPath $settings -Encoding ascii
    $started = Get-Date
    $process = Start-Process -FilePath "$PSScriptRoot/Fiji/fiji-windows-x64.exe" -ArgumentList @('--no-splash', '--allow-multiple', ('"--class-path={0}/build/test-classes"' -f $PSScriptRoot), '-macro', ('"{0}/tests/refresh-benchmark.ijm"' -f $PSScriptRoot), ('"{0}|{1}"' -f $PSScriptRoot, $Label)) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput "$PSScriptRoot/build/refresh-$Label-stdout.log" -RedirectStandardError "$PSScriptRoot/build/refresh-$Label-stderr.log" -PassThru
    $result = Join-Path $PSScriptRoot "build/refresh-$Label-result.txt"
    do {
        Start-Sleep -Milliseconds 500
        $done = (Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTime -ge $started
    } while (!$done -and ((Get-Date) - $started).TotalSeconds -lt 90)
    if (!$done) { throw "Refresh benchmark timed out (PID $($process.Id)); inspect build/refresh-$Label-stderr.log" }
    $report = Get-Content -LiteralPath $result -Encoding UTF8
    $report
    if ($report -match '^FAIL:') { throw 'Refresh benchmark failed' }
} finally {
    if ($null -ne $previous) { [IO.File]::WriteAllBytes($settings, [byte[]]$previous) }
    elseif (Test-Path -LiteralPath $settings) { Remove-Item -LiteralPath $settings }
}
