$out = "c:\Users\lrs\Desktop\py\interview\_g2_precheck_out.txt"
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("=== precheck $(Get-Date -Format 'HH:mm:ss') ===")
$lines.Add("--- platform consumer errors (last 300 lines of error.log, 21:4x) ---")
$c = (Get-Content "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-main\logs\error.log" -Encoding UTF8 -Tail 300 | Select-String "21:4[3-9]:.*ensureConsumer" | Measure-Object).Count
$lines.Add("count=$c")
$lines.Add("--- device states ---")
$lines.Add((mysql -uroot -proot reason_faster -N -e "SELECT CONCAT(device_state, ':', COUNT(*)) FROM device_record WHERE device_no LIKE 'BARRIER-B-%' GROUP BY device_state;" 2>$null) -join ";")
$lines.Add("--- open alarms ---")
$lines.Add((mysql -uroot -proot reason_faster -N -e "SELECT COUNT(*) FROM device_alarm WHERE device_no LIKE 'BARRIER-B-%' AND alarm_handled=0;" 2>$null))
$lines.Add("--- online keys ---")
$lines.Add((redis-cli -n 15 keys "barrier:online:*" | Measure-Object -Line | Select-Object -ExpandProperty Lines))
$lines.Add("--- GETDEL WARN after 21:40 ---")
$g = (Get-Content "c:\Users\lrs\Desktop\py\interview\inteink-faster\reason-main\logs\warn.log" -Encoding UTF8 -Tail 400 | Select-String "21:4[0-9]:.*GETDEL" | Measure-Object).Count
$lines.Add("count=$g")
[System.IO.File]::WriteAllLines($out, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "written $out"
