param([ValidateSet('config','weka','weka-editor','scheme','scheme-tip','export','stitch','coloc','coloc-report')][string[]]$Modes=@('config','weka','scheme','export','stitch','coloc','weka-editor','scheme-tip','coloc-report'),[switch]$Runtime)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler=(Get-ChildItem Fiji/java -Filter javac.exe -Recurse|Select-Object -First 1).FullName
& $compiler -proc:none --release 11 -encoding UTF-8 -cp 'Fiji/jars/*;Fiji/plugins/*;build/agent-classes' -d build/test-classes tests/Fiji_Plugin_Robot.java tests/Fiji_Runtime_Smoke.java tests/Fiji_Tooltip_Smoke.java
if($LASTEXITCODE -ne 0){throw 'Robot test compilation failed'}
$settings = Join-Path $PSScriptRoot 'Fiji/plugins/Fiji_Chinese_Display/settings.properties'
$previous = if (Test-Path -LiteralPath $settings) { [IO.File]::ReadAllBytes($settings) } else { $null }
try {
    foreach($mode in $Modes){
     $started=Get-Date
     if($Runtime){
      Start-Process -FilePath "$PSScriptRoot/Fiji/fiji-windows-x64.exe" -ArgumentList @('--no-splash','--allow-multiple',('"--class-path={0}/build/test-classes"' -f $PSScriptRoot),'-macro',('"{0}/tests/runtime-plugin.ijm"' -f $PSScriptRoot),$mode) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
     }else{
      & .\Start-Fiji.ps1 -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes",'-macro',"$PSScriptRoot\tests\plugin-robot.ijm",$mode)
     }
     $file=Join-Path $PSScriptRoot "build/robot-$mode-result.txt"
     do{Start-Sleep -Milliseconds 500;$done=(Test-Path $file) -and (Get-Item $file).LastWriteTime -ge $started}while(!$done -and ((Get-Date)-$started).TotalSeconds -lt 70)
     if(!$done){throw "Robot $mode timed out"};$report=Get-Content $file -Encoding UTF8;$report
     if($report -match '^FAIL:'){throw "Robot $mode failed"}
    }
} finally {
    if ($null -ne $previous) { [IO.File]::WriteAllBytes($settings, [byte[]]$previous) }
    elseif (Test-Path -LiteralPath $settings) { Remove-Item -LiteralPath $settings }
}
