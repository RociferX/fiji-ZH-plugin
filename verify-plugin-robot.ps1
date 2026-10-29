param([ValidateSet('config','weka','weka-editor','scheme','scheme-tip','export','stitch','coloc','coloc-report')][string[]]$Modes=@('config','weka','scheme','export','stitch','coloc','weka-editor','scheme-tip','coloc-report'))
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler=(Get-ChildItem Fiji/java -Filter javac.exe -Recurse|Select-Object -First 1).FullName
& $compiler -proc:none --release 11 -encoding UTF-8 -cp 'Fiji/jars/*;Fiji/plugins/*;build/agent-classes' -d build/test-classes tests/Fiji_Plugin_Robot.java
if($LASTEXITCODE -ne 0){throw 'Robot test compilation failed'}
foreach($mode in $Modes){
 $started=Get-Date
 & .\Start-Fiji.ps1 -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes",'-macro',"$PSScriptRoot\tests\plugin-robot.ijm",$mode)
 $file=Join-Path $PSScriptRoot "build/robot-$mode-result.txt"
 do{Start-Sleep -Milliseconds 500;$done=(Test-Path $file) -and (Get-Item $file).LastWriteTime -ge $started}while(!$done -and ((Get-Date)-$started).TotalSeconds -lt 70)
 if(!$done){throw "Robot $mode timed out"};$report=Get-Content $file -Encoding UTF8;$report
 if($report -match '^FAIL:'){throw "Robot $mode failed"}
}
