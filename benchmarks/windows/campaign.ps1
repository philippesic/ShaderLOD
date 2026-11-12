param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Output,
    [Parameter(Mandatory=$true)][string]$Spec,
    [string]$Control,
    [switch]$Resume
)
$ErrorActionPreference='Stop'
Set-Location $Repository
$arguments=@('-m','shaderlod_host','qualify','--spec',$Spec,'--launch',"$Repository\build\moddev\windows-launch.json",'--profile',"$Repository\run-benchmark",'--display',"$Repository\build\moddev\windows-launch.json",'--ownership-root',"$Repository\ownership",'--output-root',$Output)
if ($Control) { $arguments+=@('--control',$Control) }
if ($Resume) { $arguments+='--resume' }
& "$Repository\tools\python\python.exe" @arguments *> ($Output+'.launcher.log')
exit $LASTEXITCODE
