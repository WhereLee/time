# _g4_observe.ps1 — 批次3 量下回归：状态快照（观察/断言共用；输出落盘规避回显风暴）
# 用法: powershell -File _g4_observe.ps1 -Tag before
param([string]$Tag = "obs")
$ErrorActionPreference = "Continue"
$root = "c:\Users\lrs\Desktop\py\interview"
$f = Join-Path $root "_g4_$Tag.txt"
$out = New-Object System.Collections.Generic.List[string]
$out.Add("=== time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') tag=$Tag ===")
$out.Add("--- ledger(device_state) ---")
$out.Add(@((& mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT device_state, COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' GROUP BY device_state;" 2>$null)) -join " | ")
$out.Add("--- online keys(db15) ---")
$out.Add("$((& redis-cli "-n" "15" "keys" "barrier:online:*" | Measure-Object -Line).Lines)")
$out.Add("--- alarms unhandled (type:count) ---")
$out.Add(@((& mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT alarm_type, COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0 GROUP BY alarm_type;" 2>$null)) -join " | ")
$out.Add("--- flows by command_status ---")
$out.Add(@((& mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT command_status, COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' GROUP BY command_status;" 2>$null)) -join " | ")
$out.Add("--- job201 recent (id,state,duration) ---")
$out.Add(@((& mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT log_id, log_state, log_duration FROM schedule_job_log WHERE job_id=201 ORDER BY log_id DESC LIMIT 3;" 2>$null)) -join " | ")
$out.Add("--- sim states ---")
try {
    $s = Invoke-RestMethod "http://127.0.0.1:8300/sim/status"
    $out.Add((($s.devices | Group-Object state | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join " "))
} catch { $out.Add("sim status error: $($_.Exception.Message)") }
$out | Out-File -Encoding utf8 $f
Write-Output "written $f"
