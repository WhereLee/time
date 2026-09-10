# _g4b_p3.ps1 - batch4 P3: task watchdog — enabled-but-not-running drift detection
# Step: pause job200 via API (state=1 + quartz paused) then force DB state=0
#       -> watchdog must detect it (enabled per DB, but no fresh success) and raise JOB_STALLED
#       -> resume via API -> next watchdog round auto-closes
$ErrorActionPreference = "Stop"
$main = "http://localhost:8200"

function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" $sql 2>$null } finally { $ErrorActionPreference = $old }
}

$login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 5
if ($login.code -ne 0) { Write-Output "LOGIN FAIL"; exit 1 }
$h = @{ token = $login.data.token }

Write-Output "=== P3 task watchdog drift $(Get-Date -Format 'HH:mm:ss') ==="
$st8Before = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8;")
Write-Output "baseline: type8(JOB_STALLED)=$st8Before"

$pause = Invoke-RestMethod -Uri "$main/api/sys/schedule/pause/200" -Method Post -Headers $h -TimeoutSec 10
Write-Output "pause job200 via API: code=$($pause.code)"
Invoke-Mysql "UPDATE schedule_job SET job_state=0 WHERE job_id=200;" | Out-Null
Write-Output "drift injected: quartz trigger PAUSED but schedule_job.state forced 0 (enabled-but-not-running) at $(Get-Date -Format 'HH:mm:ss')"

$seen = $false
foreach ($i in 1..10) {
    Start-Sleep -Seconds 30
    $c = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8 AND alarm_handled=0;")
    $lastOk = [int](Invoke-Mysql "SELECT IFNULL(MAX(log_createtime),0) FROM schedule_job_log WHERE job_id=200 AND log_state=0;")
    $now = [int]([DateTimeOffset]::Now.ToUnixTimeSeconds())
    Write-Output "  t=$($i*30)s: unhandledT8=$c job200LastOkAgoSec=$($now-$lastOk)"
    if ($c -ge 1) { $seen = $true; break }
}
Write-Output "watchdog raised: $seen"

$resume = Invoke-RestMethod -Uri "$main/api/sys/schedule/resume/200" -Method Post -Headers $h -TimeoutSec 10
Write-Output "resume job200 via API: code=$($resume.code) at $(Get-Date -Format 'HH:mm:ss')"

foreach ($t in 60, 120, 180) {
    Start-Sleep -Seconds 60
    $c = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8 AND alarm_handled=0;")
    $hd = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8 AND alarm_handled=1;")
    $lastOk2 = [int](Invoke-Mysql "SELECT IFNULL(MAX(log_createtime),0) FROM schedule_job_log WHERE job_id=200 AND log_state=0;")
    $now2 = [int]([DateTimeOffset]::Now.ToUnixTimeSeconds())
    Write-Output "  rec t=${t}s: unhandled=$c handledTotal=$hd job200LastOkAgoSec=$($now2-$lastOk2)"
}

$total = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8;")
$unh = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8 AND alarm_handled=0;")
$hdAll = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=8 AND alarm_handled=1;")
Write-Output "P3 verdict: raised=$($total-$st8Before) (expect 1) unhandled=$unh (expect 0) handled=$hdAll"
Write-Output "=== P3 done $(Get-Date -Format 'HH:mm:ss') ==="
