# _g4b_p4.ps1 - batch4 P4: reconcile escalation replay (batch3 Redis seq-rollback incident re-run)
# step1: tamper B-03 -> DOWN (physical-world change, no manual hold)
# step2: rollback redis seq by 5 -> next 5 auto rounds collide u_device_seq (silent-skip incident shape)
# step3: expect fail streak 1..5 -> RECONCILE_ERROR raised; round6 seq passes -> auto-recover, alarm closed
$ErrorActionPreference = "Stop"
$sim = "http://127.0.0.1:8300"
$dev = "BARRIER-B-03"

function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" $sql 2>$null } finally { $ErrorActionPreference = $old }
}

Write-Output "=== P4 reconcile escalation replay $(Get-Date -Format 'HH:mm:ss') ==="

$t = Invoke-RestMethod -Uri "$sim/sim/tamper" -Method Post -ContentType "application/json" -Body (@{ deviceNo = $dev; state = "DOWN" } | ConvertTo-Json) -TimeoutSec 10
Write-Output "tamper $dev -> DOWN: code=$($t.code)"
Start-Sleep -Seconds 5
$state = [int](Invoke-Mysql "SELECT device_state FROM device_record WHERE device_no='$dev';")
$dbMax = [int](Invoke-Mysql "SELECT IFNULL(MAX(command_seq),0) FROM device_command_log WHERE device_no='$dev';")
Write-Output "$dev ledger state=$state (expect 2 DOWN) dbMaxSeq=$dbMax"

& redis-cli -n 15 set "barrier:cmd-seq:$dev" ($dbMax - 5) | Out-Null
$rb = & redis-cli -n 15 get "barrier:cmd-seq:$dev"
Write-Output "redis seq rollback to $rb at $(Get-Date -Format 'HH:mm:ss') (expect $($dbMax-5); next 5 rounds collide)"

$t9Before = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=9 AND device_no='$dev';")
Write-Output "baseline: type9 for $dev = $t9Before"

$escalated = $false; $closed = $false
foreach ($i in 1..16) {
    Start-Sleep -Seconds 30
    $streak = & redis-cli -n 15 get "barrier:reconcile-fail:$dev"
    $un = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=9 AND device_no='$dev' AND alarm_handled=0;")
    $hd = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_alarm WHERE alarm_type=9 AND device_no='$dev' AND alarm_handled=1;")
    $st = [int](Invoke-Mysql "SELECT device_state FROM device_record WHERE device_no='$dev';")
    Write-Output "  t=$($i*30)s: failStreak=$streak unhandledT9=$un handledT9=$hd state=$st"
    if ($un -ge 1) { $escalated = $true }
    if ($escalated -and $un -eq 0 -and $hd -ge 1 -and $st -eq 1) { $closed = $true; break }
}

$streakEnd = & redis-cli -n 15 get "barrier:reconcile-fail:$dev"
$stEnd = [int](Invoke-Mysql "SELECT device_state FROM device_record WHERE device_no='$dev';")
$pending = [int](Invoke-Mysql "SELECT COUNT(*) FROM device_command_log WHERE device_no='$dev' AND command_status=0;")
$lastRow = Invoke-Mysql "SELECT command_seq, command_status FROM device_command_log WHERE device_no='$dev' ORDER BY command_seq DESC LIMIT 1;"
$streakCleared = [string]::IsNullOrEmpty($streakEnd)
Write-Output "P4 verdict: escalated=$escalated closed=$closed streakCleared=$streakCleared state=$stEnd (expect 1 UP) pending=$pending (expect 0)"
Write-Output "  last flow: $lastRow (expect ARRIVED=1)"
Write-Output "=== P4 done $(Get-Date -Format 'HH:mm:ss') ==="
