param([ValidateSet('bio','bio-help','coloc','coloc-names','stitch','weka','track')][string[]]$Modes=@('bio','coloc','stitch','weka','track'))
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler=(Get-ChildItem Fiji/java -Recurse -Filter javac.exe | Select-Object -First 1).FullName
& $compiler -proc:none --release 11 -encoding UTF-8 -cp 'Fiji/jars/*;Fiji/plugins/*;build/agent-classes' -d build/test-classes tests/Fiji_Plugin_Probe.java
if($LASTEXITCODE -ne 0){throw 'Plugin test compilation failed'}
foreach($mode in $Modes){
 $started=Get-Date
 & .\Start-Fiji.ps1 -FijiArguments @("--class-path=$PSScriptRoot\build\test-classes",'-macro',"$PSScriptRoot\tests\plugins.ijm",$mode)
 $report=Join-Path $PSScriptRoot "build/plugin-$mode-result.txt"
 do{Start-Sleep -Milliseconds 500;$done=(Test-Path $report) -and (Get-Item $report).LastWriteTime -ge $started}while(!$done -and ((Get-Date)-$started).TotalSeconds -lt 50)
 if(!$done){throw "Plugin $mode timed out"}
 $result=Get-Content $report -Encoding UTF8;$result
 if($result -match '^FAIL:'){throw "Plugin $mode failed"}
}
