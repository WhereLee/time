# _g5_s4.ps1 -- batch5 S4: audit downgrade (reason.audit.query-log-enabled=false)
# Flow: login -> count sys_log -> 5x GET list -> count (must be equal, GET not logged)
#       -> POST schedule/run/203 (a @SysLog'd write) -> count (must be +1)
# Evidence: _g5_s4_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8200/api'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s4_out.txt'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$env:MYSQL_PWD = 'root'   # avoids the -p plaintext warning on stderr (PS5.1 EAP=Stop would treat it as fatal)
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function SqlN([string]$q) { ((& $mysql -uroot -D reason_faster -N -B --default-character-set=utf8mb4 -e $q 2>$null) | Select-Object -First 1) }

# --- login (POST /sys/login is @SysLog'd: itself writes one audit row) ---
$lr = Invoke-RestMethod -Uri "$base/sys/login" -Method Post -ContentType 'application/json' -Body (@{ loginname = 'adminManager'; password = 'admin123' } | ConvertTo-Json -Compress) -TimeoutSec 10
Check "admin login ok" ($lr.code -eq 0) ("login code=" + $lr.code)
$token = $lr.data.token
Check "token issued" (-not [string]::IsNullOrEmpty($token)) "empty token"
$auth = @{ token = $token }
Start-Sleep -Milliseconds 800

# --- count before GETs ---
$c1 = [long](SqlN 'SELECT COUNT(*) FROM sys_log')
L ("sys_log count after login: " + $c1)

# --- 5x GET list (query-log skip path) ---
foreach ($i in 1..5) {
    $r = Invoke-RestMethod -Uri "$base/device/record/list?page=1&limit=10" -Headers $auth -Method Get -TimeoutSec 10
    if ($i -eq 1) { L ("GET list code=" + $r.code) }
}
Start-Sleep -Milliseconds 800
$c2 = [long](SqlN 'SELECT COUNT(*) FROM sys_log')
L ("sys_log count after 5x GET: " + $c2)
Check "GET requests did NOT grow sys_log (downgrade works)" ($c2 -eq $c1) ("before=" + $c1 + " after=" + $c2)

# --- POST write path still audited: run job 203 (also serves S5 trigger check) ---
$rr = Invoke-RestMethod -Uri "$base/sys/schedule/run/203" -Method Post -Headers $auth -TimeoutSec 10
L ("run job 203 code=" + $rr.code)
Check "job 203 run accepted" ($rr.code -eq 0) ("code=" + $rr.code)
Start-Sleep -Milliseconds 1000
$c3 = [long](SqlN 'SELECT COUNT(*) FROM sys_log')
L ("sys_log count after POST: " + $c3)
Check "POST write path still audited (+1)" ($c3 -eq ($c2 + 1)) ("before=" + $c2 + " after=" + $c3)

# --- evidence: latest audit row for the POST ---
$lastRow = SqlN "SELECT CONCAT(log_module,'|',log_url,'|',log_createtime) FROM sys_log ORDER BY log_id DESC LIMIT 1"
L ("latest sys_log row: " + $lastRow)

if ($fail -eq 0) { L 'S4 RESULT: PASS' } else { L ("S4 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
