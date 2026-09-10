# _g5_s1.ps1 -- batch5 S1: config security scan (static, no runtime dependency)
# Asserts: no ghost whitelist paths / no default active profile / CORS not wildcard /
#          prod credentials ENC-only / no retired access-token / jasypt config present.
# NOTE: assertions are comment-aware -- ghost paths are checked against the ANON_PATHS
#       array entries only (javadoc/doc-comments legitimately mention removed names);
#       java checks strip FULL-LINE comments only (never regex-strip /*..*/ across code,
#       string literals like "/**" would pair with a later */ and eat real code).
# All output ASCII; evidence written UTF-8 to _g5_s1_out.txt.
$ErrorActionPreference = 'Stop'
$repo = 'c:\Users\lrs\Desktop\py\interview\inteink-faster'
$outPath = 'c:\Users\lrs\Desktop\py\interview\_g5_s1_out.txt'
$log = New-Object System.Collections.Generic.List[string]
$fail = 0

function L([string]$m) { $log.Add($m); Write-Host $m }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { L ("PASS: " + $name) } else { L ("FAIL: " + $name + " | " + $detail); $script:fail++ }
}
function Strip-Yaml([string]$t) { return [regex]::Replace($t, '(?m)^\s*#.*$', '') }
function Strip-Xml([string]$t) { return [regex]::Replace($t, '(?s)<!--.*?-->', '') }
function Strip-CommentLines([string]$t) {
    # remove whole-line comments only: //... / /*... / *... javadoc interior / closing */
    # (no cross-code regex pairing -- strings like "/**" would eat real code)
    $t = [regex]::Replace($t, '(?m)^\s*(//|/\*|\*).*$', '')
    $t = [regex]::Replace($t, '(?m)^\s*\*/\s*$', '')
    return $t
}

$secRaw   = [System.IO.File]::ReadAllText("$repo\reason-main\src\main\java\com\reason\config\SecurityConfig.java")
$appYmlC  = Strip-Yaml ([System.IO.File]::ReadAllText("$repo\reason-main\src\main\resources\application.yml"))
$prodYmlC = Strip-Yaml ([System.IO.File]::ReadAllText("$repo\reason-main\src\main\resources\application-prod.yml"))
$corsCode = Strip-CommentLines ([System.IO.File]::ReadAllText("$repo\reason-main\src\main\java\com\reason\config\CorsConfig.java"))
$corsProp = [System.IO.File]::ReadAllText("$repo\reason-main\src\main\java\com\reason\config\CorsProperties.java")

# --- extract ANON_PATHS entries (quoted strings in array body; comments inside are skipped naturally) ---
$arrMatch = [regex]::Match($secRaw, 'ANON_PATHS\s*=\s*\{([\s\S]*?)\n\s*\};')
$entries = @()
if ($arrMatch.Success) {
    $entries = @([regex]::Matches($arrMatch.Groups[1].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
}
L ("whitelist entries found (" + $entries.Count + "): " + ($entries -join ', '))
Check "whitelist parsed (>=10 entries)" ($entries.Count -ge 10) ("parsed=" + $entries.Count)

# --- 1. ghost whitelist paths must not be entries (T16) ---
$ghosts = @('/druid/**', '/swagger-resources/**', '/v2/api-docs', '/sys/code', '/sys/auth', '/test/**', '/aaa.txt', '/app/**', '/etc/test/**')
foreach ($g in $ghosts) {
    Check ("ghost-path not in whitelist: $g") ($entries -notcontains $g) "still whitelisted"
}

# --- 2. core whitelist entries still present ---
$keepers = @('/sys/login', '/sys/init', '/device/event', '/device/heartbeat', '/actuator/health', '/actuator/info', '/captcha.jpg')
foreach ($k in $keepers) {
    Check ("whitelist keeps: $k") ($entries -contains $k) "entry missing"
}

# --- 3. no default active profile in application.yml (T16 fail-fast) ---
$activeLines = @($appYmlC -split "`n" | Where-Object { $_ -match '^\s*active:\s*\S+' })
Check "no spring.profiles.active default" ($activeLines.Count -eq 0) ("found: " + ($activeLines -join '; '))

# --- 4. CORS: config-driven allowedOrigins, empty by default ---
Check "application.yml cors allowed-origins empty default" ($appYmlC -match 'allowed-origins:\s*\[\]') "not literal []"
Check "CorsConfig code uses allowedOrigins( only (comment lines stripped)" (($corsCode -match 'allowedOrigins\(') -and ($corsCode -notmatch 'allowedOriginPatterns\(')) "wrong API used in code"
Check "CorsProperties default empty list" ($corsProp -match 'new ArrayList') "default may be wildcard"

# --- 5. prod credentials ENC-only, no retired access-token in live config ---
$userLine = (@($prodYmlC -split "`n" | Where-Object { $_ -match '^\s*username:\s*' }) -join '')
$passLine = (@($prodYmlC -split "`n" | Where-Object { $_ -match '^\s*password:\s*\S' }) -join '')
Check "prod username is ENC(...)" ($userLine -match 'ENC\(') ("line: " + $userLine.Trim())
Check "prod password is ENC(...)" ($passLine -match 'ENC\(') ("line: " + $passLine.Trim())
Check "prod has no plaintext root password" ($prodYmlC -notmatch 'password:\s*root\b') "plaintext root found"

$resLive = New-Object System.Collections.Generic.List[string]
foreach ($f in (Get-ChildItem "$repo\reason-main\src\main\resources" -File -Recurse)) {
    if ($f.Name -eq 'application.yml' -or $f.Name -eq 'application-prod.yml') { continue }
    $t = [System.IO.File]::ReadAllText($f.FullName)
    if ($f.Extension -eq '.yml' -or $f.Extension -eq '.yaml') { $t = Strip-Yaml $t }
    elseif ($f.Extension -eq '.xml') { $t = Strip-Xml $t }
    $resLive.Add($t)
}
Check "no retired access-token in live resources (excl. prod-yml/comment doc)" (((($resLive) -join "`n")) -notmatch 'access-token') "access-token still referenced in live config"

# --- 6. jasypt closed loop config present ---
Check "jasypt encryptor algorithm configured" ($appYmlC -match 'PBEWITHHMACSHA512ANDAES_256') "algorithm missing"

# --- 7. whitelist entry vs controller mapping existence (machine cross-check, warn-level) ---
$javaAll = ''
foreach ($jf in (Get-ChildItem "$repo\reason-main\src\main\java" -Recurse -Filter *.java)) {
    $javaAll += [System.IO.File]::ReadAllText($jf.FullName)
}
$frameworkKnown = @('/actuator/health', '/actuator/info', '/error', '/doc.html', '/v3/api-docs')
foreach ($e in $entries) {
    if ($frameworkKnown -contains $e -or $e -match '\*$') { L ("SKIP: $e (framework/pattern entry)"); continue }
    $plain = $e.TrimStart('/')
    $found = ($javaAll -match [regex]::Escape('"' + $plain + '"')) -or ($javaAll -match [regex]::Escape('"/' + $plain + '"'))
    if ($found) { L ("PASS: whitelist entry maps to controller: $e") } else { L ("WARN: no mapping annotation literally found for: $e") }
}

if ($fail -eq 0) { L 'S1 RESULT: PASS' } else { L ("S1 RESULT: FAIL (" + $fail + " check(s) failed)") }
[System.IO.File]::WriteAllText($outPath, ($log -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
exit $fail
