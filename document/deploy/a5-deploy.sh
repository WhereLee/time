#!/bin/bash
# A5 云部署：A 块设备域全量落地（08 资产重设 + 09 巡检 job + 10 设备管理菜单/留痕表 + 双 jar 升级）
# 语义：停车资产从 M1 演示 3 车位升级为商超 300 车位形态（删旧建新，历史演示数据随资产重设清场）
set -e
echo "== [1/5] stop services =="
sudo systemctl stop reason-main reason-device-sim || true
sleep 2
echo "== [2/5] import sql (08-10) =="
cd /tmp/a5
for f in 08-*.sql 09-*.sql 10-*.sql; do
  echo "  import $f"
  sudo mysql reason_faster --default-character-set=utf8mb4 < "$f"
done
echo "  asset counts (两遍幂等已本地验，此处单遍导入后计数):"
sudo mysql reason_faster -N -B -e "SELECT 'park_space',COUNT(*) FROM park_space; SELECT 'charging_pile',COUNT(*) FROM charging_pile; SELECT 'device_online',COUNT(*) FROM device_online; SELECT 'device_scan_job',COUNT(*) FROM schedule_job WHERE job_bean='deviceOnlineScanTask'; SELECT 'menu_400_405',COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 400 AND 405; SELECT 'role2_menu',COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 400 AND 405; SELECT 'gate_manual_op_tbl',COUNT(*) FROM information_schema.tables WHERE table_schema='reason_faster' AND table_name='gate_manual_op';"
echo "== [3/5] install jars =="
sudo cp -f /tmp/a5/reason-main-1.0.0.jar /opt/reason/main/
sudo cp -f /tmp/a5/reason-device-sim.jar /opt/reason/sim/
echo "== [4/5] start services =="
sudo systemctl start reason-main reason-device-sim
echo "== [5/5] health wait =="
ok=0
for i in $(seq 1 12); do
  sleep 10
  m=$(curl -s http://127.0.0.1:8200/api/actuator/health | grep -o '"status":"[A-Z]*"' | head -1)
  s=$(curl -s http://127.0.0.1:8300/actuator/health | grep -o '"status":"[A-Z]*"' | head -1)
  if echo "$m" | grep -q UP && echo "$s" | grep -q UP; then ok=1; echo "  both-UP at round $i"; break; fi
  echo "  wait round $i: main=$m sim=$s"
done
[ "$ok" = 1 ] || { echo "HEALTH-FAIL"; exit 1; }
echo "job list:"
sudo journalctl -u reason-main --since "1 min ago" | grep -o "scheduleJobList:[0-9]*" | tail -1
echo "DEPLOY-DONE"
