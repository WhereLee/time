#!/bin/bash
# M1 云上验收清单实跑（M1 验收 1-7 落点；退出码 0=全过）
# 依赖：服务已起；脚本在服务器本机执行；DB 用 sudo mysql socket
# v3 修订：造数时长避"按小时向上取整"边界（V2 回拨 6600s、V3 回拨 1800s、BN 过期回拨 90000s）；
#      A-003 不存在改 B-002 造长期占用；新增尾部清理段（浙M1 数据 + 桩/车位复位）
set -u
API="http://127.0.0.1:8200/api"
MYSQL="sudo mysql reason_faster -N -B"
PASS=$(sudo cat /opt/reason/config/main/admin-manager-password.txt)
DEVTOKEN=$(sudo grep "access-token:" /opt/reason/config/main/application-prod.yml | head -1 | sed -E 's/.*access-token:[[:space:]]*//')
FAIL=0
say()  { echo "[$1] $2"; }
chk()  { if [ "$1" = "PASS" ]; then say PASS "$2"; else say FAIL "$2"; FAIL=1; fi; }
pick() { echo "$1" | grep -oE "\"$2\":(\"[0-9]+\"|[0-9]+)" | head -1 | sed -E "s/^.*:\"?([0-9]+)\"?$/\1/"; }
now_s() { date +%s; }

echo "===== M1-V1 管理端权限闭环 ====="
LOGIN=$(curl -s -X POST $API/sys/login -H "Content-Type: application/json" -d "{\"loginname\":\"adminManager\",\"password\":\"$PASS\"}")
TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"//')
[ -n "$TOKEN" ] && chk PASS "adminManager 登录成功" || chk FAIL "登录失败"
AUTH="token: $TOKEN"
# 充电四查询 200
for ep in "charging/session/page" "charging/order/page" "charging/benefit/page" "charging/pile/page"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH" "$API/$ep")
  [ "$code" = "200" ] && chk PASS "管理端 $ep -> 200" || chk FAIL "管理端 $ep -> $code"
done
# 无 token 401
code=$(curl -s -o /dev/null -w "%{http_code}" "$API/charging/pile/page")
[ "$code" = "401" ] && chk PASS "无 token 访问充电接口 -> 401" || chk FAIL "无 token -> $code"
# DB 权限行
n=$($MYSQL -e "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 300 AND 310")
[ "$n" = "11" ] && chk PASS "role2 充电菜单授权 11 行" || chk FAIL "授权行=$n"

echo "===== M1-V2 设备通道充电闭环（直报 + DB 断言） ====="
H="Content-Type: application/json"
# 1 停车入场 B-001
S1=$(curl -s -X POST $API/device/parking/entry -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"deviceNo":"DEV-B1","spaceNo":"B-001","plateNo":"浙M1CHG01"}')
PARK_ID=$(pick "$S1" "data")
chk "$( [ -n "$PARK_ID" ] && echo PASS )" "B-001 停车入场 parkSession=$PARK_ID"
# 2 回拨停车时长 6600s（1h50m：停满后按小时向上取整收 2h，稳定避边界）
$MYSQL -e "UPDATE park_session SET session_entry_time = session_entry_time - 6600 WHERE session_id = $PARK_ID"
# 3 充电开始（锚定停车会话）
S2=$(curl -s -X POST $API/device/charging/start -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"pileNo":"PILE-001","plateNo":"浙M1CHG01"}')
CHG_ID=$(pick "$S2" "data")
chk "$( [ -n "$CHG_ID" ] && echo PASS )" "充电开始 chargeSession=$CHG_ID（锚定+车牌一致）"
# 4 充电结束 30kWh
S3=$(curl -s -X POST $API/device/charging/finish -H "$H" -H "X-Device-Token: $DEVTOKEN" -d "{\"deviceNo\":\"PILE-001\",\"sessionId\":$CHG_ID,\"energyWh\":30000}")
CHG_ORDER=$(pick "$S3" "orderId")
chk "$( [ -n "$CHG_ORDER" ] && echo PASS )" "充电结算 orderId=$CHG_ORDER"
# 5 充电订单金额断言 2400/1200/3600
AMT=$($MYSQL -e "SELECT CONCAT(elec_amount_fen,'/',service_amount_fen,'/',amount_fen) FROM charge_order WHERE order_id=$CHG_ORDER")
[ "$AMT" = "2400/1200/3600" ] && chk PASS "充电金额两段快照 2400/1200/3600" || chk FAIL "金额=$AMT"
# 6 权益签发断言
BN=$($MYSQL -e "SELECT benefit_no FROM benefit_record WHERE source_order_id=$CHG_ORDER AND benefit_state=0")
chk "$( [ -n "$BN" ] && echo PASS )" "权益已签发 $BN"
# 7 出场凭码核销
S4=$(curl -s -X POST $API/device/parking/exit -H "$H" -H "X-Device-Token: $DEVTOKEN" -d "{\"deviceNo\":\"DEV-B1\",\"spaceNo\":\"B-001\",\"sessionId\":$PARK_ID,\"benefitNo\":\"$BN\"}")
PARK_ORDER=$(pick "$S4" "orderId")
chk "$( [ -n "$PARK_ORDER" ] && echo PASS )" "出场核销 orderId=$PARK_ORDER"
# 8 停车订单减免断言：应收 400 减 200 实付 200
DIS=$($MYSQL -e "SELECT CONCAT(amount_fen,'/',discount_fen) FROM park_order WHERE order_id=$PARK_ORDER")
[ "$DIS" = "400/200" ] && chk PASS "减免快照 amount/discount=400/200" || chk FAIL "减免快照=$DIS"
# 9 权益已核销 + 回写
RB=$($MYSQL -e "SELECT CONCAT(benefit_state,'/',redeem_session_id,'/',redeem_order_id) FROM benefit_record WHERE benefit_no='$BN'")
[ "$RB" = "1/$PARK_ID/$PARK_ORDER" ] && chk PASS "权益核销回写 1/$PARK_ID/$PARK_ORDER" || chk FAIL "核销态=$RB"

echo "===== M1-V3 sim 双进程充电桩链路（sim /sim/event → main） ====="
SIM="http://127.0.0.1:8300"
# B-002 停车（main 设备通道）→ sim 桩 PILE-002 start/finish
P2=$(curl -s -X POST $API/device/parking/entry -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"deviceNo":"DEV-B2","spaceNo":"B-002","plateNo":"浙M1CHG02"}')
PARK2=$(pick "$P2" "data")
chk "$( [ -n "$PARK2" ] && echo PASS )" "B-002 停车入场 parkSession=$PARK2"
# 回拨 1800s（约停 0.5h → 取整 1h 应收 200，免停 1h 全免，区间中部稳定）
$MYSQL -e "UPDATE park_session SET session_entry_time = session_entry_time - 1800 WHERE session_id = $PARK2"
SIMSTART=$(curl -s -X POST $SIM/sim/event/PILE-002/start -d "plateNo=浙M1CHG02")
SC=$(pick "$SIMSTART" "sessionId")
chk "$( [ -n "$SC" ] && echo PASS )" "sim PILE-002 start 充电会话=$SC"
SIMFIN=$(curl -s -X POST $SIM/sim/event/PILE-002/finish -d "energyWh=20000")
SO=$(pick "$SIMFIN" "orderId")
chk "$( [ -n "$SO" ] && echo PASS )" "sim PILE-002 finish 充电订单=$SO"
AMT2=$($MYSQL -e "SELECT CONCAT(elec_amount_fen,'/',service_amount_fen,'/',amount_fen) FROM charge_order WHERE order_id=$SO")
[ "$AMT2" = "1600/800/2400" ] && chk PASS "sim 链路订单金额 1600/800/2400" || chk FAIL "金额=$AMT2"
BN2=$($MYSQL -e "SELECT benefit_no FROM benefit_record WHERE source_order_id=$SO AND benefit_state=0")
S5=$(curl -s -X POST $API/device/parking/exit -H "$H" -H "X-Device-Token: $DEVTOKEN" -d "{\"deviceNo\":\"DEV-B2\",\"spaceNo\":\"B-002\",\"sessionId\":$PARK2,\"benefitNo\":\"$BN2\"}")
PARK2O=$(pick "$S5" "orderId")
chk "$( [ -n "$PARK2O" ] && echo PASS )" "sim 链路出场核销 orderId=$PARK2O（停 1h 免 1h 全免）"
DIS2=$($MYSQL -e "SELECT CONCAT(amount_fen,'/',discount_fen) FROM park_order WHERE order_id=$PARK2O")
[ "$DIS2" = "200/200" ] && chk PASS "sim 链路减免快照 200/200（停 0.5h 免 1h 全免，实付 0）" || chk FAIL "减免快照=$DIS2"

echo "===== M1-V4 调度 job 实证（cron 统一每分钟） ====="
$MYSQL -e "UPDATE schedule_job SET job_cron='0 * * * * ?' WHERE job_status=0"
#  a. 过期权益：B-001 快充一轮造权益，expire 回拨 90000s（>24h 有效窗）确保到期
P3=$(curl -s -X POST $API/device/parking/entry -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"deviceNo":"DEV-B1","spaceNo":"B-001","plateNo":"浙M1CHG04"}')
PARK3=$(pick "$P3" "data")
chk "$( [ -n "$PARK3" ] && echo PASS )" "B-001 停车入场 parkSession=$PARK3（造过期权益）"
S6=$(curl -s -X POST $API/device/charging/start -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"pileNo":"PILE-001","plateNo":"浙M1CHG04"}')
CHG3=$(pick "$S6" "data")
S7=$(curl -s -X POST $API/device/charging/finish -H "$H" -H "X-Device-Token: $DEVTOKEN" -d "{\"deviceNo\":\"PILE-001\",\"sessionId\":$CHG3,\"energyWh\":5000}")
CO3=$(pick "$S7" "orderId")
BN3=$($MYSQL -e "SELECT benefit_no FROM benefit_record WHERE source_order_id=$CO3 AND benefit_state=0")
$MYSQL -e "UPDATE benefit_record SET expire_time = expire_time - 90000 WHERE benefit_no='$BN3'"
echo "  已造过期权益 $BN3"
#  b. 悬挂充电会话：B-003 停车 + PILE-003 start 不 finish + 开始时间回拨 3h（>2h 阈值）
S8b=$(curl -s -X POST $API/device/parking/entry -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"deviceNo":"DEV-B3","spaceNo":"B-003","plateNo":"浙M1CHG03"}')
PARKB=$(pick "$S8b" "data")
chk "$( [ -n "$PARKB" ] && echo PASS )" "B-003 停车入场 parkSession=$PARKB（造悬挂会话）"
S8=$(curl -s -X POST $API/device/charging/start -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"pileNo":"PILE-003","plateNo":"浙M1CHG03"}')
CHG4=$(pick "$S8" "data")
$MYSQL -e "UPDATE charge_session SET session_start_time = session_start_time - 10800 WHERE session_id = $CHG4"
echo "  已造悬挂充电会话 $CHG4（开始时间回拨 3h）"
#  c. 长期占用：B-002 停车回拨 4 天（A 区车位不存在，B-002 已由 V3 出场释放）
S9=$(curl -s -X POST $API/device/parking/entry -H "$H" -H "X-Device-Token: $DEVTOKEN" -d '{"deviceNo":"DEV-B2","spaceNo":"B-002","plateNo":"浙M1LONG01"}')
PARKL=$(pick "$S9" "data")
chk "$( [ -n "$PARKL" ] && echo PASS )" "B-002 长期占用停车 parkSession=$PARKL"
$MYSQL -e "UPDATE park_session SET session_entry_time = session_entry_time - 345600 WHERE session_id = $PARKL"
echo "  已造长期占用停车 $PARKL（回拨 4 天）"
echo "  等待 95s 让四 job 各跑一轮..."
sleep 95
# 断言 a：权益过期
ES=$($MYSQL -e "SELECT benefit_state FROM benefit_record WHERE benefit_no='$BN3'")
[ "$ES" = "2" ] && chk PASS "权益过期 job：$BN3 → EXPIRED(2)" || chk FAIL "权益态=$ES"
# 断言 b：超时强制结束 state=3 + 0 元订单 + 桩空闲
CS=$($MYSQL -e "SELECT session_state FROM charge_session WHERE session_id=$CHG4")
[ "$CS" = "3" ] && chk PASS "超时巡检：会话 $CHG4 → TIMEOUT(3)" || chk FAIL "会话态=$CS"
Z0=$($MYSQL -e "SELECT COUNT(*) FROM charge_order WHERE session_id=$CHG4 AND amount_fen=0 AND energy_wh=0")
[ "$Z0" = "1" ] && chk PASS "超时巡检：0 电 0 元订单生成" || chk FAIL "0 元订单数=$Z0"
PS=$($MYSQL -e "SELECT pile_state FROM charging_pile WHERE pile_no='PILE-003'")
[ "$PS" = "0" ] && chk PASS "超时巡检：桩 PILE-003 释放回空闲" || chk FAIL "桩态=$PS"
# 断言 c：长期占用告警日志
sudo journalctl -u reason-main --since "5 minutes ago" | grep -q "长期占用告警" && chk PASS "长期占用 job：告警日志命中" || chk FAIL "未见长期占用告警日志"
# 断言 d：对账与执行记录
sudo journalctl -u reason-main --since "5 minutes ago" | grep -q "跨方对账完成" && chk PASS "跨方对账 job：完成日志命中" || chk FAIL "未见对账日志"
JN=$($MYSQL -e "SELECT COUNT(*) FROM schedule_job_log WHERE log_createtime >= $(now_s) - 600 AND log_state=0")
[ "$JN" -ge "4" ] && chk PASS "schedule_job_log 本轮成功记录 ≥4（$JN）" || chk FAIL "执行记录=$JN"

echo "===== M1-V5 重启恢复 ====="
BEFORE=$($MYSQL -e "SELECT COUNT(*) FROM charge_order")
sudo systemctl restart reason-main
sleep 45
curl -sf http://127.0.0.1:8200/api/actuator/health > /dev/null && chk PASS "重启后 health OK" || chk FAIL "重启后 health 失败"
AFTER=$($MYSQL -e "SELECT COUNT(*) FROM charge_order")
[ "$BEFORE" = "$AFTER" ] && chk PASS "重启后账务数据不丢（charge_order=$AFTER）" || chk FAIL "数据变化 $BEFORE→$AFTER"
sudo journalctl -u reason-main --since "2 minutes ago" | grep -q "权益过期扫描完成" && chk PASS "重启后 Quartz job 恢复执行" || chk FAIL "重启后 job 未恢复"

echo "===== 收尾：恢复 job 默认 cron + 清理验收数据（去污染） ====="
$MYSQL -e "UPDATE schedule_job SET job_cron = CASE job_bean WHEN 'benefitExpireTask' THEN '0 * * * * ?' WHEN 'chargeSessionTimeoutTask' THEN '0 0/5 * * * ?' WHEN 'parkLongOccupancyTask' THEN '0 0 * * * ?' WHEN 'reconcileTask' THEN '0 30 * * * ?' END WHERE job_status=0"
$MYSQL -e "SELECT CONCAT(job_bean,'|',job_cron) FROM schedule_job WHERE job_status=0 ORDER BY job_id"
$MYSQL -e "DELETE FROM park_order WHERE session_id IN (SELECT session_id FROM park_session WHERE plate_no LIKE '浙M1%')"
$MYSQL -e "DELETE FROM benefit_record WHERE redeem_session_id IN (SELECT session_id FROM park_session WHERE plate_no LIKE '浙M1%') OR source_order_id IN (SELECT order_id FROM charge_order WHERE session_id IN (SELECT session_id FROM charge_session WHERE plate_no LIKE '浙M1%'))"
$MYSQL -e "DELETE FROM charge_order WHERE session_id IN (SELECT session_id FROM charge_session WHERE plate_no LIKE '浙M1%')"
$MYSQL -e "DELETE FROM charge_session WHERE plate_no LIKE '浙M1%'"
$MYSQL -e "DELETE FROM park_session WHERE plate_no LIKE '浙M1%'"
$MYSQL -e "UPDATE charging_pile SET pile_state=0 WHERE pile_no IN ('PILE-001','PILE-002','PILE-003')"
$MYSQL -e "UPDATE park_space SET space_state=0 WHERE space_no IN ('B-001','B-002','B-003')"
LEFT=$($MYSQL -e "SELECT CONCAT((SELECT COUNT(*) FROM park_session),'|',(SELECT COUNT(*) FROM charge_session),'|',(SELECT COUNT(*) FROM charge_order),'|',(SELECT COUNT(*) FROM benefit_record))")
echo "验收数据清理完成，业务表存量 park|charge|order|benefit=$LEFT"
echo "CRON-RESTORED"

echo "===== 汇总 ====="
if [ "$FAIL" = "0" ]; then echo "M1-ACCEPTED-ALL"; else echo "M1-HAS-FAILURE"; fi
exit $FAIL
