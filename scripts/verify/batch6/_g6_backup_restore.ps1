# _g6_backup_restore.ps1 -- batch6: backup & restore drill
# Goal: prove the production backup procedure (document/deploy/backup.sh core: mysqldump full + import)
#       end to end: dump -> import into a TEMP database -> restored counts must sit inside the
#       [t0, t1] window of the LIVE source counts (the platform keeps writing while we dump).
# Safety: never touches the source db; temp db "reason_faster_restore_check" is dropped afterwards.
# Requires: MYSQL_PWD env var (local dev root credential). Evidence: _g6_backup_restore_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$dumpExe = Join-Path $mysqlBin 'mysqldump.exe'
$work = "$env:TEMP\_g6_drill"
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g6_backup_restore_out.txt'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
if ([string]::IsNullOrWhiteSpace($env:MYSQL_PWD)) { Write-Host 'ERROR: MYSQL_PWD env var required'; exit 2 }
New-Item -ItemType Directory -Force $work | Out-Null
$srcDb = 'reason_faster'
$rstrDb = 'reason_faster_restore_check'
$dumpFile = Join-Path $work 'reason_faster_drill.sql'
$tables = @('device_record', 'device_command_log', 'device_alarm', 'sys_log', 'schedule_job', 'sys_param', 'sys_user', 'sys_role_menu')

# ---- 0) snapshot LIVE source counts (t0) ----
$before = @{}
foreach ($t in $tables) {
    $before[$t] = [long]((& $mysql '-uroot' '-N' '-e' ("SELECT COUNT(*) FROM " + $t) $srcDb | Select-Object -First 1).Trim())
}
L "t0 source counts snapshot taken (live db keeps writing; assertions use window [t0,t1])"

# ---- 1) dump source db (same flags as deploy/backup.sh) ----
$p = Start-Process -FilePath $dumpExe -ArgumentList '-uroot', '--single-transaction', '--routines', '--triggers', '--events', '--set-gtid-purged=OFF', $srcDb -RedirectStandardOutput $dumpFile -NoNewWindow -PassThru -Wait
Check "mysqldump exits 0" ($p.ExitCode -eq 0) ("exit=" + $p.ExitCode)
$len = (Get-Item $dumpFile).Length
L ("dump size = " + $len + " bytes")
Check "dump file non-trivial (>500KB)" ($len -gt 500KB) ("len=" + $len)
$hasTable = Select-String -Path $dumpFile -Pattern 'CREATE TABLE `device_record`' -Quiet
Check "dump contains device_record DDL" ($hasTable) "DDL marker missing"

# ---- 2) create temp db + import ----
& $mysql '-uroot' '-e' ("DROP DATABASE IF EXISTS " + $rstrDb + "; CREATE DATABASE " + $rstrDb + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;")
$p2 = Start-Process -FilePath $mysql -ArgumentList '-uroot', $rstrDb -RedirectStandardInput $dumpFile -NoNewWindow -PassThru -Wait
Check "restore import exits 0" ($p2.ExitCode -eq 0) ("exit=" + $p2.ExitCode)

# ---- 3) t1 counts + restored counts + window assertion ----
foreach ($t in $tables) {
    $after = [long]((& $mysql '-uroot' '-N' '-e' ("SELECT COUNT(*) FROM " + $t) $srcDb | Select-Object -First 1).Trim())
    $rst = [long]((& $mysql '-uroot' '-N' '-e' ("SELECT COUNT(*) FROM " + $t) $rstrDb | Select-Object -First 1).Trim())
    L ("rowcount " + $t + ": t0=" + $before[$t] + " t1=" + $after + " restore=" + $rst)
    Check ("restore in window [t0,t1] for " + $t) (($rst -ge $before[$t]) -and ($rst -le $after)) ("t0=" + $before[$t] + " rst=" + $rst + " t1=" + $after)
}

# ---- 4) cleanup ----
& $mysql '-uroot' '-e' ("DROP DATABASE " + $rstrDb + ";")
Remove-Item $dumpFile -Force -ErrorAction SilentlyContinue
L "restore drill cleanup done (temp db dropped, source untouched)"

$verdict = if ($fail -eq 0) { 'PASS' } else { 'FAIL' }
L ("B6-BACKUP RESULT: " + $verdict)
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
