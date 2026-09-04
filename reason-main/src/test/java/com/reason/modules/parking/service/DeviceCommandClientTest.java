package com.reason.modules.parking.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备指令下发客户端测试（行为级：起真实 HTTP 端点验证成功/失败语义）
 *
 * <p>关键断言：失败只返回 false 不抛异常（账务不依赖设备反馈的契约）。</p>
 */
@DisplayName("设备指令下发客户端")
class DeviceCommandClientTest {

    private HttpServer server;
    private DeviceCommandClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new DeviceCommandClient();
        ReflectionTestUtils.setField(client, "simBaseUrl", baseUrl);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("指令下发成功：2xx 响应 → true")
    void 指令下发成功() {
        server.createContext("/cmd/exec", this::respond200);

        boolean ok = client.sendCommand(DeviceCommandClient.CMD_OPEN_GATE, "A-001");

        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("指令下发失败：目标不可达 → false 且不抛（账务已完成，指令失败仅告警）")
    void 指令下发失败不抛() {
        //指向无人监听的端口（server 未创建 /cmd/exec 外的场景用随机未监听端口模拟）
        String deadUrl = "http://127.0.0.1:" + unusedPort();
        ReflectionTestUtils.setField(client, "simBaseUrl", deadUrl);

        boolean ok = client.sendCommand(DeviceCommandClient.CMD_OPEN_GATE, "A-001");

        assertThat(ok).isFalse();
    }

    @Test
    @DisplayName("指令下发失败：业务端 5xx → false 且不抛")
    void 指令下发业务错误不抛() {
        server.createContext("/cmd/exec", ex -> {
            byte[] body = "boom".getBytes();
            ex.sendResponseHeaders(500, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });

        boolean ok = client.sendCommand("LOCK", "A-002");

        assertThat(ok).isFalse();
    }

    private void respond200(HttpExchange exchange) throws IOException {
        byte[] body = "{\"code\":0}".getBytes();
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private int unusedPort() {
        //拿到一个已关闭监听的随机端口（连接会被拒绝）
        return (int) (20000 + (System.currentTimeMillis() % 20000));
    }
}
