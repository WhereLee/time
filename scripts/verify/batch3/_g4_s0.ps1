# _g4_s0.ps1 — batch3 S0: reset baseline. 50 x manual CLOSE -> all DOWN.
# Grants 30-min ManualHold protection (auto-reconcile yields during the whole scenario set).
# ASCII-only on purpose (PS 5.1 reads BOM-less UTF-8 as GBK).
$ErrorActionPreference = "Stop"
$main = "http://localhost:8200"
$redisCli = "redis-cli"

$ready = $false
foreach ($i in 1..20) {
    try {
        $login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 3
        if ($login.code -eq 0) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}
if (-not $ready) { Write-Output "MAIN NOT READY"; exit 1 }
$h = @{ token = $login.data.token }

function Clear-RgKeys {
    $keys = & $redisCli -n 15 keys "rg:*" 2>$null
    foreach ($k in $keys) { & $redisCli -n 15 del $k | Out-Null }
}
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
function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { mysql -uroot -proot reason_faster $sql 2>$null } finally { $ErrorActionPreference = $old }
}
function Wait-State($expect, $label) {
    for ($i = 1; $i -le 40; $i++) {
        Start-Sleep -Seconds 5
        $pending = [int](Invoke-Mysql "-N -e `"SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state <> $expect;`"")
        Write-Output "  wait ${label}: still-not-target=$pending (try $i/40)"
        if ($pending -eq 0) { return $true }
    }
    return $false
}

Write-Output "=== S0 CLOSE all 50 (reset to DOWN) $(Get-Date -Format 'HH:mm:ss') ==="
$ok = 0; $fail = 0; $failList = @()
for ($g = 0; $g -lt 5; $g++) {
    $t0 = Get-Date
    for ($i = $g * 10 + 1; $i -le ($g + 1) * 10; $i++) {
        $no = "BARRIER-B-" + $i.ToString("00")
        if (Send-Cmd $no "close") { $ok++ } else { $fail++; $failList += $no }
    }
    $gap = 3 - ((Get-Date) - $t0).TotalSeconds
    if ($gap -gt 0 -and $g -lt 4) { Start-Sleep -Milliseconds ([int]($gap * 1000)) }
}
Write-Output "S0 sent: ok=$ok fail=$fail"
if ($fail -gt 0) { Write-Output "S0 failures: $($failList -join ',')" }
if (-not (Wait-State 2 "DOWN")) { Write-Output "S0 DOWN TIMEOUT"; exit 1 }
Write-Output "S0 all DOWN confirmed at $(Get-Date -Format 'HH:mm:ss')"

# pending check (assertion 2 baseline)
$p = [int](Invoke-Mysql "-N -e `"SELECT COUNT(*) FROM device_command_log WHERE device_no LIKE 'BARRIER-B-%' AND command_status=0;`"")
$alarm = [int](Invoke-Mysql "-N -e `"SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;`"")
Write-Output "S0 verdict: PENDING=$p unhandledAlarms=$alarm"
