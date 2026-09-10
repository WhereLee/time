# _g4_s3.ps1 — batch3 S3: upstream block 60s (T20 under load). 10 x OPEN during block (B-11..20).
# During: hearts lost ~6 beats -> OFFLINE alarms raised (~50); HTTP events blocked but MQ delivers -> flows ARRIVED.
# After restore: hearts resume -> markOnlineRecovered auto-closes OFFLINE; assertion 4: no residual alarms, flows terminal.
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

Write-Output "=== S3 upstream-block 60s + 10 OPEN  $(Get-Date -Format 'HH:mm:ss') ==="
$alarm0 = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_type=2;")
Write-Output "baseline OFFLINE alarms: $alarm0"

Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"blockUpstream":true}' | Out-Null
Write-Output "network set: blockUpstream=true (events+heartbeat upstream blocked)"

$ok = 0; for ($i = 11; $i -le 20; $i++) { if (Send-Cmd ("BARRIER-B-" + $i.ToString("00")) "open") { $ok++ } }
Write-Output "S3 sent OPEN x10: ok=$ok (downstream intact)"

Start-Sleep -Seconds 25
$p1 = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;")
Write-Output "  t=25s: PENDING=$p1 (expect 0: MQ dual-path closes flows while HTTP blocked) online=$(Online-Count)"

Start-Sleep -Seconds 45
$alarmMid = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_type=2 AND alarm_handled=0;")
$offTotal = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_type=2;")
Write-Output "  t=70s: OFFLINE raised=$($offTotal-$alarm0) unhandled=$alarmMid online=$(Online-Count)"

Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"blockUpstream":false}' | Out-Null
Write-Output "network set: blockUpstream=false (restored) $(Get-Date -Format 'HH:mm:ss')"

# wait recovery: heartbeats resume -> online keys back, markOnlineRecovered closes OFFLINE
foreach ($t in 15, 30, 45) {
    Start-Sleep -Seconds 15
    Write-Output "  rec t=${t}s: online=$(Online-Count) unhandledOFFLINE=$([int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_type=2 AND alarm_handled=0;"))"
}

$online = Online-Count
$unhandledAll = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;")
$offHandled = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_type=2 AND alarm_handled=1;")
$pending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;")
$up = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state=1;")
Write-Output "S3 verdict: online=$online (expect 50) unhandledAlarms=$unhandledAll (expect 0) OFFLINE handled=$offHandled (expect 50) PENDING=$pending (expect 0) ledgerUP=$up (expect 20)"
Write-Output "=== S3 done $(Get-Date -Format 'HH:mm:ss') ==="
