# _g4b_p2.ps1 - batch4 P2: batch-offline merged alarm (D-F, 52 devices offline -> 1 alarm)
$ErrorActionPreference = "Stop"
$sim = "http://127.0.0.1:8300"

function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" $sql 2>$null } finally { $ErrorActionPreference = $old }
}
function OnlineCount { (& redis-cli -n 15 keys "barrier:online:*" | Measure-Object -Line).Lines }

Write-Output "=== P2 batch-offline merged alarm $(Get-Date -Format 'HH:mm:ss') ==="
$type2Before = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=2 AND device_no LIKE 'BARRIER-B-%';")
$type7Before = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7;")
Write-Output "baseline: B-series type2=$type2Before type7(BATCH_OFFLINE)=$type7Before online=$(OnlineCount)"

Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"blockUpstream":true}' | Out-Null
Write-Output "network: blockUpstream=true (all heartbeat upstream blocked) at $(Get-Date -Format 'HH:mm:ss')"

# wait TTL(30s) + scan round(30s) + margin
Start-Sleep -Seconds 75
$type7Mid = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7;")
$type2Mid = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=2 AND device_no LIKE 'BARRIER-B-%';")
$batchUnhandled = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7 AND alarm_handled=0;")
$batchRow = Invoke-Mysql "SELECT device_no, alarm_content FROM device_alarm WHERE alarm_type=7 ORDER BY alarm_id DESC LIMIT 1;"
Write-Output "  t=75s: type7 new=$($type7Mid-$type7Before) (expect 1) B-series type2 new=$($type2Mid-$type2Before) (expect 0!) unhandledBatch=$batchUnhandled online=$(OnlineCount)"
Write-Output "  batch row: $batchRow"

Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body '{"blockUpstream":false}' | Out-Null
Write-Output "network: blockUpstream=false (restored) $(Get-Date -Format 'HH:mm:ss')"

foreach ($t in 30, 60, 90) {
    Start-Sleep -Seconds 30
    $bu = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7 AND alarm_handled=0;")
    Write-Output "  rec t=${t}s: online=$(OnlineCount) batchUnhandled=$bu"
}

$type7After = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7;")
$batchHandled = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=7 AND alarm_handled=1;")
$online = OnlineCount
$type2After = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=2 AND device_no LIKE 'BARRIER-B-%';")
Write-Output "P2 verdict: batchRows=$type7After (expect 1) batchHandled=$batchHandled (expect 1) online=$online (expect 50) B-seriesType2New=$($type2After-$type2Before) (expect 0)"
Write-Output "=== P2 done $(Get-Date -Format 'HH:mm:ss') ==="
