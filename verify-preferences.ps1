$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$java = (Get-ChildItem Fiji/java -Recurse -Filter java.exe | Select-Object -First 1).FullName
$compiler = Join-Path (Split-Path -Parent $java) 'javac.exe'
$jarTool = Join-Path (Split-Path -Parent $java) 'jar.exe'
& $compiler --release 11 -encoding UTF-8 -d build/test-classes tests/Fiji_Preference_Smoke.java
if ($LASTEXITCODE -ne 0) { throw 'Preference test compilation failed' }
@('Manifest-Version: 1.0', 'Agent-Class: Fiji_Preference_Smoke', '') | Set-Content build/PREFERENCE-TEST.MF -Encoding ascii
& $jarTool cfm build/preference-test.jar build/PREFERENCE-TEST.MF -C build/test-classes Fiji_Preference_Smoke.class
if ($LASTEXITCODE -ne 0) { throw 'Preference test packaging failed' }
$settings = Join-Path $PSScriptRoot 'Fiji/plugins/Fiji_Chinese_Display/settings.properties'
$previous = if (Test-Path -LiteralPath $settings) { [IO.File]::ReadAllBytes($settings) } else { $null }
try {
    if (Test-Path -LiteralPath $settings) { Remove-Item -LiteralPath $settings }
    foreach ($phase in @('first', 'chinese', 'english')) {
        $started = Get-Date
        $process = Start-Process -FilePath "$PSScriptRoot/Fiji/fiji-windows-x64.exe" -ArgumentList @('--no-splash', '--allow-multiple') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
        & $java '--add-modules=jdk.attach' -cp build/fiji-zh-plugin.jar org.fiji.zh.AttachHelper $process.Id "$PSScriptRoot/build/preference-test.jar" "$phase|$PSScriptRoot"
        if ($LASTEXITCODE -ne 0) { throw "Cannot attach preference observer for $phase" }
        $file = Join-Path $PSScriptRoot "build/preference-$phase-result.txt"
        do {
            Start-Sleep -Milliseconds 500
            $done = (Test-Path -LiteralPath $file) -and (Get-Item -LiteralPath $file).LastWriteTime -ge $started
        } while (!$done -and ((Get-Date) - $started).TotalSeconds -lt 60)
        if (!$done) { throw "Preference restart test timed out: $phase (PID $($process.Id))" }
        $report = Get-Content -LiteralPath $file -Encoding UTF8
        $report
        if ($report -match '^FAIL:') { throw "Preference restart test failed: $phase" }
    }
} finally {
    if ($null -ne $previous) { [IO.File]::WriteAllBytes($settings, [byte[]]$previous) }
    elseif (Test-Path -LiteralPath $settings) { Remove-Item -LiteralPath $settings }
}
