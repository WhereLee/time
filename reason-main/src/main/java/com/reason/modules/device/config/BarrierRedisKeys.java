package com.reason.modules.device.config;

/**
 * 升降杆 Redis key 命名空间（全部业务 key 收口在此，防散落各处拼错/冲突）
 *
 * <p>每个 key 都对应一个"非 Redis 不可"的理由：
 * <ul>
 *   <li>online：TTL 即离线判定（把过期时间当业务逻辑，免扫库）；</li>
 *   <li>manual-hold：自动到期的临时状态（手动保持期，免清理任务）；</li>
 *   <li>alarm-dedup：SETNX 原子去重窗口（告警防刷屏，免查插竞态）；</li>
 *   <li>alarm-open：OFFLINE 未处理标记（GETDEL 原子取删——心跳洪峰下恢复路径零空 UPDATE，批次2 B5）；</li>
 *   <li>cmd-seq：INCR 原子递增（指令幂等序号，免 DB MAX+1 并发竞态）。</li>
 * </ul></p>
 */
public final class BarrierRedisKeys {

    /** 设备在线 key（value=最近心跳时间戳仅诊断用；判定只看存在性，TTL=心跳超时阈值） */
    public static final String ONLINE_PREFIX = "barrier:online:";

    /** 手动保持期 key（value=操作人 userId；TTL=保持期窗口，过期自动恢复自动规则） */
    public static final String MANUAL_HOLD_PREFIX = "barrier:manual-hold:";

    /** 告警去重 key（SETNX 占位，TTL=去重窗口；同设备同类型窗口内只落一条告警） */
    public static final String ALARM_DEDUP_PREFIX = "barrier:alarm-dedup:";

    /** OFFLINE 未处理标记（批次2 B5，无 TTL 显式生命周期：raise 落库后置位——标记存在=该设备有
     * 未处理 OFFLINE 待恢复路径自动关闭；markOnlineRecovered 先 GETDEL 命中才 UPDATE，未命中零 DB 写；
     * 人工 handle 后残留标记由恢复路径 GETDEL 一次性吸收，幂等无害） */
    public static final String ALARM_OPEN_PREFIX = "barrier:alarm-open:";

    /** 指令序号 key（INCR 递增，无 TTL：设备内 seq 永久单调，跨重启/多实例一致） */
    public static final String CMD_SEQ_PREFIX = "barrier:cmd-seq:";

    private BarrierRedisKeys() {
    }
}
