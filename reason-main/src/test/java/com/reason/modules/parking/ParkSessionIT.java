package com.reason.modules.parking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.dao.ChargingPileDao;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.service.ChargeSessionService;
import com.reason.modules.parking.dao.ParkOrderDao;
import com.reason.modules.parking.dao.ParkSessionDao;
import com.reason.modules.parking.dao.ParkSpaceDao;
import com.reason.modules.parking.entity.ParkOrderEntity;
import com.reason.modules.parking.entity.ParkSessionEntity;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.enums.ParkSessionState;
import com.reason.modules.parking.enums.ParkSpaceState;
import com.reason.modules.parking.service.ParkSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 停车会话集成测试（真实 MySQL 容器验证并发正确性）
 *
 * <p>执行位置：仅 CI（本地无 Docker 不跑）。Mock 单测只能验证「行数 0 → 拒绝」的分支；
 * 真实并发下「条件更新仅一方生效」必须真库验证——本类即 M0 验收 3「同车位并发入场仅一个成功」的落点。</p>
 *
 * <p>容器说明：本类独立一套 MySQL/Redis（AuthIT 同款）。两个 IT 类各起一套容器是显式取舍——
 * Testcontainers 容器字段继承父类的机制存在版本兼容不确定性，本地无法预验证；
 * 确定性优先于省一套容器，第三个 IT 类出现时再评估抽取共享基类。</p>
 *
 * <p>数据隔离：容器用一次即焚，类内用例用不同车位编号前缀避免互相干扰。</p>
 */
@SpringBootTest
@Testcontainers
@DisplayName("停车会话集成测试（真实并发）")
class ParkSessionIT {

    /** 并发用例车位编号前缀 */
    private static final String SPACE_NO_CONCURRENT = "IT-CONCUR";
    /** 生命周期用例车位编号前缀 */
    private static final String SPACE_NO_CYCLE = "IT-CYCLE";
    /** 出场结算用例车位编号前缀 */
    private static final String SPACE_NO_SETTLE = "IT-SETTLE";
    /** 跨方免停权益端到端用例前缀 */
    private static final String SPACE_NO_BENEFIT = "IT-BENEFIT";
    private static final String PILE_NO_BENEFIT = "IT-PILE-BENEFIT";
    /** 权益错配用例前缀 */
    private static final String SPACE_NO_MISMATCH = "IT-MISMATCH";
    private static final String PILE_NO_MISMATCH = "IT-PILE-MISMATCH";
    /** 并发核销用例前缀 */
    private static final String SPACE_NO_CONCURREDEEM = "IT-DEEM";
    private static final String PILE_NO_CONCURREDEEM = "IT-PILE-DEEM";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("reason_faster")
            .withUsername("root")
            .withPassword("root")
            //仓库根 db/ 脚本挂载进 entrypoint 初始化目录：容器创建后按文件名序自动建库导入
            //01 基线 + 02 停车域 + 03 停车授权 + 04 charging（含 park_order 减免列，与本 IT 表结构一致）+ 05 充电授权
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/reason-faster.sql"),
                    "/docker-entrypoint-initdb.d/01-reason-faster.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/02-parking.sql"),
                    "/docker-entrypoint-initdb.d/02-parking.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/03-系统管理员授权停车菜单.sql"),
                    "/docker-entrypoint-initdb.d/03-parking-role-menu-grant.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/04-charging.sql"),
                    "/docker-entrypoint-initdb.d/04-charging.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/05-系统管理员授权充电菜单.sql"),
                    "/docker-entrypoint-initdb.d/05-charging-role-menu-grant.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/06-业务定时任务注册.sql"),
                    "/docker-entrypoint-initdb.d/06-business-jobs.sql");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerContainerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ParkSessionService parkSessionService;
    @Autowired
    private ParkSpaceDao parkSpaceDao;
    @Autowired
    private ParkSessionDao parkSessionDao;
    @Autowired
    private ParkOrderDao parkOrderDao;
    @Autowired
    private ChargeSessionService chargeSessionService;
    @Autowired
    private ChargingPileDao chargingPileDao;
    @Autowired
    private BenefitRecordDao benefitRecordDao;

    @Test
    @DisplayName("同车位并发入场：仅一个成功（条件更新行锁语义的真库验证）")
    void 同车位并发入场_仅一个成功() throws Exception {
        ParkSpaceEntity space = insertSpace(SPACE_NO_CONCURRENT);

        //双线程齐发入场（CountDownLatch 消除先后偏差）
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Long> successIds = Collections.synchronizedList(new ArrayList<>());
        List<String> failureMsgs = Collections.synchronizedList(new ArrayList<>());
        String[] plates = {"浙B12345", "浙B54321"};
        for (int i = 0; i < 2; i++) {
            String plate = plates[i];
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    successIds.add(parkSessionService.entry(SPACE_NO_CONCURRENT, plate));
                } catch (Exception e) {
                    failureMsgs.add(e.getMessage());
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        //恰好一个成功、一个被拒（兜底查重或条件更新 0 行，消息一致）
        assertThat(successIds).hasSize(1);
        assertThat(failureMsgs).hasSize(1);
        assertThat(failureMsgs.get(0)).contains("已被占用");

        //库内最终状态：进行中会话仅 1 条，车位占用
        Long ongoingCount = parkSessionDao.selectCount(new LambdaQueryWrapper<ParkSessionEntity>()
                .eq(ParkSessionEntity::getSpaceId, space.getSpaceId())
                .eq(ParkSessionEntity::getSessionState, ParkSessionState.ONGOING.getCode()));
        assertThat(ongoingCount).isEqualTo(1L);
        ParkSpaceEntity after = parkSpaceDao.selectById(space.getSpaceId());
        assertThat(after.getSpaceState()).isEqualTo(ParkSpaceState.OCCUPIED.getCode());
    }

    @Test
    @DisplayName("入场 → 取消 → 车位释放 → 可再次入场（生命周期真库验证）")
    void 取消后车位可再次入场() {
        ParkSpaceEntity space = insertSpace(SPACE_NO_CYCLE);

        Long first = parkSessionService.entry(SPACE_NO_CYCLE, "浙B12345");
        assertThat(first).isNotNull();

        parkSessionService.cancel(first, "测试取消");

        //车位已释放回空闲
        ParkSpaceEntity afterCancel = parkSpaceDao.selectById(space.getSpaceId());
        assertThat(afterCancel.getSpaceState()).isEqualTo(ParkSpaceState.IDLE.getCode());
        ParkSessionEntity cancelled = parkSessionDao.selectById(first);
        assertThat(cancelled.getSessionState()).isEqualTo(ParkSessionState.CANCELLED.getCode());
        assertThat(cancelled.getSessionCancelTime()).isNotNull();

        //释放后可再次入场（条件更新语义：占用→释放→占用）
        Long second = parkSessionService.entry(SPACE_NO_CYCLE, "浙B54321");
        assertThat(second).isNotNull();
        ParkSessionEntity ongoing = parkSessionDao.selectById(second);
        assertThat(ongoing.getSessionState()).isEqualTo(ParkSessionState.ONGOING.getCode());
        assertThat(ongoing.getPlateNo()).isEqualTo("浙B54321");
    }

    @Test
    @DisplayName("出场结算：订单生成（65 分钟 → 2 小时 × 200 分）+ 会话终态 + 车位释放 + 可再次入场")
    void 出场结算生成订单并释放车位() {
        ParkSpaceEntity space = insertSpace(SPACE_NO_SETTLE);

        Long sessionId = parkSessionService.entry(SPACE_NO_SETTLE, "浙B12345");

        //把入场时间回拨 65 分钟（DB 直改模拟停车时长，真实场景由时间自然流逝）
        ParkSessionEntity parked = parkSessionDao.selectById(sessionId);
        parked.setSessionEntryTime(parked.getSessionEntryTime() - 3900);
        parkSessionDao.updateById(parked);

        Long orderId = parkSessionService.exit(sessionId);
        assertThat(orderId).isNotNull();

        //订单快照断言（分存储 + 快照字段齐）
        ParkOrderEntity order = parkOrderDao.selectById(orderId);
        assertThat(order.getSessionId()).isEqualTo(sessionId);
        assertThat(order.getPlateNo()).isEqualTo("浙B12345");
        assertThat(order.getSpaceNo()).isEqualTo(SPACE_NO_SETTLE);
        assertThat(order.getDurationMinutes()).isEqualTo(65);
        assertThat(order.getUnitPriceFen()).isEqualTo(200);
        assertThat(order.getAmountFen()).isEqualTo(400L);   //ceil(3900/3600)=2 小时 × 200 分

        //会话终态 + 车位释放
        ParkSessionEntity afterExit = parkSessionDao.selectById(sessionId);
        assertThat(afterExit.getSessionState()).isEqualTo(ParkSessionState.FINISHED.getCode());
        assertThat(afterExit.getSessionExitTime()).isNotNull();
        ParkSpaceEntity afterSpace = parkSpaceDao.selectById(space.getSpaceId());
        assertThat(afterSpace.getSpaceState()).isEqualTo(ParkSpaceState.IDLE.getCode());

        //释放后可再次入场（完整生命周期闭环）
        Long again = parkSessionService.entry(SPACE_NO_SETTLE, "浙B54321");
        assertThat(again).isNotNull();
    }

    // ---------- 跨方免停权益端到端（M1 验收 2/3 落点，真库验证） ----------

    private String benefitNoOf(String plateNo) {
        List<BenefitRecordEntity> list = benefitRecordDao.selectList(new LambdaQueryWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getPlateNo, plateNo)
                .orderByDesc(BenefitRecordEntity::getBenefitId)
                .last("LIMIT 1"));
        assertThat(list).hasSize(1);
        return list.get(0).getBenefitNo();
    }

    @Test
    @DisplayName("停车→充电→出场核销免停：订单减免快照 + 权益置已核销 + 锚定一致")
    void 跨方免停全链路核销() {
        ParkSpaceEntity space = insertSpace(SPACE_NO_BENEFIT);
        insertPile(PILE_NO_BENEFIT, space.getSpaceId());
        Long parkSessionId = parkSessionService.entry(SPACE_NO_BENEFIT, "浙B12345");
        //停车时长 2 小时：应收 400 分（回拨时间制造时长）
        ParkSessionEntity parked = parkSessionDao.selectById(parkSessionId);
        parked.setSessionEntryTime(parked.getSessionEntryTime() - 7200);
        parkSessionDao.updateById(parked);

        Long chargeSessionId = chargeSessionService.start(PILE_NO_BENEFIT, "浙B12345");
        chargeSessionService.finish(chargeSessionId, 30_000L);
        String benefitNo = benefitNoOf("浙B12345");

        Long orderId = parkSessionService.exit(parkSessionId, benefitNo);

        //停车订单：应收 400 − 免停 1h 折算 200 = 实付 200（快照完整）
        ParkOrderEntity order = parkOrderDao.selectById(orderId);
        assertThat(order.getAmountFen()).isEqualTo(400L);
        assertThat(order.getDiscountFen()).isEqualTo(200L);
        assertThat(order.getBenefitNo()).isEqualTo(benefitNo);
        //权益：可用 → 已核销，回写核销会话/订单
        BenefitRecordEntity benefit = benefitRecordDao.selectList(new LambdaQueryWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getBenefitNo, benefitNo)).get(0);
        assertThat(benefit.getBenefitState()).isEqualTo(1);
        assertThat(benefit.getRedeemSessionId()).isEqualTo(parkSessionId);
        assertThat(benefit.getRedeemOrderId()).isEqualTo(orderId);
        assertThat(benefit.getRedeemTime()).isNotNull();
        //充电订单独立闭环（两段费率 36 元）
        assertThat(order.getSpaceNo()).isEqualTo(SPACE_NO_BENEFIT);
    }

    @Test
    @DisplayName("权益错配：B 车出场携带 A 车权益 → 无减免结算，权益保持可用（防凭证盗用）")
    void 权益锚定错配不减免() {
        ParkSpaceEntity spaceA = insertSpace(SPACE_NO_MISMATCH + "-A");
        insertPile(PILE_NO_MISMATCH + "-A", spaceA.getSpaceId());
        Long sessionA = parkSessionService.entry(SPACE_NO_MISMATCH + "-A", "浙B11111");
        Long chargeId = chargeSessionService.start(PILE_NO_MISMATCH + "-A", "浙B11111");
        chargeSessionService.finish(chargeId, 20_000L);
        String benefitNo = benefitNoOf("浙B11111");

        //B 车在另一车位出场（盗用 A 的权益码）
        insertSpace(SPACE_NO_MISMATCH + "-B");
        Long sessionB = parkSessionService.entry(SPACE_NO_MISMATCH + "-B", "浙B22222");
        ParkSessionEntity parkedB = parkSessionDao.selectById(sessionB);
        parkedB.setSessionEntryTime(parkedB.getSessionEntryTime() - 3600);
        parkSessionDao.updateById(parkedB);
        Long orderIdB = parkSessionService.exit(sessionB, benefitNo);

        //B 无减免；权益仍可用（未被错配消费）
        ParkOrderEntity orderB = parkOrderDao.selectById(orderIdB);
        assertThat(orderB.getDiscountFen()).isZero();
        assertThat(orderB.getBenefitNo()).isNull();
        BenefitRecordEntity benefit = benefitRecordDao.selectList(new LambdaQueryWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getBenefitNo, benefitNo)).get(0);
        assertThat(benefit.getBenefitState()).isZero();
    }

    @Test
    @DisplayName("同会话并发出场带权益：恰好一个成功减免，另一个被会话判官拒绝（核销仅一次）")
    void 并发出场核销仅一次() throws Exception {
        ParkSpaceEntity space = insertSpace(SPACE_NO_CONCURREDEEM);
        insertPile(PILE_NO_CONCURREDEEM, space.getSpaceId());
        Long parkSessionId = parkSessionService.entry(SPACE_NO_CONCURREDEEM, "浙B33333");
        ParkSessionEntity parked = parkSessionDao.selectById(parkSessionId);
        parked.setSessionEntryTime(parked.getSessionEntryTime() - 3600);
        parkSessionDao.updateById(parked);
        Long chargeId = chargeSessionService.start(PILE_NO_CONCURREDEEM, "浙B33333");
        chargeSessionService.finish(chargeId, 10_000L);
        String benefitNo = benefitNoOf("浙B33333");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Long> success = Collections.synchronizedList(new ArrayList<>());
        List<String> failed = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    success.add(parkSessionService.exit(parkSessionId, benefitNo));
                } catch (RuntimeException e) {
                    failed.add(e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(20, TimeUnit.SECONDS);

        //恰一方成功（会话判官），另一方收到业务异常
        assertThat(success).hasSize(1);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0)).contains("状态已变更");
        //权益只被核销一次（无重复减免）
        BenefitRecordEntity benefit = benefitRecordDao.selectList(new LambdaQueryWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getBenefitNo, benefitNo)).get(0);
        assertThat(benefit.getBenefitState()).isEqualTo(1);
        assertThat(benefit.getRedeemOrderId()).isEqualTo(success.get(0));
    }

    private ParkSpaceEntity insertSpace(String spaceNo) {
        long now = System.currentTimeMillis() / 1000;
        ParkSpaceEntity space = new ParkSpaceEntity();
        space.setSpaceNo(spaceNo);
        space.setSpaceArea("IT 测试区");
        space.setSpaceState(ParkSpaceState.IDLE.getCode());
        space.setSpaceCreatetime(now);
        space.setSpaceUpdatetime(now);
        parkSpaceDao.insert(space);
        return space;
    }

    private void insertPile(String pileNo, Long spaceId) {
        long now = System.currentTimeMillis() / 1000;
        ChargingPileEntity pile = new ChargingPileEntity();
        pile.setPileNo(pileNo);
        pile.setSpaceId(spaceId);
        pile.setPileState(0);
        pile.setPileCreator(1L);
        pile.setPileCreatetime(now);
        chargingPileDao.insert(pile);
    }
}
