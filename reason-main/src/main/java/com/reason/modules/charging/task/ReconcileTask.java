package com.reason.modules.charging.task;

import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.dao.ChargeOrderDao;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import com.reason.modules.charging.enums.BenefitState;
import com.reason.modules.job.task.ITask;
import com.reason.modules.parking.dao.ParkOrderDao;
import com.reason.modules.parking.entity.ParkOrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 跨方对账任务（调度注册：schedule_job → reconcileTask，每小时半点）
 *
 * <p>职责（M1-1 定稿第 4 条）：凭证化协作的兜底防线——扫描三组一致性，差异即告警：</p>
 * <ol>
 *   <li><b>签发完整性</b>：电量 &gt; 0 的充电订单必须已签发权益（超时 0 电单无权益属正常，不告警）</li>
 *   <li><b>核销完整性</b>：已核销权益必须命中停车订单快照（redeem_order_id 存在、权益码一致、减免成立）</li>
 *   <li><b>快照一致性</b>：停车订单带减免必须有对应已核销权益（事务保证的强一致反向印证，异常即告警）</li>
 * </ol>
 *
 * <p>实现说明：内存 join（M1 数据量级）而非 SQL JOIN——对账逻辑集中可读、两侧原始事实一次拉取；
 * 数据量上升后改分批/游标（M4 压测评估）。本 job 直读双方表是<b>对账例外</b>：比对两侧原始事实
 * 正是对账本义（等价审计 SQL），不破坏"业务路径不直连跨域表"的纪律。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Component("reconcileTask")
public class ReconcileTask implements ITask {

    /** 单类差异最多打印条数（防日志刷屏，计数仍完整） */
    private static final int MAX_PRINT = 20;

    @Autowired
    private ChargeOrderDao chargeOrderDao;

    @Autowired
    private BenefitRecordDao benefitRecordDao;

    @Autowired
    private ParkOrderDao parkOrderDao;

    @Override
    public void run(String params) {
        long start = System.currentTimeMillis() / 1000;
        //两侧原始事实一次拉取（M1 量级内存比对）
        List<ChargeOrderEntity> chargeOrders = chargeOrderDao.selectList(null);
        List<BenefitRecordEntity> benefits = benefitRecordDao.selectList(null);
        List<ParkOrderEntity> parkOrders = parkOrderDao.selectList(null);

        Map<Long, BenefitRecordEntity> benefitBySourceOrder = benefits.stream()
                .collect(Collectors.toMap(BenefitRecordEntity::getSourceOrderId, Function.identity(), (a, b) -> a));
        Map<String, BenefitRecordEntity> benefitByNo = benefits.stream()
                .collect(Collectors.toMap(BenefitRecordEntity::getBenefitNo, Function.identity(), (a, b) -> a));
        Map<Long, ParkOrderEntity> parkOrderById = parkOrders.stream()
                .collect(Collectors.toMap(ParkOrderEntity::getOrderId, Function.identity(), (a, b) -> a));

        //①签发完整性：电量>0 的成功订单必须有权益
        List<ChargeOrderEntity> missingBenefit = chargeOrders.stream()
                .filter(o -> o.getEnergyWh() != null && o.getEnergyWh() > 0)
                .filter(o -> !benefitBySourceOrder.containsKey(o.getOrderId()))
                .collect(Collectors.toList());

        //②核销完整性：已核销权益必须命中停车订单且权益码/减免一致
        List<String> redeemMissingDiffs = new ArrayList<>();
        List<String> redeemMismatchDiffs = new ArrayList<>();
        for (BenefitRecordEntity b : benefits) {
            if (b.getBenefitState() != BenefitState.REDEEMED.getCode()) {
                continue;
            }
            ParkOrderEntity po = b.getRedeemOrderId() == null ? null : parkOrderById.get(b.getRedeemOrderId());
            if (po == null) {
                redeemMissingDiffs.add("benefitNo=" + b.getBenefitNo() + ", redeemOrderId=" + b.getRedeemOrderId()
                        + ", redeemSessionId=" + b.getRedeemSessionId());
            } else if (b.getBenefitNo() == null || !b.getBenefitNo().equals(po.getBenefitNo())
                    || po.getDiscountFen() == null || po.getDiscountFen() <= 0) {
                redeemMismatchDiffs.add("benefitNo=" + b.getBenefitNo() + ", orderId=" + po.getOrderId()
                        + ", orderBenefitNo=" + po.getBenefitNo() + ", orderDiscountFen=" + po.getDiscountFen());
            }
        }

        //③快照一致性：停车订单带减免必须有对应已核销权益
        List<String> snapshotMismatchDiffs = new ArrayList<>();
        for (ParkOrderEntity po : parkOrders) {
            if (po.getDiscountFen() == null || po.getDiscountFen() <= 0) {
                continue;
            }
            BenefitRecordEntity b = po.getBenefitNo() == null ? null : benefitByNo.get(po.getBenefitNo());
            if (b == null || b.getBenefitState() != BenefitState.REDEEMED.getCode()) {
                snapshotMismatchDiffs.add("orderId=" + po.getOrderId() + ", benefitNo=" + po.getBenefitNo()
                        + ", benefitState=" + (b == null ? null : b.getBenefitState()));
            }
        }

        //差异输出（超出上限截断打印，计数完整）
        printDiff("跨方对账①签发完整性：电量>0 订单未签发权益", missingBenefit.stream()
                .map(o -> "orderId=" + o.getOrderId() + ", plate=" + o.getPlateNo()).collect(Collectors.toList()));
        printDiff("跨方对账②核销完整性：已核销权益无停车订单", redeemMissingDiffs);
        printDiff("跨方对账②核销完整性：订单快照不匹配（权益码/减免缺失）", redeemMismatchDiffs);
        printDiff("跨方对账③快照一致性：停车订单带减免但权益非已核销", snapshotMismatchDiffs);
        log.info("跨方对账完成：充电订单 {} / 权益 {} / 停车订单 {}，差异①②③=({},{}+{},{})，耗时 {}s",
                chargeOrders.size(), benefits.size(), parkOrders.size(),
                missingBenefit.size(), redeemMissingDiffs.size(), redeemMismatchDiffs.size(), snapshotMismatchDiffs.size(),
                System.currentTimeMillis() / 1000 - start);
    }

    private void printDiff(String title, List<String> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        Set<String> shown = new HashSet<>(diffs.subList(0, Math.min(diffs.size(), MAX_PRINT)));
        log.warn("{}：共 {} 条，示例 {}", title, diffs.size(), shown);
    }
}
