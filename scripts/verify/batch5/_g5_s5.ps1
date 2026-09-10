# _g5_s5.ps1 -- batch5 S5: retention clean task, dual time-domain cutoff (D-H regression)
# Seeds OLD(100d)+FRESH rows into device_alarm(ms) / device_command_log(ms) / sys_log(s,+NULL row),
# triggers job 203, asserts: OLD gone / FRESH kept / NULL kept (sys_log must NOT be killed by a ms cutoff).
# Evidence: _g5_s5_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8200/api'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s5_out.txt'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$env:MYSQL_PWD = 'root'   # avoids the -p plaintext warning on stderr (PS5.1 EAP=Stop would treat it as fatal)
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function Sql([string]$q) { & $mysql -uroot -D reason_faster -N -B --default-character-set=utf8mb4 -e $q 2>$null }
function SqlN([string]$q) { (@(Sql $q) | Select-Object -First 1) }

$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$nowS  = [long][Math]::Floor($nowMs / 1000)
$oldMs = $nowMs - 100L * 86400L * 1000L
$oldS  = $nowS  - 100L * 86400L
L ("nowMs=$nowMs nowS=$nowS oldMs=$oldMs oldS=$oldS")

# --- cleanup any stale probes first ---
Sql "DELETE FROM device_alarm WHERE device_no='__g5_ret__'" | Out-Null
Sql "DELETE FROM device_command_log WHERE device_no='__g5_ret__'" | Out-Null
Sql "DELETE FROM sys_log WHERE log_module IN ('g5_ret','g5_ret_null')" | Out-Null

# --- seed ---
Sql "INSERT INTO device_alarm (device_no, alarm_type, alarm_content, alarm_handled, alarm_createtime) VALUES ('__g5_ret__', 2, 'g5 old alarm', 0, $oldMs)" | Out-Null
Sql "INSERT INTO device_alarm (device_no, alarm_type, alarm_content, alarm_handled, alarm_createtime) VALUES ('__g5_ret__', 2, 'g5 fresh alarm', 0, $nowMs)" | Out-Null
Sql "INSERT INTO device_command_log (device_no, command_action, command_seq, trigger_type, command_status, retry_count, command_createtime) VALUES ('__g5_ret__', 'OPEN', 1, 1, 0, 0, $oldMs)" | Out-Null
Sql "INSERT INTO device_command_log (device_no, command_action, command_seq, trigger_type, command_status, retry_count, command_createtime) VALUES ('__g5_ret__', 'OPEN', 2, 1, 0, 0, $nowMs)" | Out-Null
Sql "INSERT INTO sys_log (log_module, log_message, log_createtime) VALUES ('g5_ret', 'old seconds row', $oldS)" | Out-Null
Sql "INSERT INTO sys_log (log_module, log_message, log_createtime) VALUES ('g5_ret', 'fresh seconds row', $nowS)" | Out-Null
Sql "INSERT INTO sys_log (log_module, log_message, log_createtime) VALUES ('g5_ret_null', 'null ts row', NULL)" | Out-Null
L ("seeded: alarm=" + (SqlN "SELECT COUNT(*) FROM device_alarm WHERE device_no='__g5_ret__'") + " cmdlog=" + (SqlN "SELECT COUNT(*) FROM device_command_log WHERE device_no='__g5_ret__'") + " syslog=" + (SqlN "SELECT COUNT(*) FROM sys_log WHERE log_module IN ('g5_ret','g5_ret_null')"))

# --- login + trigger job 203 ---
$lr = Invoke-RestMethod -Uri "$base/sys/login" -Method Post -ContentType 'application/json' -Body (@{ loginname = 'adminManager'; password = 'admin123' } | ConvertTo-Json -Compress) -TimeoutSec 10
Check "admin login ok" ($lr.code -eq 0) ("code=" + $lr.code)
$auth = @{ token = $lr.data.token }
$rr = Invoke-RestMethod -Uri "$base/sys/schedule/run/203" -Method Post -Headers $auth -TimeoutSec 10
Check "job 203 triggered" ($rr.code -eq 0) ("code=" + $rr.code)

# poll up to 10s for the deletes to land (old alarm identified by CONTENT marker
# -- the ms/seconds domain boundary does NOT apply to old-vs-fresh rows: 100d-old ms
# values are still > 1e12; ms/seconds discriminator is 1e11)
$oldAlarmLeft = 1; $tries = 0
while ($tries -lt 10) {
    $oldAlarmLeft = [int](SqlN "SELECT COUNT(*) FROM device_alarm WHERE device_no='__g5_ret__' AND alarm_content='g5 old alarm'")
    if ($oldAlarmLeft -eq 0) { break }
    Start-Sleep -Milliseconds 1000; $tries++
}
L ("poll tries=" + $tries + " oldAlarmLeft=" + $oldAlarmLeft)

# --- assertions (content markers; ms domain >1e11, seconds domain <1e11) ---
Check "old alarm (ms, 100d) deleted by ms cutoff" ($oldAlarmLeft -eq 0) ("left=" + $oldAlarmLeft)
$freshAlarm = [int](SqlN "SELECT COUNT(*) FROM device_alarm WHERE device_no='__g5_ret__' AND alarm_content='g5 fresh alarm'")
$freshAlarmTs = SqlN "SELECT alarm_createtime FROM device_alarm WHERE device_no='__g5_ret__' AND alarm_content='g5 fresh alarm'"
Check "fresh alarm kept" ($freshAlarm -eq 1) ("fresh=" + $freshAlarm)
Check "fresh alarm ts is ms domain (>1e11)" ([long]$freshAlarmTs -gt 100000000000) ("ts=" + $freshAlarmTs)
$oldCmd = [int](SqlN "SELECT COUNT(*) FROM device_command_log WHERE device_no='__g5_ret__' AND command_seq=1")
$freshCmd = [int](SqlN "SELECT COUNT(*) FROM device_command_log WHERE device_no='__g5_ret__' AND command_seq=2")
$freshCmdTs = SqlN "SELECT command_createtime FROM device_command_log WHERE device_no='__g5_ret__' AND command_seq=2"
Check "old command log (ms) deleted" ($oldCmd -eq 0) ("old=" + $oldCmd)
Check "fresh command log kept" ($freshCmd -eq 1) ("fresh=" + $freshCmd)
Check "fresh command log ts is ms domain (>1e11)" ([long]$freshCmdTs -gt 100000000000) ("ts=" + $freshCmdTs)
$oldSys = [int](SqlN "SELECT COUNT(*) FROM sys_log WHERE log_module='g5_ret' AND log_message='old seconds row'")
$freshSys = [int](SqlN "SELECT COUNT(*) FROM sys_log WHERE log_module='g5_ret' AND log_message='fresh seconds row'")
$freshSysTs = SqlN "SELECT log_createtime FROM sys_log WHERE log_module='g5_ret' AND log_message='fresh seconds row'"
$nullSys = [int](SqlN "SELECT COUNT(*) FROM sys_log WHERE log_module='g5_ret_null'")
Check "old sys_log (seconds, 100d) deleted by seconds cutoff" ($oldSys -eq 0) ("old=" + $oldSys)
Check "fresh sys_log (seconds) kept -- would die if cutoff were milliseconds" ($freshSys -eq 1) ("fresh=" + $freshSys)
Check "fresh sys_log ts stays seconds domain (<1e11)" ([long]$freshSysTs -lt 100000000000) ("ts=" + $freshSysTs)
Check "NULL-timestamp sys_log kept (comparison exempt)" ($nullSys -eq 1) ("null=" + $nullSys)

# --- job evidence ---
$jobRow = SqlN "SELECT CONCAT(job_id,'|',log_state,'|',log_createtime) FROM schedule_job_log WHERE job_id=203 ORDER BY log_id DESC LIMIT 1"
L ("latest job203 log row: " + $jobRow)

# --- cleanup ---
Sql "DELETE FROM device_alarm WHERE device_no='__g5_ret__'" | Out-Null
Sql "DELETE FROM device_command_log WHERE device_no='__g5_ret__'" | Out-Null
Sql "DELETE FROM sys_log WHERE log_module IN ('g5_ret','g5_ret_null')" | Out-Null
L 'cleanup done'

if ($fail -eq 0) { L 'S5 RESULT: PASS' } else { L ("S5 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
