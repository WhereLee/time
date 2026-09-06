package com.reason.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveDataMasker 单元测试：敏感字段掩码覆盖性与非敏感字段零误伤
 */
@DisplayName("敏感数据掩码器")
class SensitiveDataMaskerTest {

    @Test
    @DisplayName("顶层敏感字段被掩码，非敏感字段原样保留")
    void 顶层敏感字段掩码_非敏感字段保留() {
        String json = """
                {"loginname":"adminManager","password":"admin123","userName":"李","page":"1"}
                """;

        String masked = SensitiveDataMasker.maskJson(json);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("adminManager");
        assertThat(masked).contains("\"page\":\"1\"");
        assertThat(masked).doesNotContain("admin123");
    }

    @Test
    @DisplayName("嵌套对象与数组内的敏感字段同样被掩码（递归）")
    void 嵌套对象与数组递归掩码() {
        String json = """
                {"data":{"userPassword":"hash-abc","salt":"s1","roleIds":[1,2]},"items":[{"token":"t1","id":1},{"id":2}]}
                """;

        String masked = SensitiveDataMasker.maskJson(json);

        assertThat(masked).contains("\"userPassword\":\"***\"");
        assertThat(masked).contains("\"salt\":\"***\"");
        assertThat(masked).contains("\"token\":\"***\"");
        assertThat(masked).contains("\"roleIds\":[1,2]");
        assertThat(masked).doesNotContain("hash-abc").doesNotContain("t1");
    }

    @Test
    @DisplayName("词根大小写不敏感（userPassword / User-Password 变体均命中）")
    void 大小写不敏感命中() {
        assertThat(SensitiveDataMasker.isSensitiveKey("userPassword")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("USER_PASSWORD")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("OldPassword")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("userName")).isFalse();
        assertThat(SensitiveDataMasker.isSensitiveKey("logState")).isFalse();
        assertThat(SensitiveDataMasker.isSensitiveKey(null)).isFalse();
    }

    @Test
    @DisplayName("空输入与非法输入防御：原样返回不抛异常")
    void 空与非法输入防御() {
        assertThat(SensitiveDataMasker.maskJson(null)).isNull();
        assertThat(SensitiveDataMasker.maskJson("")).isEmpty();
    }

    @Test
    @DisplayName("常见操作日志参数形态：数组包裹的表单参数（JsonUtil.toJsonString(args) 的输出形态）")
    void 数组包裹表单参数形态() {
        String json = """
                [{"loginname":"adminManager","password":"admin123","uuid":null}]
                """;

        String masked = SensitiveDataMasker.maskJson(json);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).doesNotContain("admin123");
        assertThat(masked).contains("adminManager");
    }
}
