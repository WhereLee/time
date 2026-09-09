package com.reason.barrier.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模拟器配置测试（批次2，F1：生成式清单派生 / 密钥文件解析 / B1 二选一语义 / B4 fail-fast 与 fail-safe）
 */
@DisplayName("模拟器配置(批次2生成式注册)")
class SimPropertiesTest {

    @TempDir
    Path tempDir;

    private SimProperties explicitProps() {
        SimProperties properties = new SimProperties();
        SimProperties.DeviceCfg device = new SimProperties.DeviceCfg();
        device.setDeviceNo("BARRIER-E-01");
        device.setName("东门一号杆");
        device.setSecret("test-secret-e01");
        properties.setDevices(List.of(device));
        return properties;
    }

    @Test
    @DisplayName("deviceCount=0：effectiveDevices == 显式列表（日常 2 台语义零回归）")
    void 显式模式_清单原样返回() {
        SimProperties properties = explicitProps();

        List<SimProperties.DeviceCfg> effective = properties.effectiveDevices();

        assertThat(effective).hasSize(1);
        assertThat(effective.get(0).getDeviceNo()).isEqualTo("BARRIER-E-01");
        assertThat(effective.get(0).getSecret()).isEqualTo("test-secret-e01");
    }

    @Test
    @DisplayName("生成式清单：count=3 → 设备号/名称按前缀+序号 %02d 派生（显式列表忽略）")
    void 生成式清单_派生规则() {
        SimProperties properties = explicitProps();
        properties.setDeviceCount(3);
        properties.setDeviceNoPrefix("BARRIER-B-");
        properties.setDeviceNamePrefix("批量杆-");

        List<SimProperties.DeviceCfg> effective = properties.effectiveDevices();

        assertThat(effective).hasSize(3);
        assertThat(effective).extracting(SimProperties.DeviceCfg::getDeviceNo)
                .containsExactly("BARRIER-B-01", "BARRIER-B-02", "BARRIER-B-03");
        assertThat(effective).extracting(SimProperties.DeviceCfg::getName)
                .containsExactly("批量杆-01", "批量杆-02", "批量杆-03");
        //显式列表整单忽略（B1 二选一）：不含显式设备
        assertThat(effective).extracting(SimProperties.DeviceCfg::getDeviceNo)
                .doesNotContain("BARRIER-E-01");
    }

    @Test
    @DisplayName("密钥文件：# 注释行跳过、deviceNo 精确匹配取 secret")
    void 密钥文件_解析取secret() throws Exception {
        Path secretFile = tempDir.resolve("batch-secrets.properties");
        Files.writeString(secretFile, """
                # 批量密钥（仓库零明文，本文件 gitignore）

                BARRIER-B-01=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                BARRIER-B-03=cccccccccccccccccccccccccccccccc
                """);
        SimProperties properties = explicitProps();
        properties.setDeviceCount(2);
        properties.setSecretFile(secretFile.toString());

        List<SimProperties.DeviceCfg> effective = properties.effectiveDevices();

        assertThat(effective.get(0).getSecret()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        //B-02 缺行 → secret 置空串（fail-safe：上报取消 + error 日志，与显式列表缺密钥同语义）
        assertThat(effective.get(1).getSecret()).isEmpty();
    }

    @Test
    @DisplayName("密钥文件未配置：生成式清单 secret 全空串（fail-safe 路径）")
    void 密钥文件未配置_secret空串() {
        SimProperties properties = explicitProps();
        properties.setDeviceCount(2);

        List<SimProperties.DeviceCfg> effective = properties.effectiveDevices();

        assertThat(effective).allSatisfy(cfg -> assertThat(cfg.getSecret()).isEmpty());
    }

    @Test
    @DisplayName("密钥文件配置了但不存在：fail-fast（B4 配置错位立即暴露）")
    void 密钥文件缺失_failfast() {
        SimProperties properties = explicitProps();
        properties.setDeviceCount(2);
        properties.setSecretFile(tempDir.resolve("not-exists.properties").toString());

        assertThatThrownBy(properties::effectiveDevices)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("secretOf 回退密钥文件（补漏：生成式下显式列表为空——reporter 取密钥须命中文件缓存）")
    void secretOf_生成式回退密钥文件() throws Exception {
        Path secretFile = tempDir.resolve("batch-secrets.properties");
        Files.writeString(secretFile, "BARRIER-B-01=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n");
        SimProperties properties = explicitProps();
        properties.setDeviceCount(1);
        properties.setDeviceNoPrefix("BARRIER-B-");
        properties.setSecretFile(secretFile.toString());

        //先触发一次文件加载（effectiveDevices），再断言 secretOf 命中缓存
        properties.effectiveDevices();
        assertThat(properties.secretOf("BARRIER-B-01"))
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        //未配置设备仍返回 null（fail secure）
        assertThat(properties.secretOf("BARRIER-B-99")).isNull();
    }
}
