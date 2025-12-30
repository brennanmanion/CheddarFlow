param(
    [string]$RepoPath,
    [string]$NodePath = "C:\\Program Files\\nodejs\\node.exe",
    [string]$Time = "07:00",
    [string]$TaskName = "CheddarFlow Options Export"
)

if (-not $RepoPath) {
    Write-Error "-RepoPath is required (location of the cloned CheddarFlow repo)."
    exit 1
}

$scriptPath = Join-Path $RepoPath "automation\options_flow_download.js"

$action = New-ScheduledTaskAction -Execute $NodePath -Argument "`"$scriptPath`"" -WorkingDirectory $RepoPath
$trigger = New-ScheduledTaskTrigger -Daily -At $Time

$settings = New-ScheduledTaskSettingsSet -Compatibility Win8 -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description "Download CheddarFlow options CSV each morning" -RunLevel Highest -Force
Write-Host "Scheduled task '$TaskName' created for $Time"
