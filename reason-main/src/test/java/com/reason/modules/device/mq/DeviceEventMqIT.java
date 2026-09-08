package com.reason.modules.device.mq;

import com.alibaba.fastjson2.JSON;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.reason.modules.device.config.DeviceSignature;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备事件 MQ 通道集成测试（阶段2，F2——真实 RocketMQ 容器，端到端消费语义）
 *
 * <p>执行位置：仅 CI（GitHub Actions 独立 mq-it job，-Dgroups=mq）——本机无 Docker 时由
 * 命名约定 *IT 天然隔离（surefire 只匹配 *Test，failsafe 只匹配 *IT）。</p>
 *
 * <p>容器链：MySQL（挂 db/01-05 全量脚本）+ Redis + RocketMQ namesrv + broker（--enable-proxy
 * 内嵌 gRPC 8081，两容器共享 Network，broker 经容器别名 namesrv 解析）。</p>
 *
 * <p>验收映射（规格 F2）：①真事件闭环（发送→消费→台账/流水终态）②重复投递幂等（业务序守卫吸收）
 * ③验签失败毒消息不重投 ④积压续消费（平台 context 起前投递，consumer 连上后全部消费——
 * "平台重启窗口零丢失"的等价实证：消息在 broker 持久化，消费端后到不丢）。</p>
 *
 * <p>防干扰：reason.barrier.auto-enabled=false（禁 AutoTask 校正 IT 设备）+
 * command-timeout-seconds=3600（禁 MonitorTask 超时对账动 IT 流水）。</p>
 */
@SpringBootTest
@Testcontainers
@Tag("mq")
@DisplayName("设备事件MQ通道集成测试(阶段2)")
class DeviceEventMqIT {

    /** IT 设备（用例①②③共用，事件序号单调编排） */
    private static final String DEVICE_NO = "BARRIER-IT-01";
    /** IT 设备2（用例④积压续消费——@BeforeAll 在 Spring context 起前投递） */
    private static final String DEVICE_NO_BACKLOG = "BARRIER-IT-02";
    private static final String SECRET = "it-secret-0123456789abcdef0123456789abcdef";
    private static final String TOPIC = "device-event";

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("reason_faster")
            .withUsername("root")
            .withPassword("root")
            //挂全量 db 脚本（按文件名序执行）：01 壳子基线 + 02 台账 + 03 流水/告警 + 04/05 协议 v2 列
            .withCopyFileToContainer(MountableFile.forHostPath("../db/reason-faster.sql"),
                    "/docker-entrypoint-initdb.d/01-reason-faster.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/02-barrier-device-record.sql"),
                    "/docker-entrypoint-initdb.d/02-barrier-device-record.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/03-barrier-full-loop.sql"),
                    "/docker-entrypoint-initdb.d/03-barrier-full-loop.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/04-barrier-protocol-v2.sql"),
                    "/docker-entrypoint-initdb.d/04-barrier-protocol-v2.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/05-barrier-stage0-rest.sql"),
                    "/docker-entrypoint-initdb.d/05-barrier-stage0-rest.sql");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> NAMESRV =
            new GenericContainer<>(DockerImageName.parse("apache/rocketmq:5.3.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("namesrv")
                    .withExposedPorts(9876)
                    .withCommand("sh", "/home/rocketmq/rocketmq-5.3.1/bin/mqnamesrv")
                    .waitingFor(Wait.forListeningPort());

    /** broker 内嵌 proxy（--enable-proxy）：gRPC 8081 与 remoting 10911 同进程。
     *  就绪信号用 proxy 的 "startup successfully"（内嵌模式下 broker 先起、proxy 后起——
     *  proxy 成功即 broker+gRPC 均就绪；等 broker 的 "boot success" 会因日志措辞差异超时，CI 首跑实测）。
     *  autoCreateTopicEnable=true + defaultTopicQueueNums=1：topic 由首条消息自动建（单队列，D5）——
     *  容器内 mqadmin 建 topic 静默失败/路由不同步（CI 第2/3跑实测），改走 broker 自动建避开工具链不确定性 */
    @Container
    static final GenericContainer<?> BROKER =
            new GenericContainer<>(DockerImageName.parse("apache/rocketmq:5.3.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("broker")
                    .withExposedPorts(10911, 8081)
                    .withCommand("sh", "/home/rocketmq/rocketmq-5.3.1/bin/mqbroker",
                            "--enable-proxy", "-n", "namesrv:9876",
                            "autoCreateTopicEnable=true", "defaultTopicQueueNums=1")
                    .waitingFor(Wait.forLogMessage(".*startup successfully.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)));

    static Producer producer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerContainerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("reason.device.mq.endpoint",
                () -> BROKER.getHost() + ":" + BROKER.getMappedPort(8081));
        //防干扰：禁自动升降（AutoTask 不碰 IT 设备）+ 拉长指令超时（MonitorTask 不动 IT 流水）
        registry.add("reason.barrier.auto-enabled", () -> "false");
        registry.add("reason.barrier.command-timeout-seconds", () -> "3600");
    }

    /**
     * Spring context 起前准备（容器已由 @Container 启动）：
     * JDBC 注册设备/预置流水 → 发积压消息（用例④，首条消息触发 autoCreateTopic 建 topic）→ 建 producer
     */
    @BeforeAll
    static void setUpBrokerAndBacklog() throws Exception {
        //topic 由 broker autoCreateTopicEnable 在首条消息时自动建（单队列 defaultTopicQueueNums=1）——
        //容器内 mqadmin 建 topic 静默失败/路由不同步（CI 第2/3跑实测），改走自动建避开工具链不确定性

        //1. JDBC 注册两台 IT 设备 + 预置流水（Spring context 未起，直连容器）
        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = conn.createStatement()) {
            long now = System.currentTimeMillis() / 1000;
            st.executeUpdate("INSERT INTO device_record (device_no, device_name, device_type, device_state,"
                    + " device_secret, device_createtime, device_updatetime) VALUES ('" + DEVICE_NO
                    + "', 'IT一号杆', 1, 0, '" + SECRET + "', " + now + ", " + now + ")");
            st.executeUpdate("INSERT INTO device_record (device_no, device_name, device_type, device_state,"
                    + " device_secret, device_createtime, device_updatetime) VALUES ('" + DEVICE_NO_BACKLOG
                    + "', 'IT二号杆', 1, 0, '" + SECRET + "', " + now + ", " + now + ")");
            //用例①预置：seq=7 OPEN 待到位；用例②预置：seq=8 CLOSE 待到位
            st.executeUpdate("INSERT INTO device_command_log (device_no, command_action, command_seq,"
                    + " trigger_type, command_status, retry_count, command_createtime) VALUES ('"
                    + DEVICE_NO + "', 'OPEN', 7, 1, 0, 0, " + now + ")");
            st.executeUpdate("INSERT INTO device_command_log (device_no, command_action, command_seq,"
                    + " trigger_type, command_status, retry_count, command_createtime) VALUES ('"
                    + DEVICE_NO + "', 'CLOSE', 8, 1, 0, 0, " + now + ")");
        }

        //2. producer（测试侧模拟 sim 发送）——不 setTopics：跳过启动时路由校验（topic 尚未由首条消息自动建）
        producer = ClientServiceProvider.loadService().newProducerBuilder()
                .setClientConfiguration(ClientConfiguration.newBuilder()
                        .setEndpoints(BROKER.getHost() + ":" + BROKER.getMappedPort(8081))
                        .build())
                .build();

        //3. 积压消息（用例④）：此刻 Spring context 未起、平台 consumer 未连——消息在 broker 积压（首条触发 autoCreateTopic），
        //context 起后 consumer 连上应全部消费（"平台重启窗口零丢失"的等价实证）
        send(DEVICE_NO_BACKLOG, 1, null, "it-boot-backlog", 1, true);
    }

    @Test
    @DisplayName("真事件闭环：发送→消费→台账序守卫更新 + 按 seq 精确销账 ARRIVED")
    void 真事件_消费后台账与流水终态() throws Exception {
        send(DEVICE_NO, 1, 7L, "it-boot-1", 1, true);

        awaitTrue(() -> Integer.valueOf(1).equals(queryInt(
                        "SELECT device_state FROM device_record WHERE device_no = '" + DEVICE_NO + "'"))
                && Integer.valueOf(1).equals(queryInt(
                        "SELECT command_status FROM device_command_log WHERE device_no = '" + DEVICE_NO
                                + "' AND command_seq = 7")),
                "台账 UP + 流水 seq=7 ARRIVED");
    }

    @Test
    @DisplayName("重复投递：同 eventSeq 双发→业务幂等吸收（序守卫拒第二条，台账/流水终态唯一）")
    void 重复投递_业务幂等吸收() throws Exception {
        send(DEVICE_NO, 2, 8L, "it-boot-1", 2, true);
        send(DEVICE_NO, 2, 8L, "it-boot-1", 2, true); //重复投递（MQ at-least-once 语义模拟）

        awaitTrue(() -> Integer.valueOf(2).equals(queryInt(
                        "SELECT device_state FROM device_record WHERE device_no = '" + DEVICE_NO + "'"))
                && Integer.valueOf(1).equals(queryInt(
                        "SELECT command_status FROM device_command_log WHERE device_no = '" + DEVICE_NO
                                + "' AND command_seq = 8"))
                && Integer.valueOf(2).equals(queryInt(
                        "SELECT last_event_seq FROM device_record WHERE device_no = '" + DEVICE_NO + "'")),
                "台账 DOWN + 流水 seq=8 ARRIVED 唯一 + last_event_seq=2（第二条被序守卫拒）");
        //流水唯一性：seq=8 只有一行（重复投递不产生重复销账/重复行）
        assertThat(queryInt("SELECT COUNT(*) FROM device_command_log WHERE device_no = '" + DEVICE_NO
                + "' AND command_seq = 8")).isEqualTo(1);
    }

    @Test
    @DisplayName("验签失败毒消息：ack 丢弃不重投——DB 零变化")
    void 验签失败_毒消息丢弃不重投() throws Exception {
        send(DEVICE_NO, 1, null, "it-boot-1", 3, false); //签名错误

        //给足消费+可能的重投窗口（毒消息被 ack 丢弃，不入重投循环——契约 §7.3）
        Thread.sleep(8000);
        assertThat(queryInt("SELECT device_state FROM device_record WHERE device_no = '" + DEVICE_NO + "'"))
                .as("毒消息不得改动台账").isEqualTo(2);
        assertThat(queryInt("SELECT last_event_seq FROM device_record WHERE device_no = '" + DEVICE_NO + "'"))
                .as("毒消息不得推进事件序基线").isEqualTo(2);
    }

    @Test
    @DisplayName("积压续消费：context 起前投递的消息，consumer 连上后全部消费（重启窗口零丢失等价实证）")
    void 积压消息_续消费不丢() {
        awaitTrue(() -> Integer.valueOf(1).equals(queryInt(
                        "SELECT device_state FROM device_record WHERE device_no = '" + DEVICE_NO_BACKLOG + "'")),
                "积压事件被消费，台账 UP");
    }

    // ---------- helpers ----------

    /**
     * 发送事件消息（模拟 sim MqEventReporter 信封：body=协议 v2 JSON，签名置 message property）
     *
     * @param validSign true=正确签名；false=伪造签名（毒消息用例）
     */
    private static void send(String deviceNo, int state, Long commandSeq,
                             String bootId, long eventSeq, boolean validSign) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceNo", deviceNo);
        body.put("state", state);
        body.put("commandSeq", commandSeq);
        body.put("bootId", bootId);
        body.put("eventSeq", eventSeq);
        String canonical = DeviceSignature.canonicalEvent(deviceNo, state, commandSeq, bootId, eventSeq);
        String sign = validSign ? DeviceSignature.sign(SECRET, canonical) : "forged-signature";
        Message message = ClientServiceProvider.loadService().newMessageBuilder()
                .setTopic(TOPIC)
                .setBody(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8))
                .addProperty("X-Device-No", deviceNo)
                .addProperty("X-Device-Sign", sign)
                .setKeys(deviceNo + "-" + eventSeq)
                .build();
        producer.send(message);
    }

    private Integer queryInt(String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 轮询等待条件成立（最长 30s；消费异步，断言用轮询而非 sleep——测试竞态教训） */
    private void awaitTrue(BooleanSupplier condition, String desc) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(condition.getAsBoolean()).as(desc).isTrue();
    }
}
