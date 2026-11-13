param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Session,
    [int]$Timeout=600,
    [switch]$DistantHorizons
)
$ErrorActionPreference='Stop'
Set-Location $Repository
$launch="$Repository\build\moddev\windows-launch.json"
$slot="$Repository\run-benchmark"
if ($DistantHorizons) {
    $launch="$Repository\build\moddev\windows-launch-dh.json"
    $slot="$Repository\run-benchmark-dh"
}
& "$Repository\tools\python\python.exe" -m shaderlod_host session-open --launch $launch --profile $slot --display $launch --ownership-root "$Repository\ownership" --session $Session --timeout $Timeout *> ($Session+'.launcher.log')
exit $LASTEXITCODE
