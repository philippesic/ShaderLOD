param(
    [Parameter(Mandatory=$true)][string]$Name,
    [Parameter(Mandatory=$true)][string]$Request,
    [string]$PreviousName
)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$runner='C:\ShaderLODBench\interactive-job.py'
$python='C:\ShaderLODBench\tools\python\Scripts\python.exe'
$base='C:\ShaderLODBench\requests\'
if(-not $Name.StartsWith('ShaderLOD-RTX5080-')){throw 'Owned task prefix required'}
if(-not ([IO.Path]::GetFullPath($Request)).StartsWith($base)){throw 'Owned request path required'}
if(-not (Test-Path -LiteralPath $Request)){throw 'Request missing'}
if($PreviousName){
    if(-not $PreviousName.StartsWith('ShaderLOD-RTX5080-')){throw 'Owned previous task required'}
    $old=Get-ScheduledTask -TaskName $PreviousName
    if($old.State -eq 'Running'){throw 'Previous task still running'}
    if($old.Actions.Execute -ne $python -or -not $old.Actions.Arguments.StartsWith($runner+' '+$base)){
        throw 'Previous task identity changed'
    }
    Unregister-ScheduledTask -TaskName $PreviousName -Confirm:$false
}
if(Get-ScheduledTask -TaskName $Name -ErrorAction SilentlyContinue){throw 'Existing task preserved'}
$action=New-ScheduledTaskAction -Execute $python -Argument ($runner+' '+$Request) -WorkingDirectory 'C:\ShaderLODBench\ShaderLOD'
$principal=New-ScheduledTaskPrincipal -UserId 'DESKTOP-E78LJSI\philip' -LogonType Interactive -RunLevel Limited
$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Minutes 30) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $Name -Action $action -Principal $principal -Settings $settings | Out-Null
Start-ScheduledTask -TaskName $Name
Get-ScheduledTask -TaskName $Name | Select-Object TaskName,State | ConvertTo-Json -Compress
