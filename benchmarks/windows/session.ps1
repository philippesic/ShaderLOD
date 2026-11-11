param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Session,
    [int]$Timeout=600
)
$ErrorActionPreference='Stop'
Set-Location $Repository
& "$Repository\tools\python\python.exe" -m shaderlod_host session-open --launch "$Repository\build\moddev\windows-launch.json" --profile "$Repository\run-benchmark" --display "$Repository\build\moddev\windows-launch.json" --ownership-root "$Repository\ownership" --session $Session --timeout $Timeout *> ($Session+'.launcher.log')
exit $LASTEXITCODE
