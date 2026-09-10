# _g4b_verdict.ps1 - batch4 collection: metrics + alarm census + residue
$ErrorActionPreference = "Stop"
$main = "http://localhost:8200"

function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" $sql 2>$null } finally { $ErrorActionPreference = $old }
}
function OnlineCount { (& redis-cli -n 15 keys "barrier:online:*" | Measure-Object -Line).Lines }

$login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 5
if ($login.code -ne 0) { Write-Output "LOGIN FAIL"; exit 1 }
$h = @{ token = $login.data.token }

Write-Output "=== batch4 verdict $(Get-Date -Format 'HH:mm:ss') ==="

$m = Invoke-RestMethod -Uri "$main/api/device/metrics" -Method Get -Headers $h -TimeoutSec 10
Write-Output ("metrics endpoint: code=" + $m.code + " pending=" + $m.data.commandPending + " online=" + $m.data.deviceOnline + "/" + $m.data.deviceProvisioned + " rate=" + $m.data.onlineRate + " unhandled=" + $m.data.unhandledAlarms)
foreach ($t in $m.data.barrierTasks) {
    Write-Output ("  task " + $t.jobId + " state=" + $t.jobState + " lastOkAgoSec=" + $t.lastSuccessAgoSeconds)
}

foreach ($ty in 7, 8, 9) {
    $tot = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=$ty;")
    $hd = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=$ty AND alarm_handled=1;")
    $un = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=$ty AND alarm_handled=0;")
    Write-Output "type$ty total=$tot handled=$hd unhandled=$un"
}

$pending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE command_status=0;")
Write-Output "pendingFlows=$pending (expect 0) online=$(OnlineCount) (expect 50)"

Write-Output "B-series unhandled by type:"
Invoke-Mysql "SELECT alarm_type, COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0 GROUP BY alarm_type;"

Write-Output "reconcile-fail keys left: $((& redis-cli -n 15 keys "barrier:reconcile-fail:*" | Measure-Object -Line).Lines)"
Write-Output "alarm-rate keys left: $((& redis-cli -n 15 keys "barrier:alarm-rate:*" | Measure-Object -Line).Lines)"
Write-Output "=== verdict done $(Get-Date -Format 'HH:mm:ss') ==="
