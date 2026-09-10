$ErrorActionPreference = "Stop"
# G2 script: event flood over 50 batch devices (BARRIER-B-01..50)
# Round 1: OPEN all 50 (10 per group, group gap 3s > moveMillis 3s) -> wait all UP
# Round 2: CLOSE all 50 (same skeleton) -> wait all DOWN
# Total = 100 manual commands / >=200 events (MOVING + arrived per command).
# NOTE: RepeatGuard blocks same operator+method within 3s window. The flood is a
# multi-terminal scenario, so the script clears the guard keys before EVERY send
# (equivalent to 50 independent operator terminals, one command each).
# NOTE: ASCII-only on purpose (Windows PowerShell 5.1 reads BOM-less UTF-8 as GBK).

$main = "http://localhost:8200"
$redisCli = "redis-cli"

# --- login ---
$ready = $false
foreach ($i in 1..40) {
    try {
        $login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 3
        if ($login.code -eq 0) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}
if (-not $ready) { Write-Output "MAIN NOT READY"; exit 1 }
$h = @{ token = $login.data.token }
Write-Output "MAIN READY at $(Get-Date -Format 'HH:mm:ss')"

# --- clear repeat-guard key (per-send, see header note) ---
function Clear-RgKeys {
    $keys = & $redisCli -n 15 keys "rg:*" 2>$null
    foreach ($k in $keys) { & $redisCli -n 15 del $k | Out-Null }
}

# --- send one command with retry (guard race fallback) ---
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

# mysql writes a password warning to stderr -> with $ErrorActionPreference=Stop that
# becomes a terminating NativeCommandError in PS 5.1. Helper switches to Continue.
function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { mysql -uroot -proot reason_faster $sql 2>$null } finally { $ErrorActionPreference = $old }
}

# --- wait until all 50 reach expected device_state (1=UP 2=DOWN) ---
function Wait-State($expect, $label) {
    for ($i = 1; $i -le 40; $i++) {
        Start-Sleep -Seconds 5
        $row = Invoke-Mysql "-N -e `"SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state <> $expect;`""
        $pending = [int]$row
        Write-Output "  wait ${label}: still-not-target=$pending (try $i/40)"
        if ($pending -eq 0) { return $true }
    }
    return $false
}

# --- round 1: OPEN ---
$ok = 0; $fail = 0; $failList = @()
Write-Output "=== ROUND 1 OPEN ==="
for ($g = 0; $g -lt 5; $g++) {
    $t0 = Get-Date
    for ($i = $g * 10 + 1; $i -le ($g + 1) * 10; $i++) {
        $no = "BARRIER-B-" + $i.ToString("00")
        if (Send-Cmd $no "open") { $ok++ } else { $fail++; $failList += $no }
    }
    $gap = 3 - ((Get-Date) - $t0).TotalSeconds
    if ($gap -gt 0 -and $g -lt 4) { Start-Sleep -Milliseconds ([int]($gap * 1000)) }
}
Write-Output "round1 sent: ok=$ok fail=$fail"
if ($fail -gt 0) { Write-Output "round1 failures: $($failList -join ',')" }
Write-Output "waiting all UP..."
if (-not (Wait-State 1 "UP")) { Write-Output "ROUND1 UP TIMEOUT"; exit 1 }
Write-Output "all UP confirmed"

# --- round 2: CLOSE ---
$ok2 = 0; $fail2 = 0; $failList2 = @()
Write-Output "=== ROUND 2 CLOSE ==="
for ($g = 0; $g -lt 5; $g++) {
    $t0 = Get-Date
    for ($i = $g * 10 + 1; $i -le ($g + 1) * 10; $i++) {
        $no = "BARRIER-B-" + $i.ToString("00")
        if (Send-Cmd $no "close") { $ok2++ } else { $fail2++; $failList2 += $no }
    }
    $gap = 3 - ((Get-Date) - $t0).TotalSeconds
    if ($gap -gt 0 -and $g -lt 4) { Start-Sleep -Milliseconds ([int]($gap * 1000)) }
}
Write-Output "round2 sent: ok=$ok2 fail=$fail2"
if ($fail2 -gt 0) { Write-Output "round2 failures: $($failList2 -join ',')" }
Write-Output "waiting all DOWN..."
if (-not (Wait-State 2 "DOWN")) { Write-Output "ROUND2 DOWN TIMEOUT"; exit 1 }
Write-Output "all DOWN confirmed"

# --- assertions (mysql via helper, output goes to file) ---
Write-Output ""
Write-Output "=== ASSERT: command log final states (BARRIER-B-%, expect all 1=ARRIVED) ==="
Invoke-Mysql "-e `"SELECT trigger_type, command_status, COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' GROUP BY trigger_type, command_status ORDER BY trigger_type, command_status;`""
Write-Output "=== ASSERT: manual commands (trigger_type=1) in this run ==="
Invoke-Mysql "-e `"SELECT command_status, COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND trigger_type=1 GROUP BY command_status;`""
Write-Output "=== ASSERT: device states (expect 50 x DOWN=2) ==="
Invoke-Mysql "-e `"SELECT device_state, COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' GROUP BY device_state;`""
Write-Output "=== ASSERT: BARRIER-B alarms (expect 0) ==="
Invoke-Mysql "-e `"SELECT COUNT(*) AS cnt FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;`""
Write-Output "=== ASSERT: monitor task recent rounds (log_duration ms, expect < 30000) ==="
Invoke-Mysql "-e `"SELECT log_id, job_id, log_state, log_duration, log_createtime FROM schedule_job_log WHERE job_id=201 ORDER BY log_id DESC LIMIT 5;`""
Write-Output "=== DONE at $(Get-Date -Format 'HH:mm:ss') ==="
