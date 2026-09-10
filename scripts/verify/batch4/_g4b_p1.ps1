# _g4b_p1.ps1 - batch4 P1: metrics endpoint verify + baseline snapshot
$ErrorActionPreference = "Stop"
$main = "http://localhost:8200"

function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" $sql 2>$null } finally { $ErrorActionPreference = $old }
}

$login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 5
if ($login.code -ne 0) { Write-Output "LOGIN FAIL"; exit 1 }
$h = @{ token = $login.data.token }

Write-Output "=== P1 metrics + baseline $(Get-Date -Format 'HH:mm:ss') ==="
$m = Invoke-RestMethod -Uri "$main/api/device/metrics" -Method Get -Headers $h -TimeoutSec 10
Write-Output "metrics endpoint: code=$($m.code)"
Write-Output ("  pending=" + $m.data.commandPending + " online=" + $m.data.deviceOnline + "/" + $m.data.deviceProvisioned + " rate=" + $m.data.onlineRate + " unhandled=" + $m.data.unhandledAlarms)
foreach ($t in $m.data.barrierTasks) {
    Write-Output ("  task jobId=" + $t.jobId + " state=" + $t.jobState + " lastOkAgoSec=" + $t.lastSuccessAgoSeconds)
}

$dbPending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE command_status=0;")
$dbUnhandled = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_handled=0;")
$onlineKeys = (& redis-cli -n 15 keys "barrier:online:*" | Measure-Object -Line).Lines
Write-Output "crosscheck: dbPending=$dbPending dbUnhandled=$dbUnhandled redisOnlineKeys=$onlineKeys"

Write-Output "baseline new alarm types: type7=$([int](Invoke-Mysql 'SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7;')) type8=$([int](Invoke-Mysql 'SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8;')) type9=$([int](Invoke-Mysql 'SELECT COUNT(*) FROM device_alarm WHERE alarm_type=9;'))"

Write-Output "B-series ledger states:"
Invoke-Mysql "SELECT device_no, device_state FROM device_record WHERE device_no LIKE 'BARRIER-B-%' ORDER BY device_no;"

Write-Output "=== P1 done $(Get-Date -Format 'HH:mm:ss') ==="
