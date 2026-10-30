param(
    [ValidateSet('zh', 'en')][string]$Language = 'zh',
    [string[]]$FijiArguments = @()
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$launcher = Join-Path $PSScriptRoot 'Fiji/fiji-windows-x64.exe'
$configurator = Join-Path $PSScriptRoot 'Fiji/config/jaunch/jaunch-windows-x64.exe'
foreach ($required in @($launcher, $configurator)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing $required. See docs/INSTALL.md for the supported Fiji layout." }
}
$agent = Join-Path $PSScriptRoot 'build/fiji-zh-display-0.14.jar'
if (!(Test-Path -LiteralPath $agent)) { & (Join-Path $PSScriptRoot 'build.ps1') }

# Ask Fiji's own configurator for its runtime, classpath and main class.
# Use java.exe because a JVM embedded through JNI does not run -javaagent premain.
$protocol = @(& $configurator $launcher --no-splash --allow-multiple @FijiArguments)
if ($LASTEXITCODE -ne 0) { throw 'Fiji launcher configuration failed' }
$jvmIndex = [Array]::IndexOf($protocol, 'JVM')
if ($jvmIndex -lt 0) { throw 'Fiji configurator did not return a JVM launch' }
$count = [int]$protocol[$jvmIndex + 1]
$payload = $protocol[($jvmIndex + 2)..($jvmIndex + 1 + $count)]
$runtimeCount = [int]$payload[1]
$runtimeArgs = @($payload[2..($runtimeCount + 1)])
$main = $payload[$runtimeCount + 2].Replace('/', '.')
$mainArgs = @($payload[($runtimeCount + 3)..($payload.Length - 1)])
$jvmBin = Split-Path -Parent (Split-Path -Parent $payload[0])
$java = Join-Path $jvmBin 'java.exe'
$allArgs = @("-javaagent:$agent", "-Dfiji.zh.home=$PSScriptRoot", "-Dfiji.zh.language=$Language") + $runtimeArgs + @($main) + $mainArgs

# Java @argfiles preserve long classpaths and paths containing spaces.
$quoted = $allArgs | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' }
$argsFile = Join-Path $PSScriptRoot 'build/fiji-launch.args'
[IO.File]::WriteAllLines($argsFile, $quoted, [Text.UTF8Encoding]::new($false))
$process = Start-Process -FilePath $java -ArgumentList ('"@' + $argsFile + '"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'build/fiji-stdout.log') -RedirectStandardError (Join-Path $PSScriptRoot 'build/fiji-stderr.log') -PassThru
Write-Output "Fiji started (PID $($process.Id)), language: $Language"
