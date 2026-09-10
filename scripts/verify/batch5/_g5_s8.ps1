# _g5_s8.ps1 -- batch5 S8: fail-fast without profile (T16)
# Flow: run reason-main WITHOUT any profile -> must fail fast (datasource missing), duration recorded.
# Evidence: _g5_s8_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$repo = 'c:\Users\lrs\Desktop\py\interview\inteink-faster'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s8_out.txt'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0
function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}

# ensure NO profile leaks in from this session
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Remove-Item Env:JASYPT_ENCRYPTOR_PASSWORD -ErrorAction SilentlyContinue
# note: jasypt key not needed here -- fail-fast occurs at datasource wiring (before any ENC decryption)

$outF = "$env:TEMP\_g5_s8_out.txt"; $errF = "$env:TEMP\_g5_s8_err.txt"
$sw = [Diagnostics.Stopwatch]::StartNew()
$p = Start-Process -FilePath 'mvn.cmd' -ArgumentList '-q','-pl','reason-main','spring-boot:run' -WorkingDirectory $repo -RedirectStandardOutput $outF -RedirectStandardError $errF -PassThru -WindowStyle Hidden
L ("started pid=" + $p.Id + " with NO profile")

$exited = $false
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    if ($p.HasExited) { $exited = $true; break }
}
$sw.Stop()
$exitCode = $null
if ($exited) { $exitCode = $p.ExitCode }
L ("exited=" + $exited + " exitCode=" + $exitCode + " elapsedSec=" + [Math]::Round($sw.Elapsed.TotalSeconds, 1))

$logText = ''
if (Test-Path $outF) { $logText += [System.IO.File]::ReadAllText($outF) }
if (Test-Path $errF) { $logText += [System.IO.File]::ReadAllText($errF) }

Check "process exited (fail-fast, not hanging)" $exited "still running after 90s"
Check "startup failed (non-zero exit)" ($exited -and $exitCode -ne 0) ("exitCode=" + $exitCode)
$marker = ($logText -match 'Failed to configure a DataSource') -or ($logText -match 'APPLICATION FAILED TO START') -or ($logText -match 'BUILD FAILURE') -or ($logText -match 'dbType not support') -or ($logText -match 'Unable to start embedded Tomcat')
Check "failure marker present (datasource/failed-start/build-failure)" $marker "no marker found"
Check "fail-fast within 30s" ($sw.Elapsed.TotalSeconds -lt 30) ("elapsed=" + [Math]::Round($sw.Elapsed.TotalSeconds, 1))

# root cause evidence line (custom dynamic datasource fails on missing url before Spring's analyzer)
foreach ($line in ($logText -split "`r?`n")) {
    if ($line -match 'Caused by: java\.lang\.IllegalStateException: dbType not support') { L ("ROOT CAUSE: " + $line.Trim()) }
}

if (-not $exited) { & taskkill /F /T /PID $p.Id 2>$null | Out-Null }

$tail = ''
if ($logText.Length -gt 1200) { $tail = $logText.Substring($logText.Length - 1200) } else { $tail = $logText }
L '--- failure tail ---'
foreach ($line in ($tail -split "`r?`n")) { if ($line.Trim().Length -gt 0) { L ("  " + $line) } }

if ($fail -eq 0) { L 'S8 RESULT: PASS' } else { L ("S8 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
