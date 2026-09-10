# _g4_s1.ps1 — batch3 S1: heartbeat delay 3000ms across 50 devices (pool vs beat), 10 x OPEN under delay.
# Assertions: online keys stay 50 (13s effective beat << 30s timeout: no OFFLINE), 10 flows ARRIVED, 0 unhandled alarms.
# Evidence: sim log "[剧本] 心跳人为延迟", pool threads coping; platform monitor 0ms.
$ErrorActionPreference = "Stop"
$main = "http://localhost:8200"
$sim = "http://127.0.0.1:8300"
$redisCli = "redis-cli"

$login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 5
if ($login.code -ne 0) { Write-Output "LOGIN FAIL"; exit 1 }
$h = @{ token = $login.data.token }

function Clear-RgKeys { (& $redisCli -n 15 keys "rg:*" 2>$null) | ForEach-Object { & $redisCli -n 15 del $_ | Out-Null } }
function Send-Cmd($no, $action) {
    for ($try = 1; $try -le 3; $try++) {
        Clear-RgKeys
        try {
            $resp = Invoke-RestMethod -Uri "$main/api/device/command/$action" -Method Post -ContentType "application/json" -Body (@{ deviceNo = $no } | ConvertTo-Json) -Headers $h -TimeoutSec 10
            if ($resp.code -eq 0) { return $true }
        } catch { }
        Start-Sleep -Seconds 4
    }
    return $false
}
function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" $sql 2>$null } finally { $ErrorActionPreference = $old }
}
function Online-Count { (& $redisCli -n 15 keys "barrier:online:*" | Measure-Object -Line).Lines }

Write-Output "=== S1 heartbeat-delay 3000ms + 10 OPEN  $(Get-Date -Format 'HH:mm:ss') ==="
Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"heartbeatDelayMillis":3000}' | Out-Null
Write-Output "network set: heartbeatDelayMillis=3000"

$ok = 0; for ($i = 1; $i -le 10; $i++) { if (Send-Cmd ("BARRIER-B-" + $i.ToString("00")) "open") { $ok++ } }
Write-Output "S1 sent OPEN x10: ok=$ok"

# observe 6 beats under delay (60s): sample online count at 20/40/60s
foreach ($t in 20, 40, 60) {
    Start-Sleep -Seconds 20
    Write-Output "  t=${t}s online=$(Online-Count)"
}

# restore
Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"heartbeatDelayMillis":0}' | Out-Null
Start-Sleep -Seconds 15

# verdict
$online = Online-Count
$alarm = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;")
$arrived = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=1 AND command_createtime > unix_timestamp()-180;")
$pending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;")
$up = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state=1;")
Write-Output "S1 verdict: online=$online (expect 50) unhandledAlarms=$alarm (expect 0) recentArrived=$arrived (expect >=10) PENDING=$pending (expect 0) ledgerUP=$up (expect 10)"
Write-Output "=== S1 done $(Get-Date -Format 'HH:mm:ss') ==="
