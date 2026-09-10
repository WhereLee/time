package com.reason.modules.device.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约共享样例测试（T18 轻量方案，平台侧）：读取仓库根 contracts/samples/event-v2.json，
 * 用本端 DeviceSignature 重算 canonical 与 sign 并断言一致——
 * 平台与 sim 两侧跑同一份样例文件，任一端实现漂移 CI 立即红灯（代替"双端人工锁步"）。
 */
@DisplayName("契约共享样例（平台侧）")
class ContractSampleTest {

    private static final File SAMPLES = new File("../contracts/samples/event-v2.json");

    @Test
    @DisplayName("本端实现与 contracts/samples/event-v2.json 完全一致")
    void 契约样例一致() throws Exception {
        assertThat(SAMPLES).as("契约样例文件（仓库 contracts/samples/）").exists();
        JsonNode root = new ObjectMapper().readTree(SAMPLES);
        String secret = root.get("testSecret").asText();
        JsonNode samples = root.get("samples");
        assertThat(samples.size()).as("样例向量数").isGreaterThanOrEqualTo(4);

        for (JsonNode s : samples) {
            String name = s.get("name").asText();
            String canonical = switch (s.get("kind").asText()) {
                case "event" -> DeviceSignature.canonicalEvent(
                        s.get("deviceNo").asText(),
                        s.get("state").asInt(),
                        s.get("commandSeq").isNull() ? null : s.get("commandSeq").asLong(),
                        s.get("bootId").asText(),
                        s.get("eventSeq").asLong());
                case "heartbeat" -> DeviceSignature.canonicalHeartbeat(
                        s.get("deviceNo").asText(), s.get("state").asInt());
                case "command" -> DeviceSignature.canonicalCommand(
                        s.get("deviceNo").asText(), s.get("action").asText(), s.get("seq").asLong());
                default -> throw new IllegalStateException("未知样例 kind=" + s.get("kind").asText());
            };
            assertThat(canonical).as("canonical 不一致: %s", name).isEqualTo(s.get("canonical").asText());
            assertThat(DeviceSignature.sign(secret, canonical)).as("sign 不一致: %s", name).isEqualTo(s.get("sign").asText());
        }
    }
}
