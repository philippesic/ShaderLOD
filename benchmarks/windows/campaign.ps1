param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Output,
    [Parameter(Mandatory=$true)][string]$Spec,
    [string]$Control,
    [switch]$Resume,
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
$arguments=@('-m','shaderlod_host','qualify','--spec',$Spec,'--launch',$launch,'--profile',$slot,'--display',$launch,'--ownership-root',"$Repository\ownership",'--output-root',$Output)
if ($Control) { $arguments+=@('--control',$Control) }
if ($Resume) { $arguments+='--resume' }
& "$Repository\tools\python\python.exe" @arguments *> ($Output+'.launcher.log')
exit $LASTEXITCODE
