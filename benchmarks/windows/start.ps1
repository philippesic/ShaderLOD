param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Session,
    [Parameter(Mandatory=$true)][string]$TaskName,
    [int]$Timeout=600,
    [string]$Spec,
    [string]$Control,
    [switch]$Resume
)
$ErrorActionPreference='Stop'
if ($Timeout -lt 1 -or $Timeout -gt 14400) { throw 'Bounded session timeout required' }
if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) { throw 'Task name already exists' }
$user=(Get-CimInstance Win32_ComputerSystem).UserName
if (-not $user) { throw 'Interactive Windows user unavailable' }
$arguments='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$Repository+'\benchmarks\windows\session.ps1" -Repository "'+$Repository+'" -Session "'+$Session+'" -Timeout '+$Timeout
if ($Spec) { $arguments='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$Repository+'\benchmarks\windows\campaign.ps1" -Repository "'+$Repository+'" -Output "'+$Session+'" -Spec "'+$Spec+'"' }
if ($Control) { $arguments+=' -Control "'+$Control+'"' }
if ($Resume) { $arguments+=' -Resume' }
$action=New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument $arguments
$principal=New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Seconds ($Timeout+30))
Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Settings $settings | Out-Null
Start-ScheduledTask -TaskName $TaskName
Write-Output $TaskName
