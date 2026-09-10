# _g5_s3.ps1 -- batch5 S3: uplink traffic guard (per-device token bucket)
# Flow: burst 25 forged-signature events at device BARRIER-B-01 ->
#       expect mix of 401 (passed limiter, rejected by auth) and 429 (limiter rejected);
#       after 1.5s refill, a VALID heartbeat must pass (lifeline unaffected in normal state);
#       device state in DB must remain untouched (all forged events died at auth).
# Evidence: _g5_s3_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8200/api'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s3_out.txt'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$env:MYSQL_PWD = 'root'   # avoids the -p plaintext warning on stderr (PS5.1 EAP=Stop would treat it as fatal)
$redisCli = 'F:\Redis\redis-cli.exe'
$dev = 'BARRIER-B-01'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function SqlN([string]$q) { ((& $mysql -uroot -D reason_faster -N -B --default-character-set=utf8mb4 -e $q 2>$null) | Select-Object -First 1) }
function Reds([string[]]$a) { (@(& $redisCli -n 15 @a 2>$null) -join '').Trim() }

# --- device state before flood ---
$stateBefore = SqlN "SELECT device_state FROM device_record WHERE device_no='$dev'"
L ("device state before flood: " + $stateBefore)

# --- burst 25 forged-signature events ---
$s401 = 0; $s429 = 0; $sOther = 0
$sw = [Diagnostics.Stopwatch]::StartNew()
foreach ($i in 1..25) {
    $headers = @{ 'X-Device-No' = $dev; 'X-Device-Sign' = 'deadbeef-forged-signature' }
    $body = @{ deviceNo = $dev; state = 1; commandSeq = $null; bootId = 'g5flood'; eventSeq = 1 } | ConvertTo-Json -Compress
    try {
        $r = Invoke-WebRequest -Uri "$base/device/event" -Method Post -ContentType 'application/json' -Body $body -Headers $headers -UseBasicParsing -TimeoutSec 10
        $st = [int]$r.StatusCode
    } catch {
        $resp = $_.Exception.Response
        if ($resp -ne $null) { $st = [int]$resp.StatusCode } else { $st = -1 }
    }
    if ($st -eq 401) { $s401++ } elseif ($st -eq 429) { $s429++ } else { $sOther++ }
}
$sw.Stop()
L ("flood done: n=25 elapsedMs=" + $sw.ElapsedMilliseconds + " 401=" + $s401 + " 429=" + $s429 + " other=" + $sOther)

Check "limiter rejected part of the burst (429 >= 5)" ($s429 -ge 5) ("429=" + $s429)
Check "pre-auth passes through to HMAC rejection (401 >= 5)" ($s401 -ge 5) ("401=" + $s401)
Check "no event slipped into business layer (other(=non 401/429) == 0)" ($sOther -eq 0) ("other=" + $sOther)
Check "per-device bucket key exists in redis" ((Reds @('EXISTS', "barrier:traffic:dev:$dev")) -eq '1') "bucket key missing"

# --- device state must be untouched by forged events ---
$stateAfter = SqlN "SELECT device_state FROM device_record WHERE device_no='$dev'"
Check "device state unchanged after forged burst" ($stateAfter -eq $stateBefore) ("before=" + $stateBefore + " after=" + $stateAfter)

# --- valid heartbeat after refill (lifeline path works) ---
Start-Sleep -Milliseconds 1600
$secret = $null
foreach ($line in [System.IO.File]::ReadAllLines('c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-barrier-sim\batch-secrets.properties')) {
    if ($line -match ("^" + [regex]::Escape($dev) + "=(.+)$")) { $secret = $Matches[1].Trim() }
}
Check "secret loaded from batch-secrets file" ($null -ne $secret -and $secret.Length -eq 32) "secret missing"
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
$canonical = "$dev|$stateAfter"
$sig = (($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))) | ForEach-Object { $_.ToString('x2') }) -join ''
$hbHeaders = @{ 'X-Device-No' = $dev; 'X-Device-Sign' = $sig }
$hbBody = @{ deviceNo = $dev; state = [int]$stateAfter } | ConvertTo-Json -Compress
try {
    $hbResp = Invoke-RestMethod -Uri "$base/device/heartbeat" -Method Post -ContentType 'application/json' -Body $hbBody -Headers $hbHeaders -TimeoutSec 10
    $hbOk = ($hbResp.code -eq 0)
    L ("heartbeat after refill: code=" + $hbResp.code)
} catch {
    $hbOk = $false
    L ("heartbeat after refill: HTTP error " + $_.Exception.Message)
}
Check "valid heartbeat accepted after bucket refill" $hbOk "heartbeat failed"

# --- cleanup: reset bucket so later scripts start clean ---
Reds @('DEL', "barrier:traffic:dev:$dev") | Out-Null
Reds @('DEL', 'barrier:traffic:global') | Out-Null

if ($fail -eq 0) { L 'S3 RESULT: PASS' } else { L ("S3 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
