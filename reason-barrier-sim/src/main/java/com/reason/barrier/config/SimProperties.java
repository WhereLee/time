package com.reason.barrier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟器配置（sim.*）
 *
 * <p>devices：本进程"安装"的物理设备清单（与平台台账编号对齐——设备上电即存在，
 * 台账由管理端登记，两边靠 deviceNo 契约对齐）；moveMillis：模拟升降耗时（真实设备
 * 升降非瞬时，到位需要时间）。</p>
 *
 * <p>批量形态（批次2，B1）：deviceCount==0（默认）→ 设备清单=显式 devices（日常 2 台语义零回归）；
 * >0 → 清单完全由生成式构成（前缀+序号派生 deviceNo/name，密钥从 secretFile 加载，
 * 显式列表整单忽略）。批量剧本用 application-batch.yml（不污染日常配置）。</p>
 */
@Data
@ConfigurationProperties(prefix = "sim")
public class SimProperties {

    /**
     * 平台事件上报地址（平台侧 /api/device/event）
     */
    private String eventUrl;

    /**
     * 平台心跳上报地址（平台侧 /api/device/heartbeat）
     */
    private String heartbeatUrl;

    /**
     * 心跳间隔秒数（需小于平台侧心跳超时阈值 reason.barrier.heartbeat-timeout-seconds，
     * 留足网络抖动余量：阈值 30s 对应间隔 10s，连续丢 2 次心跳才判离线）
     */
    private int heartbeatIntervalSeconds = 10;

    /**
     * 升降动作耗时毫秒（模拟机械动作非瞬时）
     */
    private long moveMillis = 3000;

    /**
     * HTTP 连接超时秒（sim→平台 上行链路；P5 参数化——原硬编码 2s，按注入器剧本标定）
     */
    private int connectTimeoutSeconds = 2;

    /**
     * HTTP 读超时秒（P5：原硬编码心跳/事件各 2s/3s；可配 5-10s 留抖动余量，按注入器剧本标定）
     */
    private int readTimeoutSeconds = 5;

    /**
     * 心跳并行线程上限（P5：单线程逐台串行阻塞 = T11 节拍塌缩根因——每设备独立调度、
     * 谁慢只丢自己的轮；批次2 B9 默认 8→16：50 台节拍 5rps + 延迟注入余量
     * （50 台 × 2s 延迟 / 10s 节拍 ≈ 10 并发需求 <16）；池化常驻成本可忽略，2 台日常无感）
     */
    private int heartbeatThreads = 16;

    /**
     * 安装的设备清单（与平台台账 deviceNo 对齐；secret 为 0.5 per-device HMAC 密钥——
     * 环境变量注入（仓库零明文），须与平台 device_record.device_secret 同值。
     * 批次2：仅 deviceCount==0 时生效；>0 时整单被生成式清单替代）
     */
    private List<DeviceCfg> devices = new ArrayList<>();

    /**
     * 批量生成设备总数（批次2，B1：0=显式清单模式（默认，日常语义不变）；>0=生成式模式，
     * deviceNo/name 按前缀+序号派生，显式 devices 列表忽略）
     */
    private int deviceCount = 0;

    /**
     * 生成式设备编号前缀（deviceNo={前缀}{序号 %02d}，序号 1 起：BARRIER-B-01…50）
     */
    private String deviceNoPrefix = "BARRIER-B-";

    /**
     * 生成式设备名称前缀（name={前缀}{序号 %02d}：批量杆-01…50）
     */
    private String deviceNamePrefix = "批量杆-";

    /**
     * 批量设备密钥文件（批次2，B3：每行 deviceNo=32hex，# 注释行；sim 侧"出厂烧录"语义——
     * 仓库零明文，路径须 gitignore；与平台登记入参 deviceSecret 由同一登记脚本一次产出两份）
     */
    private String secretFile = "";

    /**
     * 事件通道形态（批次3，D-A 终态开关；只作用于事件通道——心跳恒 HTTP 不走 MQ，D3）：
     * dual=双写（对照期默认，HTTP+MQ 并行）/ mq=仅 MQ（终态：HTTP 事件退役为降级开关）/
     * http=仅 HTTP（降级回滚形态）。取值非法、或 mq 形态但 sim.mq.enabled=false——启动即失败（fail-fast）
     */
    private String eventChannel = "dual";

    /**
     * MQ 事件通道（阶段2 双写 → 批次3 三态路由：是否投递 MQ 由 eventChannel 决定；
     * 心跳不走 MQ——D3 定稿：判活不依赖 broker）
     */
    private Mq mq = new Mq();

    /** 密钥文件加载缓存（null=尚未加载；加载一次后 effectiveDevices 复用） */
    private volatile Map<String, String> secretCache;

    /**
     * 按设备号取 HMAC 密钥（null=未配置——fail secure：验签必然失败）。
     * 批次2 补漏：生成式模式（deviceCount>0）下显式 devices 为空——reporter 取密钥须回退查
     * 密钥文件缓存（loadSecrets），否则 50 台全部"未配置密钥"（首跑实测暴露）
     */
    public String secretOf(String deviceNo) {
        for (DeviceCfg cfg : devices) {
            if (cfg.getDeviceNo().equals(deviceNo)) {
                return cfg.getSecret();
            }
        }
        if (deviceCount > 0) {
            return loadSecrets().getOrDefault(deviceNo, null);
        }
        return null;
    }

    /**
     * 本进程生效的设备清单（批次2，B1 二选一）：生成式模式下按派生规则构建，
     * 密钥从 secretFile 取（文件未配/设备缺行 → secret 置空串，走现有 fail-safe 路径）
     */
    public List<DeviceCfg> effectiveDevices() {
        if (deviceCount <= 0) {
            return devices;
        }
        Map<String, String> secrets = loadSecrets();
        List<DeviceCfg> generated = new ArrayList<>(deviceCount);
        for (int i = 1; i <= deviceCount; i++) {
            DeviceCfg cfg = new DeviceCfg();
            String deviceNo = deviceNoPrefix + String.format("%02d", i);
            cfg.setDeviceNo(deviceNo);
            cfg.setName(deviceNamePrefix + String.format("%02d", i));
            cfg.setSecret(secrets.getOrDefault(deviceNo, ""));
            generated.add(cfg);
        }
        return generated;
    }

    /**
     * 密钥文件加载（只读一次，缓存复用）：配置了但文件不存在 → 启动 fail-fast（B4：
     * 配置错位立即暴露——50 台全静默不上报是最难发现的事故形态）
     */
    private Map<String, String> loadSecrets() {
        Map<String, String> cache = secretCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (secretCache == null) {
                secretCache = parseSecretFile();
            }
            return secretCache;
        }
    }

    private Map<String, String> parseSecretFile() {
        if (secretFile == null || secretFile.isBlank()) {
            return Map.of();
        }
        Path path = Path.of(secretFile);
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "sim.secret-file 已配置但文件不存在: " + path.toAbsolutePath() + "（批次2 B4 fail-fast：配置错位立即暴露）");
        }
        Map<String, String> secrets = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                secrets.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("sim.secret-file 读取失败: " + path.toAbsolutePath(), e);
        }
        return secrets;
    }

    @Data
    public static class DeviceCfg {
        private String deviceNo;
        private String name;
        /** HMAC 密钥（${ENV:} 环境变量注入/批量密钥文件注入——仓库零明文） */
        private String secret;
    }

    /**
     * MQ 通道配置（sim.mq.*）
     */
    @Data
    public static class Mq {

        /**
         * MQ 通道启用开关（false=Bean 不创建，纯 HTTP 回滚形态；event-channel=mq 时与 false 组合=启动失败）
         */
        private boolean enabled = true;

        /**
         * RocketMQ proxy gRPC 端点（5.x 客户端走 gRPC；本机联调 127.0.0.1:8081）
         */
        private String endpoint = "127.0.0.1:8081";

        /**
         * 事件 topic（与平台消费订阅一致；单队列保序——D5）
         */
        private String topic = "device-event";

        /**
         * 失败缓冲队列容量（broker 故障时事件排队等恢复；满则丢弃最旧记 error，
         * 平台 QUERY_STATE/对账兜底——分层可靠性，与 HTTP 通道同哲学）
         */
        private int bufferSize = 500;
    }
}
