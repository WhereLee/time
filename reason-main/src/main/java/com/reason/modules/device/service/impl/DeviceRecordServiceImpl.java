package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.PageParams;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.StringUtils;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceRecordForm;
import com.reason.modules.device.service.DeviceRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 设备台账服务实现
 *
 * <p>档案职责：登记 + 分页查询 + 事件驱动的状态更新（铁律唯一写入路径）。
 * 登记建档状态恒为 0-未接入——状态是设备说的，不是登记人填的。
 * 列表附带在线状态（Redis 心跳 key EXISTS 实时判定，不落库）——直接读 Redis 而不经过
 * DeviceMonitorService，避免台账⇄监控双向依赖（监控服务已依赖台账的状态写入路径）。</p>
 */
@Slf4j
@Service("deviceRecordService")
public class DeviceRecordServiceImpl extends ServiceImpl<DeviceRecordDao, DeviceRecordEntity>
        implements DeviceRecordService {

    private final StringRedisTemplate stringRedisTemplate;

    public DeviceRecordServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public PageUtils queryPage(DeviceRecordForm form) {
        //T15：分页参数统一钳制（非法输入不再 500、超大 limit 不放行）
        int pageNum = PageParams.page(form.getPage());
        int limit = PageParams.limit(form.getLimit());

        IPage<DeviceRecordEntity> page = this.page(
                new Page<>(pageNum, limit),
                new LambdaQueryWrapper<DeviceRecordEntity>()
                        .like(StringUtils.isNotBlank(form.getDeviceNo()), DeviceRecordEntity::getDeviceNo, form.getDeviceNo())
                        .like(StringUtils.isNotBlank(form.getDeviceName()), DeviceRecordEntity::getDeviceName, form.getDeviceName())
                        .eq(form.getDeviceType() != null, DeviceRecordEntity::getDeviceType, form.getDeviceType())
                        .eq(form.getDeviceState() != null, DeviceRecordEntity::getDeviceState, form.getDeviceState())
                        .orderByDesc(DeviceRecordEntity::getDeviceCreatetime)
        );
        //在线状态实时填充（Redis EXISTS：心跳 key 未过期即在线）——展示字段不落库，落库即过时；
        //T15：pipeline 一次往返查全部（原每行一次 hasKey = 列表页 N+1 次 Redis 往返）
        List<DeviceRecordEntity> records = page.getRecords();
        if (!records.isEmpty()) {
            List<byte[]> keys = records.stream()
                    .map(r -> (BarrierRedisKeys.ONLINE_PREFIX + r.getDeviceNo()).getBytes(StandardCharsets.UTF_8))
                    .collect(Collectors.toList());
            List<Object> results = stringRedisTemplate.executePipelined(
                    (RedisCallback<Object>) conn -> {
                        keys.forEach(conn::exists);
                        return null;
                    });
            for (int i = 0; i < records.size(); i++) {
                records.get(i).setOnline(Boolean.TRUE.equals(results.get(i)));
            }
        }
        return new PageUtils(page);
    }

    @Override
    public void saveRecord(DeviceRecordForm form, Long userId) {
        if (StringUtils.isBlank(form.getDeviceNo())) {
            throw new RRException("设备编号不能为空");
        }
        //唯一性校验：同一编号重复登记是档案错误，直接拒绝（业务层先查 + DB 唯一索引兜底并发）
        DeviceRecordEntity exists = this.getOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, form.getDeviceNo()));
        if (exists != null) {
            throw new RRException("设备编号已存在: " + form.getDeviceNo());
        }

        DeviceRecordEntity entity = new DeviceRecordEntity();
        entity.setDeviceNo(form.getDeviceNo().trim());
        entity.setDeviceName(form.getDeviceName());
        entity.setDeviceType(form.getDeviceType() == null ? 1 : form.getDeviceType());
        entity.setLocation(form.getLocation());
        entity.setDeviceState(DeviceState.NOT_CONNECTED.getCode()); //建档恒为未接入，状态由设备事件驱动
        //0.5 per-device 凭证：登记即生成 HMAC 密钥（32hex 随机）——仓库零明文，DB 存储，
        //设备侧配置同值（真实流程为设备出厂/安装时烧录，样例为联调配置同步）
        entity.setDeviceSecret(UUID.randomUUID().toString().replace("-", ""));
        entity.setDeviceRemark(form.getDeviceRemark());
        entity.setDeviceCreator(userId);
        long now = System.currentTimeMillis() / 1000;
        entity.setDeviceCreatetime(now);
        entity.setDeviceUpdatetime(now);
        this.save(entity);
    }

    @Override
    public void updateStateByEvent(String deviceNo, int stateCode) {
        //心跳校正入口（无事件序守卫）：单条条件 UPDATE（效率：省一次查询；原子：无查改间隙竞态）
        //baseMapper.update 返回影响行数：0 = 档案不存在（模拟器里有、平台没建档 = 配置错位），
        //显式失败让设备侧发现；相同状态重复上报幂等无害（影响行数 1，直接覆盖）
        DeviceRecordEntity update = new DeviceRecordEntity();
        update.setDeviceState(stateCode);
        update.setDeviceUpdatetime(System.currentTimeMillis() / 1000);
        int rows = baseMapper.update(update, new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (rows == 0) {
            throw new RRException("未登记的设备上报事件: " + deviceNo);
        }
        //单一时钟源：时间戳以服务器为准，不采信设备时钟
        log.info("设备事件驱动状态更新(心跳校正) deviceNo={} state={}", deviceNo, stateCode);
    }

    @Override
    public boolean updateStateByEventWithSeq(String deviceNo, int stateCode, String bootId, long eventSeq) {
        //协议 v2 序守卫（contracts/PROTOCOL-V2.md §3.3）：单条条件 UPDATE 原子完成"守卫+写状态+推进基线"
        //接受条件（三取一）：
        //  1) 台账从未记录过事件基线（建档后首包）
        //  2) bootId 不同 = 设备重启新代际（重启自述/重启后首批事件不被旧序误拒）
        //  3) 同 bootId 且 eventSeq 更大 = 正常递增
        //拒绝：同 bootId 且 eventSeq <= last（重放/乱序迟到/陈旧覆盖）——协议容忍的幂等丢弃
        DeviceRecordEntity update = new DeviceRecordEntity();
        update.setDeviceState(stateCode);
        update.setDeviceLastBootId(bootId);
        update.setDeviceLastEventSeq(eventSeq);
        update.setDeviceUpdatetime(System.currentTimeMillis() / 1000);
        int rows = baseMapper.update(update, new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, deviceNo)
                .and(w -> w.isNull(DeviceRecordEntity::getDeviceLastBootId)
                        .or().ne(DeviceRecordEntity::getDeviceLastBootId, bootId)
                        .or(o -> o.eq(DeviceRecordEntity::getDeviceLastBootId, bootId)
                                .lt(DeviceRecordEntity::getDeviceLastEventSeq, eventSeq))));
        if (rows == 0) {
            //区分拒绝原因：档案不存在（配置错位，显式失败）vs 同代际旧序（幂等丢弃，不告警）
            DeviceRecordEntity record = this.getOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                    .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
            if (record == null) {
                throw new RRException("未登记的设备上报事件: " + deviceNo);
            }
            log.info("事件序守卫拒绝(同代际旧序/重放,幂等丢弃) deviceNo={} bootId={} eventSeq={} 已受理last={}/{}",
                    deviceNo, bootId, eventSeq, record.getDeviceLastBootId(), record.getDeviceLastEventSeq());
            return false;
        }
        log.info("事件驱动状态更新(序守卫通过) deviceNo={} state={} bootId={} eventSeq={}",
                deviceNo, stateCode, bootId, eventSeq);
        return true;
    }
}
