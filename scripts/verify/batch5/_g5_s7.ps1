# _g5_s7.ps1 -- batch5 S7: jasypt closed loop (prod profile boots with ENC + env key; wrong key fails)
# Case A: JASYPT_DEMO_KEY env var (demo key NOT stored in repo) -> prod boots on :8299, health UP, db UP.
# Case B: wrong key -> startup must FAIL with decryption error.
# Uses env-var overrides (SPRING_PROFILES_ACTIVE / SERVER_PORT / JASYPT_ENCRYPTOR_PASSWORD...).
# Evidence: _g5_s7_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$repo = 'c:\Users\lrs\Desktop\py\interview\inteink-faster'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s7_out.txt'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function Kill-Tree($proc) {
    if ($proc -ne $null -and -not $proc.HasExited) {
        & taskkill /F /T /PID $proc.Id 2>$null | Out-Null
        Start-Sleep -Milliseconds 1500
    }
}
function Read-Shared([string]$p) {
    # mvn redirection file stays open while the process lives -- open with share mode
    if (-not (Test-Path $p)) { return '' }
    try {
        $fs = [System.IO.File]::Open($p, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $sr = New-Object System.IO.StreamReader($fs)
        $t = $sr.ReadToEnd(); $sr.Close(); $fs.Close()
        return $t
    } catch { return '' }
}

function Run-ProdCase([string]$jasyptKey, [string]$port, [string]$outFile, [string]$errFile) {
    $env:SPRING_PROFILES_ACTIVE = 'prod'
    $env:SERVER_PORT = $port
    $env:REASON_DEVICE_MQ_ENABLED = 'false'
    $env:REASON_BARRIER_AUTO_ENABLED = 'false'
    $env:SPRING_QUARTZ_AUTO_STARTUP = 'false'
    $env:JASYPT_ENCRYPTOR_PASSWORD = $jasyptKey
    $p = Start-Process -FilePath 'mvn.cmd' -ArgumentList '-q','-pl','reason-main','spring-boot:run' -WorkingDirectory $repo -RedirectStandardOutput $outFile -RedirectStandardError $errFile -PassThru -WindowStyle Hidden
    return $p
}

# ---------- Case A: correct key ----------
$demoKey = $env:JASYPT_DEMO_KEY
if ([string]::IsNullOrWhiteSpace($demoKey)) { Write-Host 'ERROR: env JASYPT_DEMO_KEY required (demo key not stored in repo)'; exit 2 }
$outA = "$env:TEMP\_g5_s7_pos_out.txt"; $errA = "$env:TEMP\_g5_s7_pos_err.txt"
$pA = Run-ProdCase $demoKey '8299' $outA $errA
L ("caseA started pid=" + $pA.Id + " (port 8299, key=<env-injected>)")
$up = $false
$healthBody = ''
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if ($pA.HasExited) { break }
    try {
        $hj = Invoke-RestMethod -Uri 'http://127.0.0.1:8299/api/actuator/health' -TimeoutSec 2
        if ($hj.status -eq 'UP') { $up = $true; $healthBody = ($hj | ConvertTo-Json -Compress); break }
    } catch { }
}
$logTextA = ''
$logTextA += Read-Shared $outA
$logTextA += Read-Shared $errA
Check "caseA prod boots with ENC creds (health UP on 8299)" $up ("exited=" + $pA.HasExited)
L ("caseA health body: " + $healthBody)
# NOTE: this app wraps Druid in a custom dynamic datasource -> no 'db' component in actuator health.
# Proof of decrypt+connect = a real DB roundtrip: /sys/init reads sys_param through the pool.
$initOk = $false; $initCode = 'n/a'
try {
    $init = Invoke-RestMethod -Uri 'http://127.0.0.1:8299/api/sys/init' -TimeoutSec 5
    $initCode = $init.code
    $initOk = ($init.code -eq 0)
} catch { }
L ("caseA /sys/init code=" + $initCode)
Check "caseA DB roundtrip via decrypted creds (/sys/init code=0)" $initOk ("code=" + $initCode)
Check "caseA no decryption error in log" ($logTextA -notmatch 'EncryptionOperationNotPossible') "decryption error present"
Check "caseA log mentions port 8299" ($logTextA -match '8299') "port not found in log"
Kill-Tree $pA
L 'caseA process killed'

# ---------- Case B: wrong key ----------
Start-Sleep -Seconds 2
$outB = "$env:TEMP\_g5_s7_neg_out.txt"; $errB = "$env:TEMP\_g5_s7_neg_err.txt"
$pB = Run-ProdCase 'wrong-key-on-purpose' '8298' $outB $errB
L ("caseB started pid=" + $pB.Id + " (wrong key)")
$exited = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if ($pB.HasExited) { $exited = $true; break }
}
$logTextB = ''
$logTextB += Read-Shared $outB
$logTextB += Read-Shared $errB
Check "caseB wrong key causes startup failure" $exited ("still running after 120s")
Check "caseB failure is jasypt decryption related" (($logTextB -match 'EncryptionOperationNotPossible') -or ($logTextB -match 'jasypt') -or ($logTextB -match 'Failed to bind') -or ($logTextB -match 'decrypt')) "no decryption marker in log"
if (-not $exited) { Kill-Tree $pB }
$tailB = ''
if ($logTextB.Length -gt 1600) { $tailB = $logTextB.Substring($logTextB.Length - 1600) } else { $tailB = $logTextB }
L '--- caseB log tail (decryption failure evidence) ---'
foreach ($line in ($tailB -split "`r?`n")) { if ($line.Trim().Length -gt 0) { L ("  " + $line) } }

if ($fail -eq 0) { L 'S7 RESULT: PASS' } else { L ("S7 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
