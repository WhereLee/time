$ErrorActionPreference = "Stop"
# G2 baseline reset: close all 50 (round1 left some UP) -> wait all DOWN
# ASCII-only on purpose (PS 5.1 BOM-less UTF-8 = GBK corruption).
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
Write-Output "MAIN READY at $(Get-Date -Format 'HH:mm:ss')"

function Invoke-Mysql($sql) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { mysql -uroot -proot reason_faster $sql 2>$null } finally { $ErrorActionPreference = $old }
}

function Clear-RgKeys {
    $keys = & $redisCli -n 15 keys "rg:*" 2>$null
    foreach ($k in $keys) { & $redisCli -n 15 del $k | Out-Null }
}

$ok = 0; $fail = 0
for ($i = 1; $i -le 50; $i++) {
    $no = "BARRIER-B-" + $i.ToString("00")
    Clear-RgKeys
    try {
        $resp = Invoke-RestMethod -Uri "$main/api/device/command/close" -Method Post -ContentType "application/json" -Body (@{ deviceNo = $no } | ConvertTo-Json) -Headers $h -TimeoutSec 10
        if ($resp.code -eq 0) { $ok++ } else { $fail++ }
    } catch { $fail++ }
}
Write-Output "reset close sent: ok=$ok fail=$fail"

for ($t = 1; $t -le 30; $t++) {
    Start-Sleep -Seconds 5
    $row = Invoke-Mysql "-N -e `"SELECT COUNT(*) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' AND device_state <> 2;`""
    $pending = [int]$row
    Write-Output "wait DOWN: still-not-down=$pending (try $t/30)"
    if ($pending -eq 0) { Write-Output "ALL DOWN"; exit 0 }
}
Write-Output "RESET TIMEOUT"; exit 1
