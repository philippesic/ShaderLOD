param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$TaskName
)
$ErrorActionPreference='Stop'
$task=Get-ScheduledTask -TaskName $TaskName
if (-not $TaskName.StartsWith('ShaderLOD-') -or $task.State -eq 'Running') { throw 'Owned, quiescent ShaderLOD task required' }
$owned=$false
foreach ($action in $task.Actions) {
    if ($action.Arguments.Contains($Repository+'\benchmarks\windows\session.ps1') -or $action.Arguments.Contains($Repository+'\benchmarks\windows\campaign.ps1')) { $owned=$true }
}
if (-not $owned) { throw 'Task action does not match this repository' }
Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
