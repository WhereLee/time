# 修复：token 有效但用户已被物理删除导致 NPE

> 状态：已修（2026-09-04，A1 块设计用例表时发现）
> 来源：renren-security 原版 Realm 逻辑的血统缺陷

## 缺陷描述

`AuthServiceImpl.verifyTokenAndGetUser` 原逻辑：

```java
SysUserTokenEntity tokenEntity = getUserToken(token);  // 校验 token 存在且未过期
SysUserEntity user = getUser(tokenEntity.getUserId());
if (!user.open()) { ... }   // ← user 为 null 时 NPE
```

当 `sys_user_token` 表残留记录、而对应 `sys_user` 已被物理删除（如管理后台硬删用户）时，任何携带该 token 的请求都会 500（NPE），而非返回业务错误。

## 缺陷性质

**外部键引用的防御性判空缺失**——token 表与用户表没有外键约束（分表/性能设计使然），应用层必须自己兜底。renren 原版 OAuth2Realm 同样存在此问题。

## 修复

```java
//查询用户信息（token 有效但用户已被删除的防御，原 Shiro 版本此处存在 NPE 缺陷）
SysUserEntity user = getUser(tokenEntity.getUserId());
if (user == null) {
    throw new RRException("账号不存在，请重新登录");
}
```

## 回归验证

`AuthServiceTest.token有效但用户已删除_抛账号不存在_不产生NPE`——该用例**在修复前发现**（设计用例表即穷举分支的过程），修复后转绿。

## 关联

- 发现场景说明：测试设计的价值不仅在"验证已写的代码"，更在"用例表穷举时暴露未考虑的输入组合"——本缺陷在设计阶段（写测试之前）就现形。
