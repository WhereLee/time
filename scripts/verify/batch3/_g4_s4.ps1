# _g4_s4.ps1 — batch3 S4: broker outage. Stop broker (10911), 10 x CLOSE during outage (B-11..20),
# restart broker, sim MQ backlog flushes on recovery.
# Assertions: flows closed via HTTP during outage; MQ backlog delivered after restart (idempotent absorb on platform);
# no RETRY_EXCEEDED faults; no unhandled alarms; final state consistent.
# NOTE: broker PID resolved via port 10911 listener; restart uses the same mqbroker.cmd as MQ-ENV-NOTES.
$ErrorActionPreference = "Stop"
$main = "http://localhost:8200"
$redisCli = "redis-cli"
$rmqBin = "D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release\bin"

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

Write-Output "=== S4 broker outage + 10 CLOSE + broker restart  $(Get-Date -Format 'HH:mm:ss') ==="
$line = netstat -ano | Select-String ":10911" | Select-String "LISTENING" | Select-Object -First 1
$brokerPid = [int](($line.ToString().Trim() -split "\s+") | Select-Object -Last 1)
Write-Output "broker pid=$brokerPid -> stopping (planned outage for MQ failover scenario)"
Stop-Process -Id $brokerPid -Force
Start-Sleep -Seconds 6
$alive = (netstat -ano | Select-String ":10911" | Select-String "LISTENING").Count
Write-Output "broker listening after stop: $alive (expect 0)"

$ok = 0; for ($i = 11; $i -le 20; $i++) { if (Send-Cmd ("BARRIER-B-" + $i.ToString("00")) "close") { $ok++ } }
Write-Output "S4 sent CLOSE x10 during outage: ok=$ok (HTTP event path carries; MQ buffering in sim)"

Start-Sleep -Seconds 20
$pending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;")
Write-Output "  t+20s: PENDING=$pending (expect 0 via HTTP path)"

Write-Output "restarting broker $(Get-Date -Format 'HH:mm:ss')"
Start-Process cmd -ArgumentList '/k', "cd /d $rmqBin && mqbroker.cmd -n 127.0.0.1:9876"
Start-Sleep -Seconds 20
$alive2 = (netstat -ano | Select-String ":10911" | Select-String "LISTENING").Count
Write-Output "broker listening after restart: $alive2 (expect 1)"

# wait MQ backlog flush: proxy reconnects broker, sim retained events flush on 5s cadence
foreach ($t in 20, 40, 60) {
    Start-Sleep -Seconds 20
    $mqOk = Select-String -Path "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-barrier-sim\sim-logs\info.log" -Pattern "MQ上报成功" | Select-Object -Last 1
    Write-Output "  flush t=${t}s: lastMQlog=$($mqOk.Line.Substring([Math]::Max(0,$mqOk.Line.Length-90)))"
}

$retryEx = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_type=1;")
$unhandled = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;")
$pending2 = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;")
$down = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state=2;")
$down20 = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_record WHERE device_no IN ('BARRIER-B-11','BARRIER-B-12','BARRIER-B-13','BARRIER-B-14','BARRIER-B-15','BARRIER-B-16','BARRIER-B-17','BARRIER-B-18','BARRIER-B-19','BARRIER-B-20') AND device_state=2;")
Write-Output "S4 verdict: RETRY_EXCEEDED=$retryEx (expect 0) unhandledAlarms=$unhandled (expect 0) PENDING=$pending2 (expect 0) ledgerDOWN(all)=$down (expect 50) targetGroupDOWN=$down20 (expect 10)"
Write-Output "=== S4 done $(Get-Date -Format 'HH:mm:ss') ==="
