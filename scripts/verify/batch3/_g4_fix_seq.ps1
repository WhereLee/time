# _g4_fix_seq.ps1 — 批次3 环境修复：Redis seq 计数器对齐 DB 权威值
# 背景：机器重启后 Redis 加载旧 dump，barrier:cmd-seq:* 回退 → 对账 INCR 撞 u_device_seq 唯一索引
# 修复：每台设备 set barrier:cmd-seq:{deviceNo} = MAX(command_seq)（DB 为权威——唯一索引的约束面）
$ErrorActionPreference = "Continue"
$rows = & mysql "-uroot" "-proot" "reason_faster" "-N" "-e" "SELECT device_no, MAX(command_seq) FROM device_command_log GROUP BY device_no;" 2>$null
$n = 0
foreach ($r in $rows) {
    $p = $r -split "\s+"
    if ($p.Count -ge 2 -and $p[0]) {
        $old = & redis-cli "-n" "15" "get" "barrier:cmd-seq:$($p[0])"
        & redis-cli "-n" "15" "set" "barrier:cmd-seq:$($p[0])" $p[1] | Out-Null
        Write-Output "$($p[0]): redis=$old -> db_max=$($p[1])"
        $n++
    }
}
Write-Output "aligned $n devices"
