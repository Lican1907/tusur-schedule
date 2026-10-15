# PowerShell launcher for TUSUR Schedule Telegram Bot
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$python = Join-Path $scriptDir ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    $python = "python"
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Starting TUSUR Schedule Telegram Bot (@tusur_schedule_changes_bot) " -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Cyan

& $python bot.py
