# _g4_verdict.ps1 — batch3 final verdict:
# 1) k-sample ledger vs sim state (full mismatch count + 5 samples)
# 2) PENDING flows = 0  3) unhandled alarm set  4) job 200/201 recent rounds
# 5) dual-path trace pairing (same traceId seen on HTTP entry + MQ consume)
$ErrorActionPreference = "Continue"
$log = "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-main\logs\info.log"
$out = New-Object System.Collections.Generic.List[string]
$out.Add("=== batch3 verdict $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===")

# 1) ledger vs sim (full + 5 samples)
$s = Invoke-RestMethod "http://127.0.0.1:8300/sim/status"
$simMap = @{}
foreach ($d in $s.devices) { $simMap[$d.deviceNo] = $d.stateCode }
$rows = & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT CONCAT(device_no, ' ', device_state) FROM device_record WHERE device_no LIKE 'BARRIER-B-%';" 2>$null
$mismatch = 0; $total = 0
foreach ($r in $rows) {
    $p = $r -split "\s+"
    if ($p.Count -ge 2 -and $p[0]) {
        $total++
        if ($simMap.ContainsKey($p[0]) -and [int]$p[1] -ne [int]$simMap[$p[0]]) { $mismatch++ }
    }
}
$out.Add("1) ledger-vs-sim: total=$total mismatch=$mismatch (expect 0 mismatch)")
$sample = $rows | Where-Object { $_ } | Get-Random -Count 5
foreach ($r in $sample) { $p = $r -split "\s+"; $out.Add("   sample $($p[0]): ledger=$($p[1]) sim=$($simMap[$p[0]])") }

# 2) PENDING
$pending = & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;" 2>$null
$out.Add("2) PENDING flows=$pending (expect 0)")

# 3) alarms
$un = & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;" 2>$null
$out.Add("3) unhandled alarms=$un (expect 0)")
$types = & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT alarm_type, alarm_handled, COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' GROUP BY alarm_type, alarm_handled ORDER BY alarm_type;" 2>$null
foreach ($t in $types) { $out.Add("   alarm(type handled count): $t") }

# 4) jobs
$j = & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT job_id, log_state, log_duration FROM schedule_job_log WHERE job_id IN (200,201) ORDER BY log_id DESC LIMIT 6;" 2>$null
foreach ($t in $j) { $out.Add("   job: $t") }

# 5) dual-path trace pairing (last MQ consumed events)
$mqLines = Select-String -Path $log -Pattern "\[MQ" | Select-Object -Last 30
$tids = @()
foreach ($l in $mqLines) {
    if ($l.Line -match "\[([0-9a-f]{32})\]") { if ($tids -notcontains $Matches[1]) { $tids += $Matches[1] } }
}
$tids = $tids | Select-Object -Last 3
$out.Add("5) dual-path trace pairing (HTTP entry vs MQ consume vs seq guard):")
foreach ($tid in $tids) {
    $pat = [regex]::Escape("[$tid]")
    $http = (Select-String -Path $log -Pattern "\[事件入口\]" | Where-Object { $_.Line -match $pat } | Measure-Object).Count
    $mqc = (Select-String -Path $log -Pattern "\[MQ事件消费\] deviceNo=" | Where-Object { $_.Line -match $pat } | Measure-Object).Count
    $guard = (Select-String -Path $log -Pattern "序守卫|心跳校正" | Where-Object { $_.Line -match $pat } | Measure-Object).Count
    $out.Add("   trace=$tid httpEntry=$http mqConsume=$mqc guardOrCorrection=$guard")
}

$out | Out-File -Encoding utf8 "c:\Users\lrs\Desktop\py\interview\_g4_verdict.txt"
Write-Output "written _g4_verdict.txt"
