# _g5_s9_cors.ps1 -- batch5 S9: CORS whitelist FULL matrix (registered origin allowed / evil denied).
# Boots a temp dev instance on :8299 with allowed-origins=["http://localhost:9527"] via SPRING_APPLICATION_JSON.
# MQ / Quartz / auto-barrier disabled on the temp instance (zero interference with main :8200).
# Main instance :8200 (empty whitelist) is used as live control group.
# Evidence: _g5_s9_out.txt (UTF-8)
$ErrorActionPreference = 'Stop'
$repo = 'c:\Users\lrs\Desktop\py\interview\inteink-faster'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s9_out.txt'
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

# HTTP probe that returns status + CORS headers even on error statuses (PS5.1 HttpWebRequest)
function Probe([string]$uri, [string]$method, [string]$origin, [bool]$preflight) {
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.Method = $method
    $req.Timeout = 8000
    $req.AllowAutoRedirect = $false
    if ($origin -ne '') { $req.Headers.Add('Origin', $origin) }
    if ($preflight) { $req.Headers.Add('Access-Control-Request-Method', 'GET') }
    $status = -1; $acao = ''; $acac = ''
    try {
        $resp = $req.GetResponse()
        $status = [int]$resp.StatusCode
        $acao = [string]$resp.Headers['Access-Control-Allow-Origin']
        $acac = [string]$resp.Headers['Access-Control-Allow-Credentials']
        $resp.Close()
    } catch [System.Net.WebException] {
        $r = $_.Exception.Response
        if ($r -ne $null) {
            $status = [int]$r.StatusCode
            $acao = [string]$r.Headers['Access-Control-Allow-Origin']
            $acac = [string]$r.Headers['Access-Control-Allow-Credentials']
            $r.Close()
        }
    }
    return @{ status = $status; acao = $acao; acac = $acac }
}

$REG = 'http://localhost:9527'
$EVIL = 'http://evil.example'
$BASE = 'http://127.0.0.1:8299'
$MAIN = 'http://127.0.0.1:8200'

# ---------- boot temp instance (dev, port 8299, whitelist=[9527]) ----------
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_PORT = '8299'
$env:REASON_DEVICE_MQ_ENABLED = 'false'
$env:REASON_BARRIER_AUTO_ENABLED = 'false'
$env:SPRING_QUARTZ_AUTO_STARTUP = 'false'
$env:SPRING_APPLICATION_JSON = '{"reason":{"cors":{"allowed-origins":["http://localhost:9527"]}}}'
$out = "$env:TEMP\_g5_s9_boot_out.txt"; $err = "$env:TEMP\_g5_s9_boot_err.txt"
$p = Start-Process -FilePath 'mvn.cmd' -ArgumentList '-q','-pl','reason-main','spring-boot:run' -WorkingDirectory $repo -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
L ("temp instance pid=" + $p.Id + " (dev :8299, allowed-origins=[" + $REG + "])")
$up = $false
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    if ($p.HasExited) { break }
    try {
        $hj = Invoke-RestMethod -Uri "$BASE/api/actuator/health" -TimeoutSec 2
        if ($hj.status -eq 'UP') { $up = $true; break }
    } catch { }
}
Check "temp instance boots (health UP on 8299)" $up ("exited=" + $p.HasExited)

if (-not $up) {
    [System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), [System.Text.Encoding]::UTF8)
    Kill-Tree $p
    exit 1
}

# ---------- matrix: /api/sys/init (permitAll GET, stable 200) ----------
$a = Probe "$BASE/api/sys/init" 'GET' $REG $false
L ("A) reg  GET /api/sys/init -> status=" + $a.status + " ACAO=" + $a.acao + " ACAC=" + $a.acac)
Check "registered origin gets ACAO on real request" ($a.acao -eq $REG) ("acao=" + $a.acao)
Check "registered origin gets ACAC=true" ($a.acac -eq 'true') ("acac=" + $a.acac)

$b = Probe "$BASE/api/sys/init" 'GET' $EVIL $false
L ("B) evil GET /api/sys/init -> status=" + $b.status + " ACAO=" + $b.acao)
Check "evil origin denied (403 or no ACAO)" (($b.status -eq 403) -and ($b.acao -eq '')) ("status=" + $b.status + " acao=" + $b.acao)

$c = Probe "$BASE/api/sys/init" 'OPTIONS' $REG $true
L ("C) reg  preflight -> status=" + $c.status + " ACAO=" + $c.acao)
Check "registered preflight allowed (200/204 + ACAO)" (($c.status -eq 200 -or $c.status -eq 204) -and ($c.acao -eq $REG)) ("status=" + $c.status + " acao=" + $c.acao)

$d = Probe "$BASE/api/sys/init" 'OPTIONS' $EVIL $true
L ("D) evil preflight -> status=" + $d.status + " ACAO=" + $d.acao)
Check "evil preflight rejected (403, no ACAO)" (($d.status -eq 403) -and ($d.acao -eq '')) ("status=" + $d.status + " acao=" + $d.acao)

# ---------- side observation: token-required path (401 comes from security filter) ----------
$e = Probe "$BASE/api/sys/menu/nav" 'GET' $REG $false
L ("E) probe reg  GET /api/sys/menu/nav (needs token) -> status=" + $e.status + " ACAO=" + $e.acao + " [observation]")
$f = Probe "$BASE/api/sys/menu/nav" 'GET' $EVIL $false
L ("F) probe evil GET /api/sys/menu/nav -> status=" + $f.status + " ACAO=" + $f.acao + " [observation]")
Check "evil origin never leaks ACAO on any path" ($f.acao -eq '') ("acao=" + $f.acao)

# ---------- control group: main instance :8200 (empty whitelist) ----------
$g = Probe "$MAIN/api/sys/init" 'GET' $REG $false
L ("G) control main:8200 reg GET /api/sys/init -> status=" + $g.status + " ACAO=" + $g.acao)
Check "main instance (empty list) denies even registered origin" ($g.acao -eq '') ("acao=" + $g.acao)

$h = Probe "$MAIN/api/sys/init" 'OPTIONS' $EVIL $true
L ("H) control main:8200 evil preflight -> status=" + $h.status + " ACAO=" + $h.acao)
Check "main instance evil preflight rejected" (($h.status -eq 403) -and ($h.acao -eq '')) ("status=" + $h.status + " acao=" + $h.acao)

# ---------- teardown ----------
Kill-Tree $p
L "temp instance killed (main :8200 untouched)"

$verdict = if ($fail -eq 0) { 'PASS' } else { 'FAIL' }
L ("S9 RESULT: " + $verdict)
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), [System.Text.Encoding]::UTF8)
if ($fail -gt 0) { exit 1 }
