#!/bin/bash
# A5 云上验收（A 块设备域验收 1-6 全景）：资产规模 / 台账分页 / 心跳在线与刷新 / 故障注入恢复 / 手动抬杆闭环 / 巡检超时裁决
# 运行位置：服务器本机（DB 走 sudo mysql socket）；退出码 0=全部通过
set -u
API="http://127.0.0.1:8200/api"
MYSQL="sudo mysql reason_faster -N -B"
PASS=$(sudo cat /opt/reason/config/main/admin-manager-password.txt)
FAIL=0
say()  { echo "[$1] $2"; }
chk()  { if [ "$1" = "PASS" ]; then say PASS "$2"; else say FAIL "$2"; FAIL=1; fi; }
pick() { echo "$1" | grep -oE "\"$2\":(\"[0-9]+\"|[0-9]+)" | head -1 | sed -E "s/^.*:\"?([0-9]+)\"?$/\1/"; }
now_s() { date +%s; }

echo "===== A5-V1 资产规模（DB 事实） ====="
SP=$($MYSQL -e "SELECT COUNT(*) FROM park_space")
CP=$($MYSQL -e "SELECT COUNT(*) FROM charging_pile")
DO=$($MYSQL -e "SELECT COUNT(*) FROM device_online")
JB=$($MYSQL -e "SELECT COUNT(*) FROM schedule_job WHERE job_bean='deviceOnlineScanTask' AND job_status=0")
MN=$($MYSQL -e "SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 400 AND 405")
RM=$($MYSQL -e "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 400 AND 405")
[ "$SP" = "300" ] && chk PASS "车位资产 300（A/B/C 商超形态）" || chk FAIL "车位资产=$SP"
[ "$CP" = "30" ]  && chk PASS "充电桩 30（绑 C 区）" || chk FAIL "充电桩=$CP"
[ "$DO" = "336" ] && chk PASS "设备台账 336（闸机6+位检300+桩30）" || chk FAIL "设备台账=$DO"
[ "$JB" = "1" ]   && chk PASS "巡检 job 注册生效" || chk FAIL "巡检 job=$JB"
[ "$MN" = "6" -a "$RM" = "6" ] && chk PASS "设备菜单 400-405 + 管理员授权" || chk FAIL "菜单=$MN 授权=$RM"

echo "===== A5-V2 台账分页（HTTP） ====="
LOGIN=$(curl -s -X POST $API/sys/login -H "Content-Type: application/json" -d "{\"loginname\":\"adminManager\",\"password\":\"$PASS\"}")
TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"//')
[ -n "$TOKEN" ] && chk PASS "adminManager 登录" || chk FAIL "登录失败"
AUTH="token: $TOKEN"
PAGE=$(curl -s "$API/parking/device/page?page=1&limit=100" -H "$AUTH")
TC=$(pick "$PAGE" totalCount)
[ "$TC" = "336" ] && chk PASS "台账分页总数 336" || chk FAIL "台账总数=$TC"
PG=$(curl -s "$API/parking/device/page?page=1&limit=100&deviceType=2" -H "$AUTH")
[ "$(pick "$PG" totalCount)" = "300" ] && chk PASS "位检类型筛 300" || chk FAIL "位检筛=$(pick "$PG" totalCount)"
PZ=$(curl -s "$API/parking/device/page?page=1&limit=100&deviceType=3" -H "$AUTH")
[ "$(pick "$PZ" totalCount)" = "30" ] && chk PASS "充电桩类型筛 30" || chk FAIL "桩筛=$(pick "$PZ" totalCount)"
N=$(curl -s -o /dev/null -w "%{http_code}" "$API/parking/device/page?page=1" )
[ "$N" = "401" ] && chk PASS "无 token 台账 -> 401" || chk FAIL "无 token -> $N"

echo "===== A5-V3 心跳在线与刷新 ====="
T1=$($MYSQL -e "SELECT COUNT(*) FROM device_online WHERE device_state=1")
M1=$($MYSQL -e "SELECT MAX(last_heartbeat) FROM device_online")
[ "$T1" = "336" ] && chk PASS "心跳上线 336/336" || chk FAIL "在线=$T1"
sleep 12
T2=$($MYSQL -e "SELECT COUNT(*) FROM device_online WHERE device_state=1")
M2=$($MYSQL -e "SELECT MAX(last_heartbeat) FROM device_online")
[ "$T2" = "336" ] && chk PASS "12s 后仍 336 在线" || chk FAIL "在线=$T2"
[ "$M2" -gt "$M1" ] && chk PASS "last_heartbeat 持续刷新（$M1→$M2）" || chk FAIL "心跳未刷新 $M1/$M2"

echo "===== A5-V4 故障注入与恢复 ====="
DEV="SENSOR-B-050"
curl -s -X POST "http://127.0.0.1:8300/sim/device/offline/$DEV" > /dev/null
sleep 13
S1=$($MYSQL -e "SELECT device_state FROM device_online WHERE device_no='$DEV'")
[ "$S1" = "0" ] && chk PASS "注入离线：$DEV state=0" || chk FAIL "$DEV state=$S1"
curl -s -X POST "http://127.0.0.1:8300/sim/device/online/$DEV" > /dev/null
sleep 13
S2=$($MYSQL -e "SELECT device_state FROM device_online WHERE device_no='$DEV'")
[ "$S2" = "1" ] && chk PASS "恢复在线：$DEV state=1（心跳时刻刷新）" || chk FAIL "$DEV state=$S2"

echo "===== A5-V5 手动抬杆闭环 ====="
LIFT=$(curl -s -X POST "$API/parking/device/lift" -H "$AUTH" -H "Content-Type: application/json" -d '{"deviceNo":"GATE-S-OUT","plateNo":"A5TEST01","reason":"A5 验收：出口闸机故障人工放行"}')
echo "$LIFT" | grep -q '"success":true' && chk PASS "抬杆下发 success=true" || chk FAIL "抬杆返回：$LIFT"
sleep 2
GL=$(curl -s "$API/parking/device/gatelog/page?page=1&limit=10&deviceNo=GATE-S-OUT" -H "$AUTH")
R1=$(echo "$GL" | grep -o '"opResult":[0-9]' | head -1 | grep -o '[0-9]$')
[ "$R1" = "0" ] && chk PASS "留痕落库 op_result=0" || chk FAIL "留痕结果=$R1"
echo "$GL" | grep -q 'A5TEST01' && chk PASS "留痕含人工录入车牌 A5TEST01" || chk FAIL "留痕无车牌"
echo "$GL" | grep -q 'adminManager' && chk PASS "留痕含操作人 adminManager" || chk FAIL "留痕无操作人"
sudo journalctl -u reason-device-sim --since "3 min ago" | grep -q "OPEN_GATE.*GATE-S-OUT" && chk PASS "sim 侧执行痕迹 OPEN_GATE/GATE-S-OUT" || chk FAIL "sim 日志无执行痕迹"
BAD=$(curl -s -X POST "$API/parking/device/lift" -H "$AUTH" -H "Content-Type: application/json" -d '{"deviceNo":"SENSOR-A-001","reason":"测试"}')
echo "$BAD" | grep -q '非闸机设备' && chk PASS "非闸机抬杆拒绝" || chk FAIL "位检未拒绝：$BAD"
BADE=$(curl -s -X POST "$API/parking/device/lift" -H "$AUTH" -H "Content-Type: application/json" -d '{"deviceNo":"GATE-S-OUT","reason":""}')
echo "$BADE" | grep -q '原因' && chk PASS "空原因拒绝（审计必录）" || chk FAIL "空原因未拒：$BADE"

echo "===== A5-V6 巡检超时裁决（停 sim 45s） ====="
sudo systemctl stop reason-device-sim
sleep 45
O3=$($MYSQL -e "SELECT COUNT(*) FROM device_online WHERE device_state=1")
[ "$O3" = "0" ] && chk PASS "停 sim 45s 后巡检裁决 336 全离线" || chk FAIL "停 sim 后仍在线=$O3"
sudo systemctl start reason-device-sim
sleep 35
O4=$($MYSQL -e "SELECT COUNT(*) FROM device_online WHERE device_state=1")
[ "$O4" = "336" ] && chk PASS "sim 恢复后心跳重新上线 336/336" || chk FAIL "恢复后在线=$O4"
H4=$(curl -s http://127.0.0.1:8300/actuator/health | grep -o '"status":"UP"' | head -1)
[ -n "$H4" ] && chk PASS "sim 服务健康恢复" || chk FAIL "sim health 异常"

echo "===== A5 汇总 ====="
[ "$FAIL" = "0" ] && echo "A5-ALL-PASS" || echo "A5-HAS-FAIL"
exit $FAIL
