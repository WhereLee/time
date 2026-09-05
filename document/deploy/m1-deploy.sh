#!/bin/bash
# M1 云部署：导 SQL + 换 jar + 重启双服务
set -e
echo "== [1/4] stop services =="
sudo systemctl stop reason-main reason-device-sim || true
echo "== [2/4] import sql (04-07) =="
cd /tmp/m1
for f in 04-charging.sql 05-charging-role-menu-grant.sql 06-business-jobs.sql 07-charging-demo-data.sql; do
  echo "  import $f"
  sudo mysql reason_faster --default-character-set=utf8mb4 < "$f"
done
sudo mysql reason_faster -N -B -e "SELECT COUNT(*) FROM charging_pile; SELECT COUNT(*) FROM schedule_job WHERE job_status=0; SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 300 AND 310;"
echo "== [3/4] install jars =="
sudo cp -f /tmp/m1/reason-main-1.0.0.jar /opt/reason/main/
sudo cp -f /tmp/m1/reason-device-sim.jar /opt/reason/sim/
echo "== [4/4] start services =="
sudo systemctl start reason-main reason-device-sim
sleep 45
curl -sf http://127.0.0.1:8200/api/actuator/health > /dev/null && echo "MAIN-HEALTH-OK" || echo "MAIN-HEALTH-FAIL"
curl -sf http://127.0.0.1:8300/actuator/health > /dev/null && echo "SIM-HEALTH-OK" || echo "SIM-HEALTH-FAIL"
echo "DEPLOY-DONE"
