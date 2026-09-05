package com.reason.modules.sys;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证链路集成测试（真实 MySQL + Redis 容器，真实 HTTP 全栈）
 *
 * <p>执行位置：仅 CI（GitHub Actions `mvn verify`）——本机无 Docker 时由命名约定 *IT 天然隔离：
 * surefire 只匹配 *Test，failsafe 只匹配 *IT。</p>
 *
 * <p>容器生命周期：static @Container 在当前 JVM 的所有 IT 类间共享；
 * 每次 CI 均为全新容器、用一次即焚——登录写 token、日志切面写 sys_log 均无污染顾虑。</p>
 *
 * <p>路径注意：RANDOM_PORT 下 TestRestTemplate 的 LocalHostUriTemplateHandler 会自动拼接
 * context-path（/api），用例路径必须不带 /api 前缀（曾因双层 /api/api 导致 401 假绿，见
 * document/pitfalls/testresttemplate-contextpath-auto-prefix.md）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("认证链路集成测试")
class AuthIT {

    private static final String USERNAME = "adminManager";
    private static final String PASSWORD = "admin123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("reason_faster")
            .withUsername("root")
            .withPassword("root")
            //仓库根 db/ 脚本挂载进 entrypoint 初始化目录：容器创建后按文件名序自动建库导入
            //01-壳子基线（23 表）；02-parking（停车域 4 表+菜单）；03-停车授权；04-charging（充电域 5 表+减免列+充电菜单）；05-充电授权
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

    /** Testcontainers 无官方 Redis 模块，用通用容器 */
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerContainerProps(DynamicPropertyRegistry registry) {
        //容器动态地址注入 druid 配置树（test yml 为树形结构 spring.datasource.druid.*）
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    @DisplayName("无 token 访问受保护接口：401 + 统一 JSON 错误体")
    void 无token访问受保护接口_返回401JSON() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/sys/user/info", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JSONObject body = JSON.parseObject(resp.getBody());
        assertThat(body.getInteger("code")).isEqualTo(401);
        assertThat(body.getString("msg")).isNotBlank();
    }

    @Test
    @DisplayName("正确账号密码登录：返回 code=0 与 token")
    void 登录_返回token() {
        JSONObject req = new JSONObject();
        req.put("loginname", USERNAME);
        req.put("password", PASSWORD);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/sys/login", jsonEntity(req), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONObject body = JSON.parseObject(resp.getBody());
        assertThat(body.getInteger("code")).isZero();
        assertThat(body.getJSONObject("data").getString("token")).isNotBlank();
    }

    @Test
    @DisplayName("携带 token 访问受保护接口：通过 @PreAuthorize 链路返回当前用户")
    void 带token访问_返回当前用户信息() {
        JSONObject req = new JSONObject();
        req.put("loginname", USERNAME);
        req.put("password", PASSWORD);
        String token = JSON.parseObject(
                restTemplate.postForEntity("/sys/login", jsonEntity(req), String.class).getBody())
                .getJSONObject("data").getString("token");

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        ResponseEntity<String> resp = restTemplate.exchange(
                "/sys/user/info", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONObject body = JSON.parseObject(resp.getBody());
        assertThat(body.getInteger("code")).isZero();
        assertThat(body.toJSONString()).contains(USERNAME);
    }

    @Test
    @DisplayName("伪造 token：401 拒绝进入业务链路")
    void 伪造token_返回401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "forged-token-123456");
        ResponseEntity<String> resp = restTemplate.exchange(
                "/sys/user/info", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpEntity<String> jsonEntity(JSONObject body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body.toJSONString(), headers);
    }
}
