package com.reason.modules.parking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
                    "/docker-entrypoint-initdb.d/05-charging-role-menu-grant.sql");

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
}
