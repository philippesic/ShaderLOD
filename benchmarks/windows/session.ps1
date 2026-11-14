param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Session,
    [int]$Timeout=600,
    [switch]$DistantHorizons,
    [switch]$DhGeneration,
    [switch]$WithoutDhWitness
)
$ErrorActionPreference='Stop'
Set-Location $Repository
$launch="$Repository\build\moddev\windows-launch.json"
$slot="$Repository\run-benchmark"
if ($DistantHorizons) {
    $launch="$Repository\build\moddev\windows-launch-dh.json"
    $slot="$Repository\run-benchmark-dh"
}
$options=@()
if ($DhGeneration) { $options+='--dh-generation' }
if ($WithoutDhWitness) { $options+='--without-dh-witness' }
& "$Repository\tools\python\python.exe" -m shaderlod_host session-open --launch $launch --profile $slot --display $launch --ownership-root "$Repository\ownership" --session $Session --timeout $Timeout --telemetry @options *> ($Session+'.launcher.log')
exit $LASTEXITCODE
