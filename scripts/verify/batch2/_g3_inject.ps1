$ErrorActionPreference = "Stop"
# G3 script: injector trio on BARRIER-B-01 (single device)
#  1) reorder: two events of one command arrive reversed -> platform seq-guard rejects the late one
#  2) replay : one successfully delivered event is resent verbatim -> idempotent absorb
#  3) delay  : 2000ms (<grace) normal close loop; 5000ms (>grace) late event still accepted
# Chinese log forensics done OUTSIDE this script (PS 5.1 BOM-less UTF-8 issue).
# ASCII-only on purpose.
$main = "http://localhost:8200"
$sim = "http://127.0.0.1:8300"
$redisCli = "redis-cli"
$no = "BARRIER-B-01"
$out = New-Object System.Collections.Generic.List[string]

function Add-Line($s) { $out.Add($s) }

function Invoke-Mysql {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { & mysql -uroot -proot reason_faster @args 2>$null } finally { $ErrorActionPreference = $old }
}

# --- platform login ---
$login = $null
foreach ($i in 1..20) {
    try {
        $login = Invoke-RestMethod -Uri "$main/api/sys/login" -Method Post -ContentType "application/json" -Body '{"loginname":"adminManager","password":"admin123"}' -TimeoutSec 3
        if ($login.code -eq 0) { break }
    } catch { }
    Start-Sleep -Seconds 3
}
if ($null -eq $login -or $login.code -ne 0) { Write-Output "MAIN NOT READY"; exit 1 }
$h = @{ token = $login.data.token }
Add-Line ("MAIN READY at " + (Get-Date -Format 'HH:mm:ss'))

function Clear-RgKeys {
    $keys = & $redisCli -n 15 keys "rg:*" 2>$null
    foreach ($k in $keys) { & $redisCli -n 15 del $k | Out-Null }
}

function Send-Cmd($action) {
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

function Set-Network($body) {
    try {
        $r = Invoke-RestMethod -Uri "$sim/sim/network" -Method Post -ContentType "application/json" -Body ($body | ConvertTo-Json) -TimeoutSec 5
        Add-Line ("network set: " + ($body | ConvertTo-Json -Compress) + " -> " + $r.msg)
    } catch {
        Add-Line ("network FAILED: " + $_.Exception.Message)
    }
}

function Reset-Network {
    Set-Network @{ blockUpstream = $false; blockEvents = $false; blockDownstream = $false; dropNextEvent = $false; heartbeatDelayMillis = 0; eventDelayMillis = 0; replayNextEvent = $false; reorderDeviceNo = $null }
}

function Dump-State($tag) {
    Add-Line ("===== " + $tag + " =====")
    $rows = Invoke-Mysql -e "SELECT command_id, command_action, command_seq, trigger_type, command_status, retry_count, FROM_UNIXTIME(command_createtime) AS ct FROM device_command_log WHERE device_no='BARRIER-B-01' ORDER BY command_id DESC LIMIT 6;"
    Add-Line ($rows -join "`n")
    $rec = Invoke-Mysql -e "SELECT device_state, device_last_boot_id, device_last_event_seq FROM device_record WHERE device_no='BARRIER-B-01';"
    Add-Line ($rec -join "`n")
    $alm = Invoke-Mysql -e "SELECT COUNT(*) FROM device_alarm WHERE device_no='BARRIER-B-01' AND alarm_handled=0 AND alarm_createtime > unix_timestamp('2026-09-09 21:50:00');"
    Add-Line ("open alarms 01: " + ($alm -join ","))
}

Add-Line ("=== G3-1 reorder ===")
Reset-Network
Start-Sleep -Seconds 1
Set-Network @{ reorderDeviceNo = $no }
Start-Sleep -Seconds 1
Add-Line ("OPEN sent=" + (Send-Cmd "open"))
Start-Sleep -Seconds 10
Dump-State "G3-1 after OPEN (reorder consumed)"
Add-Line ("CLOSE sent=" + (Send-Cmd "close"))
Start-Sleep -Seconds 10
Dump-State "G3-1 after CLOSE (final)"

Add-Line ""
Add-Line ("=== G3-2 replay ===")
Reset-Network
Start-Sleep -Seconds 1
Set-Network @{ replayNextEvent = $true }
Start-Sleep -Seconds 1
Add-Line ("OPEN sent=" + (Send-Cmd "open"))
Start-Sleep -Seconds 10
Dump-State "G3-2 after OPEN (replay consumed)"
Add-Line ("CLOSE sent=" + (Send-Cmd "close"))
Start-Sleep -Seconds 10
Dump-State "G3-2 after CLOSE (back to DOWN)"

Add-Line ""
Add-Line ("=== G3-3 delay 2000 (<grace) ===")
Reset-Network
Start-Sleep -Seconds 1
Set-Network @{ eventDelayMillis = 2000 }
Start-Sleep -Seconds 1
Add-Line ("OPEN sent=" + (Send-Cmd "open"))
Start-Sleep -Seconds 15
Dump-State "G3-3a after OPEN delay=2000"

Add-Line ""
Add-Line ("=== G3-3 delay 5000 (>grace) ===")
Set-Network @{ eventDelayMillis = 5000 }
Start-Sleep -Seconds 1
Add-Line ("CLOSE sent=" + (Send-Cmd "close"))
Start-Sleep -Seconds 20
Dump-State "G3-3b after CLOSE delay=5000"

Add-Line ""
Reset-Network
Add-Line ("DONE at " + (Get-Date -Format 'HH:mm:ss'))
[System.IO.File]::WriteAllLines("c:\Users\lrs\Desktop\py\interview\_g3_out.txt", $out, [System.Text.UTF8Encoding]::new($false))
Write-Output "written _g3_out.txt"
