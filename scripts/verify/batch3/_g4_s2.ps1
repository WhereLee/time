# _g4_s2.ps1 — batch3 S2: event delay 2000ms + dropNextEvent over 10 x CLOSE (B-01..10).
# Assertions: all 10 flows ARRIVED (dropped HTTP send recovered by retry and/or MQ dual-write),
# PENDING=0, 0 unhandled alarms (incl. no STATE_MISMATCH: 2s event delay < 3s grace).
# Evidence: sim log "[剧本] 事件延迟" and drop-then-retry; platform HTTP retry accept / MQ dual-path accept.
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

Write-Output "=== S2 event-delay 2000ms + dropNextEvent + 10 CLOSE  $(Get-Date -Format 'HH:mm:ss') ==="
Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"eventDelayMillis":2000,"dropNextEvent":true}' | Out-Null
Write-Output "network set: eventDelayMillis=2000 dropNextEvent=true"

$ok = 0; for ($i = 1; $i -le 10; $i++) { if (Send-Cmd ("BARRIER-B-" + $i.ToString("00")) "close") { $ok++ } }
Write-Output "S2 sent CLOSE x10: ok=$ok"

Start-Sleep -Seconds 45

# restore delay (dropNextEvent is one-shot, auto-cleared)
Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"eventDelayMillis":0}' | Out-Null
Start-Sleep -Seconds 10

$alarm = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;")
$pending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;")
$down = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state=2;")
# S2 target group final state: all DOWN (B-01..10 closed back)
$down10 = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_record WHERE device_no IN ('BARRIER-B-01','BARRIER-B-02','BARRIER-B-03','BARRIER-B-04','BARRIER-B-05','BARRIER-B-06','BARRIER-B-07','BARRIER-B-08','BARRIER-B-09','BARRIER-B-10') AND device_state=2;")
Write-Output "S2 verdict: unhandledAlarms=$alarm (expect 0) PENDING=$pending (expect 0) ledgerDOWN(all50)=$down (expect 50) targetGroupDOWN=$down10 (expect 10)"
Write-Output "=== S2 done $(Get-Date -Format 'HH:mm:ss') ==="
