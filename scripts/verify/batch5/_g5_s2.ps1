# _g5_s2.ps1 -- batch5 S2: auth guard (T19) -- XFF forgery + brute force lockout
# Flow: 6 failed logins for a phantom account, each with a DIFFERENT forged X-Forwarded-For.
# Expect: trusted-proxies empty -> XFF ignored -> all failures count on real IP 127.0.0.1 ->
#         lock key login:lock:acct:<probe>:127.0.0.1 exists; NO key keyed by forged IPs.
# Evidence: _g5_s2_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8200/api'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s2_out.txt'
$redisCli = 'F:\Redis\redis-cli.exe'
$probe = 'probe_t19_nobody'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function Reds([string[]]$a) { (@(& $redisCli -n 15 @a 2>$null) -join '').Trim() }
function Fix-Text([string]$s) {
    # PS 5.1 Invoke-RestMethod decodes non-charset JSON as ISO-8859-1 -> undo for readable evidence
    if ([string]::IsNullOrEmpty($s)) { return $s }
    try { return [Text.Encoding]::UTF8.GetString([Text.Encoding]::GetEncoding(28591).GetBytes($s)) } catch { return $s }
}

# --- pre: cleanup stale probe keys ---
foreach ($i in 1..6) {
    Reds @('DEL', "login:lock:acct:${probe}:10.0.0.$i") | Out-Null
    Reds @('DEL', "login:fail:acct:${probe}:10.0.0.$i") | Out-Null
}
Reds @('DEL', "login:lock:acct:${probe}:127.0.0.1") | Out-Null
Reds @('DEL', "login:fail:acct:${probe}:127.0.0.1") | Out-Null
Reds @('DEL', 'login:fail:ip:127.0.0.1') | Out-Null

# --- 6 failed logins, forged XFF varying each time ---
$msgs = @()
foreach ($i in 1..6) {
    $headers = @{ 'X-Forwarded-For' = "10.0.0.$i" }
    $body = @{ loginname = $probe; password = 'definitely-wrong-pass' } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/sys/login" -Method Post -ContentType 'application/json' -Body $body -Headers $headers -TimeoutSec 10
    $msgs += Fix-Text ([string]$r.msg)
    L ("attempt " + $i + " (XFF=10.0.0." + $i + "): code=" + $r.code)
}

# --- assertions ---
Check "lock key keyed by REAL ip (127.0.0.1) exists" ((Reds @('EXISTS', "login:lock:acct:${probe}:127.0.0.1")) -eq '1') "lock key missing"
$forgedLocks = 0
foreach ($i in 1..6) { if ((Reds @('EXISTS', "login:lock:acct:${probe}:10.0.0.$i")) -eq '1') { $forgedLocks++ } }
Check "no lock key keyed by any forged XFF ip" ($forgedLocks -eq 0) ("forged-key locks=" + $forgedLocks)
$ipFail = Reds @('GET', 'login:fail:ip:127.0.0.1')
# note: 6th attempt was cut by the pre-check (assertNotBlocked) and never counter-incremented,
# so the real-ip counter holds the 5 counted failures -- exactly 5.
Check "ip-dimension counter tracks real ip only (==5)" ($ipFail -eq '5') ("ipFail=" + $ipFail)
$forgedIpCounters = 0
foreach ($i in 1..6) { if ((Reds @('EXISTS', "login:fail:ip:10.0.0.$i")) -eq '1') { $forgedIpCounters++ } }
Check "no IP-dimension counter under any forged XFF ip" ($forgedIpCounters -eq 0) ("forged counters=" + $forgedIpCounters)
Check "lock message differs from normal failure (lock triggered on 5th)" ($msgs[4] -ne $msgs[0]) "identical messages"
L ("attempt1 msg hash: " + $msgs[0].GetHashCode() + " ; attempt5 msg hash: " + $msgs[4].GetHashCode() + " ; attempt6 msg hash: " + $msgs[5].GetHashCode())

# --- cleanup: leave no residue (admin login success would also clear ip counter) ---
Reds @('DEL', "login:lock:acct:${probe}:127.0.0.1") | Out-Null
Reds @('DEL', "login:fail:acct:${probe}:127.0.0.1") | Out-Null
Reds @('DEL', 'login:fail:ip:127.0.0.1') | Out-Null
L ("cleanup done; probe msg1 raw (utf8 in evidence): " + $msgs[0])
L ("probe msg5 raw: " + $msgs[4])
L ("probe msg6 raw: " + $msgs[5])

if ($fail -eq 0) { L 'S2 RESULT: PASS' } else { L ("S2 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
