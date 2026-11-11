param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Output,
    [Parameter(Mandatory=$true)][string]$Spec
)
$ErrorActionPreference='Stop'
Set-Location $Repository
& "$Repository\tools\python\python.exe" -m shaderlod_host qualify --spec $Spec --launch "$Repository\build\moddev\windows-launch.json" --profile "$Repository\run-benchmark" --display "$Repository\build\moddev\windows-launch.json" --ownership-root "$Repository\ownership" --output-root $Output *> ($Output+'.launcher.log')
exit $LASTEXITCODE
