package com.reason.modules.sys.security;

import com.reason.common.exception.RRException;
import com.reason.common.utils.DateUtils;
import com.reason.common.utils.ParamUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败防护（T19 认证前置：loginname+IP 双维度限速）
 *
 * <p>两层防线（原实现仅"账号维度"锁，存在两个真实缺陷：①恶意锁号——知道用户名即可用
 * 错误密码把任意账号锁死（DoS）；②无 IP 维度——轮换账号名撞库不受任何约束）：</p>
 * <ol>
 *   <li>账号×IP 组合锁：同一 (loginname, ip) 连续失败达 attempt_limit（sys_param）→
 *       锁定 lock_time 分钟——他人 IP 不受影响（恶意锁号消除），同 IP 换账号名由第 2 层兜住；</li>
 *   <li>IP 维度闸：同一 IP 在窗口内（reason.security.login-ip-*）失败达上限 → 该 IP 拒绝登录——
 *       约束"广撒网撞库 / 轮换账号名爆破"。</li>
 * </ol>
 *
 * <p>存储（Redis，StringRedisTemplate）：计数键 INCR 原子累加（首增时挂 TTL），
 * 账号锁定由独立锁键承担（TTL=lock_time）；登录成功清理全部失败痕迹。
 * attempt_limit=0 关闭账号维度（原有语义）；login-ip-max=0 关闭 IP 维度。</p>
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /** 失败结局：调用方据此抛对应文案 */
    public enum FailureOutcome {
        /** 普通失败（未触发任何锁定） */
        NORMAL,
        /** 本次失败触发账号×IP 组合锁 */
        LOCKED_BY_ACCOUNT,
        /** 本次失败触发 IP 维度闸 */
        LOCKED_BY_IP
    }

    private static final String ACCT_FAIL_KEY = "login:fail:acct:%s:%s";
    private static final String ACCT_LOCK_KEY = "login:lock:acct:%s:%s";
    private static final String IP_FAIL_KEY = "login:fail:ip:%s";

    /** loginname 进 Redis key 前的长度上限（防御超长 key） */
    private static final int MAX_LOGINNAME_LEN = 64;

    private final StringRedisTemplate stringRedisTemplate;
    private final ParamUtils paramUtils;

    /** IP 维度闸：窗口内失败上限（0=关闭） */
    @Value("${reason.security.login-ip-max:20}")
    private int ipMax;

    /** IP 维度闸：窗口秒数 */
    @Value("${reason.security.login-ip-window-seconds:600}")
    private int ipWindowSeconds;

    public LoginAttemptGuard(StringRedisTemplate stringRedisTemplate, ParamUtils paramUtils) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.paramUtils = paramUtils;
    }

    /**
     * 登录前预检：账号组合锁 / IP 闸命中 → 抛业务异常
     * （文案不区分"账号不存在"与"密码错误"——信息最小化）
     */
    public void assertNotBlocked(String loginname, String ip) {
        Map<String, Integer> policy = paramUtils.getAttemptLimtAndLockTime();
        String acct = normalize(loginname);
        if (policy.get("attemptLimit") != 0
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(acctLockKey(acct, ip)))) {
            throw new RRException("账号已限时锁定，请稍后再尝试");
        }
        if (ipMax > 0 && currentIpFailCount(ip) >= ipMax) {
            throw new RRException("当前网络登录失败次数过多，请稍后再试");
        }
    }

    /**
     * 记录一次"账号或密码错误"：双维度计数递增（都记录），返回结局
     * （判定优先级：账号组合锁 &gt; IP 闸 &gt; 普通）
     */
    public FailureOutcome onFailure(String loginname, String ip) {
        Map<String, Integer> policy = paramUtils.getAttemptLimtAndLockTime();
        int attemptLimit = policy.get("attemptLimit");
        int lockTime = policy.get("lockTime");
        String acct = normalize(loginname);

        // 1) IP 维度：窗口内累计（每次失败都记——防"轮换账号名"绕开组合维度）
        long ipTimes = 0;
        if (ipMax > 0) {
            ipTimes = incrWithExpire(ipFailKey(ip), ipWindowSeconds);
        }

        // 2) 账号×IP 维度：达阈值 → 上锁（独立锁键 TTL=lockTime 分钟）+ 重置计数（解锁后重新计数）
        long acctTimes = 0;
        if (attemptLimit > 0) {
            String failKey = acctFailKey(acct, ip);
            acctTimes = incrWithExpire(failKey, secondsUntilEndOfDay());
            if (acctTimes >= attemptLimit) {
                stringRedisTemplate.delete(failKey);
                stringRedisTemplate.opsForValue().set(acctLockKey(acct, ip), "1", lockTime * 60L, TimeUnit.SECONDS);
                log.warn("[登录防护] 账号组合锁定 loginname={} ip={} times={}", acct, ip, acctTimes);
                return FailureOutcome.LOCKED_BY_ACCOUNT;
            }
        }
        if (ipTimes >= ipMax && ipMax > 0) {
            log.warn("[登录防护] IP 闸拦截 ip={} times={}", ip, ipTimes);
            return FailureOutcome.LOCKED_BY_IP;
        }
        return FailureOutcome.NORMAL;
    }

    /**
     * 登录成功：清理该 (loginname, ip) 与 IP 的全部失败痕迹（幂等）
     */
    public void onSuccess(String loginname, String ip) {
        String acct = normalize(loginname);
        stringRedisTemplate.delete(List.of(acctFailKey(acct, ip), acctLockKey(acct, ip), ipFailKey(ip)));
    }

    private long incrWithExpire(String key, long ttlSeconds) {
        Long v = stringRedisTemplate.opsForValue().increment(key);
        if (v != null && v == 1L) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return v == null ? 0 : v;
    }

    private long currentIpFailCount(String ip) {
        String v = stringRedisTemplate.opsForValue().get(ipFailKey(ip));
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 计数键 TTL：到当日 23:59:59（与原实现"当日窗口"语义一致；兜底至少 60s） */
    private static long secondsUntilEndOfDay() {
        long nowSec = System.currentTimeMillis() / 1000;
        return Math.max(60, DateUtils.getEndTime(nowSec) - nowSec);
    }

    private static String normalize(String loginname) {
        String s = loginname == null ? "" : loginname.trim();
        return s.length() > MAX_LOGINNAME_LEN ? s.substring(0, MAX_LOGINNAME_LEN) : s;
    }

    private static String acctFailKey(String acct, String ip) {
        return String.format(ACCT_FAIL_KEY, acct, ip);
    }

    private static String acctLockKey(String acct, String ip) {
        return String.format(ACCT_LOCK_KEY, acct, ip);
    }

    private static String ipFailKey(String ip) {
        return String.format(IP_FAIL_KEY, ip);
    }
}
