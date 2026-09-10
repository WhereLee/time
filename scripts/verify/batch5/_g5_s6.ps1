# _g5_s6.ps1 -- batch5 S6: D-H millisecond regression, real barrier loop
# Flow: login -> POST command/open on BARRIER-B-01 -> poll ARRIVED; assert ms-domain timestamps
#       on device_command_log/device_record; then close back; assert no reconcile alarm flap;
#       assert sys_log stays SECONDS (anti over-conversion).
# Evidence: _g5_s6_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8200/api'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s6_out.txt'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$env:MYSQL_PWD = 'root'   # avoids the -p plaintext warning on stderr (PS5.1 EAP=Stop would treat it as fatal)
$dev = 'BARRIER-B-01'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function SqlN([string]$q) { ((& $mysql -uroot -D reason_faster -N -B --default-character-set=utf8mb4 -e $q 2>$null) | Select-Object -First 1) }
function Fix-Text([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return $s }
    try { return [Text.Encoding]::UTF8.GetString([Text.Encoding]::GetEncoding(28591).GetBytes($s)) } catch { return $s }
}

$lr = Invoke-RestMethod -Uri "$base/sys/login" -Method Post -ContentType 'application/json' -Body (@{ loginname = 'adminManager'; password = 'admin123' } | ConvertTo-Json -Compress) -TimeoutSec 10
Check "admin login ok" ($lr.code -eq 0) ("code=" + $lr.code)
$auth = @{ token = $lr.data.token }

function WaitArrived([string]$devNo, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $row = SqlN "SELECT CONCAT(command_status,'|',command_createtime,'|',COALESCE(command_updatetime,0)) FROM device_command_log WHERE device_no='$devNo' ORDER BY command_id DESC LIMIT 1"
        if ($row -match '^1\|') { return $row }
        Start-Sleep -Milliseconds 700
    }
    return $row
}

# --- OPEN ---
$r1 = Invoke-RestMethod -Uri "$base/device/command/open" -Method Post -ContentType 'application/json' -Body (@{ deviceNo = $dev } | ConvertTo-Json -Compress) -Headers $auth -TimeoutSec 15
L ("open code=" + $r1.code + " msg=" + (Fix-Text $r1.msg))
Check "open accepted" ($r1.code -eq 0) ("code=" + $r1.code)
$openRow = WaitArrived $dev 15
L ("open command row: " + $openRow)
$p = $openRow -split '\|'
Check "open ARRIVED (status=1)" ($p[0] -eq '1') ("row=" + $openRow)
Check "command_createtime in ms domain (>1e12)" ([long]$p[1] -gt 1000000000000) ("ts=" + $p[1])
Check "command_updatetime in ms domain (>1e12)" ([long]$p[2] -gt 1000000000000) ("ts=" + $p[2])
$st1 = SqlN "SELECT CONCAT(device_state,'|',device_updatetime) FROM device_record WHERE device_no='$dev'"
L ("record after open: " + $st1)
$st1p = $st1 -split '\|'
Check "device state=1 (UP) after open" ($st1p[0] -eq '1') ("state=" + $st1p[0])
Check "device_updatetime in ms domain (>1e12)" ([long]$st1p[1] -gt 1000000000000) ("ts=" + $st1p[1])

# --- CLOSE ---
Start-Sleep -Milliseconds 1200
$r2 = Invoke-RestMethod -Uri "$base/device/command/close" -Method Post -ContentType 'application/json' -Body (@{ deviceNo = $dev } | ConvertTo-Json -Compress) -Headers $auth -TimeoutSec 15
L ("close code=" + $r2.code + " msg=" + (Fix-Text $r2.msg))
Check "close accepted" ($r2.code -eq 0) ("code=" + $r2.code)
$closeRow = WaitArrived $dev 15
L ("close command row: " + $closeRow)
$c = $closeRow -split '\|'
Check "close ARRIVED (status=1)" ($c[0] -eq '1') ("row=" + $closeRow)
Check "close command_createtime in ms domain" ([long]$c[1] -gt 1000000000000) ("ts=" + $c[1])
$st2 = SqlN "SELECT CONCAT(device_state,'|',device_updatetime) FROM device_record WHERE device_no='$dev'"
L ("record after close: " + $st2)
Check "device state=2 (DOWN) after close" (($st2 -split '\|')[0] -eq '2') ("row=" + $st2)

# --- no reconcile flap in the last 10 minutes (grace semantics under ms) ---
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$flap = [int](SqlN "SELECT COUNT(*) FROM device_alarm WHERE device_no='$dev' AND alarm_createtime > $($nowMs - 600000) AND alarm_type IN (3,6)")
Check "no reconcile/moving-stuck alarm flap during the loop" ($flap -eq 0) ("count=" + $flap)

# --- sys_log stays seconds (anti over-conversion) ---
$lastSys = SqlN "SELECT log_createtime FROM sys_log ORDER BY log_id DESC LIMIT 1"
L ("latest sys_log log_createtime: " + $lastSys)
Check "sys_log timestamp remains seconds (<1e10)" ([long]$lastSys -lt 10000000000) ("ts=" + $lastSys)

if ($fail -eq 0) { L 'S6 RESULT: PASS' } else { L ("S6 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
