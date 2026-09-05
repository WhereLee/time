package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.device.dao.DeviceOnlineDao;
import com.reason.modules.device.entity.DeviceOnlineEntity;
import com.reason.modules.device.form.DeviceOnlineForm;
import com.reason.modules.device.service.DeviceOnlineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备在线台账服务实现
 *
 * <p>心跳批处理策略：online/offline 分流后各一条批量 UPDATE（339 台/10s 场景
 * 避免逐台 SQL）；批次匹配行数少于上报数时查差集告警——台账缺设备是"现场有设备
 * 平台不知道"的运维信号（配置漂移），告警不阻塞心跳主流程。</p>
 *
 * @date 2026-09-06
 */
@Slf4j
@Service
public class DeviceOnlineServiceImpl implements DeviceOnlineService {

    /** 心跳批内单设备快照的字段名（与设备通道协议一致） */
    private static final String FIELD_DEVICE_NO = "deviceNo";
    private static final String FIELD_ONLINE = "online";

    private final DeviceOnlineDao deviceOnlineDao;

    public DeviceOnlineServiceImpl(DeviceOnlineDao deviceOnlineDao) {
        this.deviceOnlineDao = deviceOnlineDao;
    }

    @Override
    public PageUtils queryPage(DeviceOnlineForm form) {
        IPage<DeviceOnlineEntity> page = new Query<DeviceOnlineEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        deviceOnlineDao.selectPage(page, new LambdaQueryWrapper<DeviceOnlineEntity>()
                .like(StringUtils.hasText(form.getDeviceNo()), DeviceOnlineEntity::getDeviceNo, form.getDeviceNo())
                .eq(form.getDeviceType() != null, DeviceOnlineEntity::getDeviceType, form.getDeviceType())
                .eq(form.getDeviceState() != null, DeviceOnlineEntity::getDeviceState, form.getDeviceState())
                .like(StringUtils.hasText(form.getBindTarget()), DeviceOnlineEntity::getBindTarget, form.getBindTarget())
                .orderByAsc(DeviceOnlineEntity::getDeviceId));
        return new PageUtils(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordHeartbeat(long reportedAt, List<Map<String, Object>> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        List<String> onlineNos = new ArrayList<>(devices.size());
        List<String> offlineNos = new ArrayList<>(devices.size());
        for (Map<String, Object> d : devices) {
            Object no = d.get(FIELD_DEVICE_NO);
            if (!(no instanceof String) || ((String) no).isBlank()) {
                log.warn("心跳批次含非法设备号，跳过：{}", d);
                continue;
            }
            boolean online = !Boolean.FALSE.equals(d.get(FIELD_ONLINE));
            (online ? onlineNos : offlineNos).add((String) no);
        }

        int matched = 0;
        if (!onlineNos.isEmpty()) {
            matched = deviceOnlineDao.updateOnlineByNos(reportedAt, onlineNos);
        }
        if (!offlineNos.isEmpty()) {
            deviceOnlineDao.updateOfflineByNos(offlineNos);
        }
        //影响行数(匹配行) < 在线上报数 → 差集为台账缺失设备，告警留痕
        if (matched < onlineNos.size()) {
            warnUnknownDevices(onlineNos);
        }
    }

    @Override
    public int offlineByTimeout(long thresholdSeconds) {
        return deviceOnlineDao.offlineByHeartbeatTimeout(thresholdSeconds);
    }

    /** 差集告警：从台账反查已知设备号，与上报集合求差 */
    private void warnUnknownDevices(List<String> reportedNos) {
        try {
            Set<String> known = new HashSet<>();
            deviceOnlineDao.selectList(null).forEach(e -> known.add(e.getDeviceNo()));
            Set<String> unknown = new HashSet<>(reportedNos);
            unknown.removeAll(known);
            if (!unknown.isEmpty()) {
                log.warn("心跳上报含台账外设备号（配置漂移/未登记接入）：{}，共 {} 台",
                        String.join(",", unknown), unknown.size());
            }
        } catch (Exception e) {
            //反查失败不阻断主流程（心跳已写入），仅记录
            log.warn("台账未知设备反查失败：{}", e.getMessage());
        }
    }
}
