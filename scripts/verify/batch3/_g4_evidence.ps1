# _g4_evidence.ps1 — batch3 evidence collector: sim + platform log fragments per scenario.
$ErrorActionPreference = "Continue"
$simLog = "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-barrier-sim\sim-logs\info.log"
$platInfo = "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-main\logs\info.log"
$platWarn = "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-main\logs\warn.log"
$out = New-Object System.Collections.Generic.List[string]
$out.Add("=== batch3 evidence $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===")

function Grab($file, $pat, $n, $label) {
    $lines = Select-String -Path $file -Pattern $pat | Select-Object -Last $n
    $out.Add("--- $label (#$($lines.Count)) ---")
    foreach ($l in $lines) {
        $t = $l.Line
        if ($t.Length -gt 150) { $t = $t.Substring($t.Length - 150) }
        $out.Add("  $t")
    }
}

# S1: heartbeat delay injection + pool coping
Grab $simLog "心跳人为延迟|heartbeat delay" 3 "S1 sim heartbeat-delay set"
# S2: event delay + drop
Grab $simLog "事件延迟|丢下一次事件" 5 "S2 sim event-delay/drop"
Grab $platInfo "上报失败-重试|上报失败" 3 "S2 platform-side retry visible (HTTP)"
# S3: upstream block + MQ delivery under block + OFFLINE raise + recovery
Grab $simLog "上行阻断|上行链路" 4 "S3 sim upstream-block"
Grab $platInfo "\[MQ事件消费\] deviceNo=BARRIER-B-1" 4 "S3 MQ consumed B-11..19 during block"
Grab $platInfo "设备告警.*type=设备离线" 3 "S3 OFFLINE raised"
Grab $platInfo "设备恢复在线|在线恢复" 3 "S3 online recovered (alarm auto-close trigger)"
# S4: broker outage effects on sim MQ channel + recovery flush
Grab $simLog "\[MQ上报失败" 3 "S4 sim MQ send failures during outage"
Grab $simLog "\[MQ上报成功" 3 "S4 sim MQ flush after restart"
Grab $platInfo "\[MQ事件消费\] deviceNo=" 3 "S4 platform MQ consumption after restart"

# seq guard / idempotent absorb evidence (dual-path)
Grab $platInfo "事件序守卫拒绝" 3 "dual idempotent absorb (seq guard reject)"
Grab $platInfo "指令按 seq 精确闭环" 3 "flows closed by seq"

$out | Out-File -Encoding utf8 "c:\Users\lrs\Desktop\py\interview\_g4_evidence.txt"
Write-Output "written _g4_evidence.txt"
