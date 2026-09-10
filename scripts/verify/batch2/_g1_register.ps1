$ErrorActionPreference = "Stop"
# G1 script: register 50 BARRIER-B-01..50 with explicit 32hex secrets + write sim secret file
# NOTE: ASCII-only on purpose (Windows PowerShell 5.1 reads BOM-less UTF-8 as GBK, Chinese literals corrupt)
# Rerun note: delete old batch records first:
#   DELETE FROM device_record WHERE device_no LIKE 'BARRIER-B-%';
$simDir = "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-barrier-sim"
$secretFile = Join-Path $simDir "batch-secrets.properties"

$ready = $false
foreach ($i in 1..40) {
    try {
        $login = Invoke-RestMethod -Uri "http://localhost:8200/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 3
        if ($login.code -eq 0) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}
if (-not $ready) { Write-Output "MAIN NOT READY"; exit 1 }
Write-Output "MAIN READY at $(Get-Date -Format 'HH:mm:ss')"
$h = @{ token = $login.data.token }

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$secrets = [ordered]@{}
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# batch2 G1 secret file (zero plaintext in repo - gitignored; same value as platform device_record.device_secret)")
for ($i = 1; $i -le 50; $i++) {
    $no = "BARRIER-B-" + $i.ToString("00")
    $bytes = New-Object byte[] 16
    $rng.GetBytes($bytes)
    $hex = ($bytes | ForEach-Object { $_.ToString("x2") }) -join ""
    $secrets[$no] = $hex
    $lines.Add("$no=$hex")
}
[System.IO.File]::WriteAllLines($secretFile, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "secret file written: $secretFile ($($secrets.Count) keys)"

$ok = 0; $dup = 0; $fail = 0
foreach ($no in $secrets.Keys) {
    $seq = [int]($no -replace "BARRIER-B-", "")
    $body = @{
        deviceNo     = $no
        deviceName   = "Batch-Barrier-" + $seq.ToString("00")
        deviceType   = 1
        location     = "batch-script-group"
        deviceSecret = $secrets[$no]
    } | ConvertTo-Json
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8200/api/device/record/save" -Method Post -ContentType "application/json" -Body $body -Headers $h -TimeoutSec 10
        if ($resp.code -eq 0) { $ok++ } else { $fail++ }
    } catch {
        $fail++
    }
}
Write-Output "register done: ok=$ok dup=$dup fail=$fail"
