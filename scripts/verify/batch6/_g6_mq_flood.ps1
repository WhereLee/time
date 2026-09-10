# _g6_mq_flood.ps1 -- batch6: MQ-form event flood on real broker (MQ-only event channel)
# Goal (engineering ledger for the sample): under the MQ event channel, a 50-device
#   command flood must prove:
#   1) events really land in the topic (broker Max Offset growth >= 200 in window:
#      100 manual commands x (MOVING + ARRIVED) per command)
#   2) backlog drains back to zero (Consume Diff Total = 0, Inflight Total = 0)
#   3) command loop closes 50/50 x2 (all window manual rows ARRIVED, zero PENDING)
#   4) liveness unaffected (redis online=50 after flood, no new BARRIER-B-% alarms)
#   5) no platform ERROR line added and no sim ERROR line added in the window
#      (sim MQ buffer overflow is an ERROR-level log -> covered by the delta check)
# Flow: login -> baselines (error.log lines, topicStatus maxOffset, consumerProgress)
#   -> 50x OPEN fast sequential (mid-flood MQ samples) -> wait all UP
#   -> 50x CLOSE fast sequential (mid-flood MQ samples) -> wait all DOWN
#   -> settle poll until drained -> final assertions
# NOTE: mqadmin consumerProgress needs -t: the gRPC pop consumer group has no
#   %RETRY% topic route, a bare -g call fails (probe fact, see MQ-ENV-NOTES).
# Evidence: _g6_mq_flood_out.txt (UTF-8)
# NOTE: ASCII-only on purpose (Windows PowerShell 5.1 reads BOM-less UTF-8 as GBK).
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8200/api'
$simBase = 'http://127.0.0.1:8300'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g6_mq_flood_out.txt'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$redisCli = 'redis-cli'
$mqadmin = 'D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release\bin\mqadmin.cmd'
$mainErrLog = 'c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-main\logs\error.log'
$simErrLog = 'c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-barrier-sim\sim-logs\error.log'
$env:MYSQL_PWD = 'root'

$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function Sql([string]$q) { & $mysql -uroot -D reason_faster -N -B --default-character-set=utf8mb4 -e $q 2>$null }
function SqlN([string]$q) { (@(Sql $q) | Select-Object -First 1) }
function FileLines([string]$p) {
    # shared open: the logback holder keeps the file while the process runs
    # (plain ReadLines dies with IOException "being used by another process")
    if (-not (Test-Path $p)) { return -1 }
    $fs = [System.IO.File]::Open($p, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $sr = New-Object System.IO.StreamReader($fs)
        $n = 0; while ($sr.ReadLine() -ne $null) { $n++ }
        $sr.Dispose(); return $n
    } finally { $fs.Dispose() }
}
function Finish() {
    if ($fail -eq 0) { L 'S-MQ-FLOOD RESULT: PASS' } else { L ("S-MQ-FLOOD RESULT: FAIL (" + $fail + " check(s) failed)") }
    [System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
    exit $fail
}

# --- MQ observation helpers (real broker, remote views) ---
function MqProgress {
    $raw = & $mqadmin consumerProgress -n 127.0.0.1:9876 -g platform-device-event -t device-event 2>&1
    $txt = ($raw | Out-String)
    $diff = -1; $inflight = -1; $tps = -1.0
    if ($txt -match 'Consume Diff Total:\s*(\d+)') { $diff = [int]$Matches[1] }
    if ($txt -match 'Consume Inflight Total:\s*(\d+)') { $inflight = [int]$Matches[1] }
    if ($txt -match 'Consume TPS:\s*([\d.]+)') { $tps = [double]$Matches[1] }
    return @{ diff = $diff; inflight = $inflight; tps = $tps }
}
function MqSample([string]$label) {
    $p = MqProgress
    L ("MQ SAMPLE [" + $label + "] diff=" + $p.diff + " inflight=" + $p.inflight + " tps=" + $p.tps)
    return $p
}
function TopicMaxOffset {
    $raw = & $mqadmin topicStatus -n 127.0.0.1:9876 -t device-event 2>&1
    $line = ($raw | Where-Object { $_ -match '^\S+\s+\d+\s+\d+\s+\d+' } | Select-Object -First 1)
    if (-not $line) { return -1 }
    $toks = $line.Trim() -split '\s+'
    return [long]$toks[3]
}

L ("=== S-MQ-FLOOD start " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ===")

# --- precheck: login + online=50 + sim status 50 ---
$ready = $false
foreach ($i in 1..20) {
    try {
        $login = Invoke-RestMethod -Uri "$base/sys/login" -Method Post -ContentType 'application/json' -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 3
        if ($login.code -eq 0) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}
if (-not $ready) { L 'MAIN NOT READY'; Finish }
$h = @{ token = $login.data.token }
L 'precheck: login ok'

$online0 = @(& $redisCli -n 15 keys 'barrier:online:BARRIER-B-*' 2>$null | Where-Object { $_ -ne '' }).Count
Check 'precheck: 50 batch devices online in redis' ($online0 -eq 50) ("online=" + $online0)

try {
    $simSt = Invoke-RestMethod -Uri "$simBase/sim/status" -TimeoutSec 10
    $simCount = @($simSt.devices).Count
} catch { $simCount = -1 }
Check 'precheck: sim status returns 50 devices' ($simCount -eq 50) ("count=" + $simCount)
Check 'precheck: mqadmin present' (Test-Path $mqadmin) $mqadmin

# --- baselines ---
$t0ms = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$mainErr0 = FileLines $mainErrLog
$simErr0 = FileLines $simErrLog
$off0 = TopicMaxOffset
$cp0 = MqSample 'baseline'
Check 'baseline: topicStatus readable' ($off0 -ge 0) ("off0=" + $off0)
Check 'baseline: backlog zero before flood' (($cp0.diff -eq 0) -and ($cp0.inflight -eq 0)) ("diff=" + $cp0.diff + " inflight=" + $cp0.inflight)
L ("baseline: off0=" + $off0 + " mainErrLines=" + $mainErr0 + " simErrLines=" + $simErr0 + " t0ms=" + $t0ms)

# --- send helpers (same skeleton as batch2 flood: multi-terminal rg:* clear) ---
function Clear-RgKeys {
    $keys = & $redisCli -n 15 keys 'rg:*' 2>$null
    foreach ($k in $keys) { if ($k) { & $redisCli -n 15 del $k | Out-Null } }
}
function Send-Cmd($no, $action) {
    for ($try = 1; $try -le 3; $try++) {
        Clear-RgKeys
        try {
            $resp = Invoke-RestMethod -Uri "$base/device/command/$action" -Method Post -ContentType 'application/json' -Body (@{ deviceNo = $no } | ConvertTo-Json) -Headers $h -TimeoutSec 10
            if ($resp.code -eq 0) { return $true }
        } catch { }
        Start-Sleep -Seconds 4
    }
    return $false
}
function Wait-State($expect, $label) {
    for ($i = 1; $i -le 40; $i++) {
        Start-Sleep -Seconds 5
        $pending = [int](SqlN "SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state <> $expect")
        L ("  wait " + $label + ": still-not-target=" + $pending + " (try " + $i + "/40)")
        if ($pending -eq 0) { return $true }
    }
    return $false
}

# --- flood round 1: 50x OPEN, fast sequential ---
L '=== ROUND 1 OPEN x50 (fast) ==='
$ok = 0; $bad = @()
$tOpen = Get-Date
for ($i = 1; $i -le 50; $i++) {
    $no = 'BARRIER-B-' + $i.ToString('00')
    if (Send-Cmd $no 'open') { $ok++ } else { $bad += $no }
    if ($i -eq 25) { $cpMid1 = MqSample 'mid-open' }
}
$openWall = [int]((Get-Date) - $tOpen).TotalSeconds
L ("round1 OPEN sent: ok=" + $ok + " fail=" + $bad.Count + " wallSec=" + $openWall)
if ($bad.Count -gt 0) { L ("round1 failures: " + ($bad -join ',')) }
Check 'round1: 50/50 open accepted' ($ok -eq 50) ("ok=" + $ok)
$cpPost1 = MqSample 'post-open-send'
if (-not (Wait-State 1 'UP')) { L 'ROUND1 UP TIMEOUT'; Finish }
L 'round1: all 50 UP confirmed'
$cpUp = MqSample 'after-up'

# --- flood round 2: 50x CLOSE, fast sequential ---
L '=== ROUND 2 CLOSE x50 (fast) ==='
$ok2 = 0; $bad2 = @()
$tClose = Get-Date
for ($i = 1; $i -le 50; $i++) {
    $no = 'BARRIER-B-' + $i.ToString('00')
    if (Send-Cmd $no 'close') { $ok2++ } else { $bad2 += $no }
    if ($i -eq 25) { $cpMid2 = MqSample 'mid-close' }
}
$closeWall = [int]((Get-Date) - $tClose).TotalSeconds
L ("round2 CLOSE sent: ok=" + $ok2 + " fail=" + $bad2.Count + " wallSec=" + $closeWall)
if ($bad2.Count -gt 0) { L ("round2 failures: " + ($bad2 -join ',')) }
Check 'round2: 50/50 close accepted' ($ok2 -eq 50) ("ok=" + $ok2)
$cpPost2 = MqSample 'post-close-send'
if (-not (Wait-State 2 'DOWN')) { L 'ROUND2 DOWN TIMEOUT'; Finish }
L 'round2: all 50 DOWN confirmed'

# --- settle: poll until backlog drained ---
$drained = $false; $cpEnd = $null
foreach ($i in 1..12) {
    $cpEnd = MqSample ('settle-' + $i)
    if (($cpEnd.diff -eq 0) -and ($cpEnd.inflight -eq 0)) { $drained = $true; break }
    Start-Sleep -Seconds 5
}
Check 'backlog drains to zero (Diff=0 and Inflight=0)' $drained ("diff=" + $cpEnd.diff + " inflight=" + $cpEnd.inflight)

# --- final assertions ---
$online1 = @(& $redisCli -n 15 keys 'barrier:online:BARRIER-B-*' 2>$null | Where-Object { $_ -ne '' }).Count
Check 'final: online=50 after flood (liveness unaffected)' ($online1 -eq 50) ("online=" + $online1)

$off1 = TopicMaxOffset
$growth = $off1 - $off0
L ("MQ offset growth in window: " + $off0 + " -> " + $off1 + " (+" + $growth + ")")
Check 'events really landed in topic (offset growth >= 200)' ($growth -ge 200) ("growth=" + $growth)

$arrived = [int](SqlN "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND trigger_type=1 AND command_createtime > $t0ms AND command_status=1")
$notArrived = [int](SqlN "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND trigger_type=1 AND command_createtime > $t0ms AND command_status <> 1")
$pending = [int](SqlN "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0")
L ("window manual rows: ARRIVED=" + $arrived + " notArrived=" + $notArrived + " pendingAllTime=" + $pending)
Check 'flow: 100/100 window manual commands ARRIVED' (($arrived -eq 100) -and ($notArrived -eq 0)) ("arrived=" + $arrived + " notArrived=" + $notArrived)
Check 'no PENDING command for B-%' ($pending -eq 0) ("pending=" + $pending)

$stateRows = Sql "SELECT CONCAT(device_state,' x ',COUNT(*)) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' GROUP BY device_state"
L ("final states: " + ($stateRows -join ' / '))
$down = [int](SqlN "SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state=2")
Check 'final: all 50 devices DOWN' ($down -eq 50) ("down=" + $down)

$newAlarm = [int](SqlN "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_createtime > $t0ms")
Check 'no new B-group alarms in window' ($newAlarm -eq 0) ("new=" + $newAlarm)

$mainErr1 = FileLines $mainErrLog
$simErr1 = FileLines $simErrLog
L ("error.log lines: main " + $mainErr0 + " -> " + $mainErr1 + " | sim " + $simErr0 + " -> " + $simErr1)
Check 'platform error.log: no new line in window' ($mainErr1 -eq $mainErr0) ("delta=" + ($mainErr1 - $mainErr0))
Check 'sim error.log: no new line in window' ($simErr1 -eq $simErr0) ("delta=" + ($simErr1 - $simErr0))
if ($simErr1 -gt 0) {
    L '-- sim error.log tail (context, pre-existing lines) --'
    $fs2 = [System.IO.File]::Open($simErrLog, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $sr2 = New-Object System.IO.StreamReader($fs2)
        $all = New-Object System.Collections.Generic.List[string]
        while (($ln2 = $sr2.ReadLine()) -ne $null) { $all.Add($ln2) }
        $sr2.Dispose()
    } finally { $fs2.Dispose() }
    foreach ($ln in @($all | Select-Object -Last 3)) { L ("  " + $ln.Substring(0, [Math]::Min(160, $ln.Length))) }
}

L ("=== S-MQ-FLOOD done " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ===")
Finish
