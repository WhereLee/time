package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.device.dao.DeviceOnlineDao;
import com.reason.modules.device.dao.GateManualOpDao;
import com.reason.modules.device.entity.DeviceOnlineEntity;
import com.reason.modules.device.entity.GateManualOpEntity;
import com.reason.modules.device.enums.DeviceType;
import com.reason.modules.device.form.GateManualOpForm;
import com.reason.modules.device.service.GateManualOpService;
import com.reason.modules.parking.service.DeviceCommandClient;
import com.reason.modules.sys.entity.SysUserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 闸机人工操作服务实现
 *
 * <p>手动抬杆闭环：① 目标必须是出入口闸机（台账校验，位检/桩拒绝抬杆语义）；
 * ② 指令下发 fire-and-forget（设备不可达不抛异常）；③ 结果无论成败落留痕表——
 * 审计记录先行于设备反馈，防止人工放行变成账外通道。</p>
 *
 * @date 2026-09-06
 */
@Slf4j
@Service
public class GateManualOpServiceImpl implements GateManualOpService {

    private final DeviceOnlineDao deviceOnlineDao;
    private final GateManualOpDao gateManualOpDao;
    private final DeviceCommandClient deviceCommandClient;

    public GateManualOpServiceImpl(DeviceOnlineDao deviceOnlineDao,
                                   GateManualOpDao gateManualOpDao,
                                   DeviceCommandClient deviceCommandClient) {
        this.deviceOnlineDao = deviceOnlineDao;
        this.gateManualOpDao = gateManualOpDao;
        this.deviceCommandClient = deviceCommandClient;
    }

    @Override
    public boolean liftGate(String deviceNo, String plateNo, String reason, SysUserEntity operator) {
        if (!StringUtils.hasText(deviceNo)) {
            throw new RRException("设备编号不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw new RRException("操作原因必录（审计要求：设备故障/特殊放行/收费争议等）");
        }
        DeviceOnlineEntity target = deviceOnlineDao.selectOne(new LambdaQueryWrapper<DeviceOnlineEntity>()
                .eq(DeviceOnlineEntity::getDeviceNo, deviceNo));
        if (target == null) {
            throw new RRException("设备不存在：" + deviceNo);
        }
        DeviceType type = DeviceType.of(target.getDeviceType());
        if (type != DeviceType.ENTRY_GATE && type != DeviceType.EXIT_GATE) {
            throw new RRException("非闸机设备不支持手动抬杆：" + deviceNo + "（类型=" + type.getDesc() + "）");
        }

        boolean sent = deviceCommandClient.sendDeviceCommand(DeviceCommandClient.CMD_OPEN_GATE, deviceNo);

        //留痕：成败都落（审计优先于设备反馈；设备不可达留现场由管理端人工跟进）
        GateManualOpEntity op = new GateManualOpEntity();
        op.setDeviceNo(deviceNo);
        op.setGateCode(target.getBindTarget());
        op.setOpType(1);
        op.setPlateNo(StringUtils.hasText(plateNo) ? plateNo.trim() : null);
        op.setOpReason(reason.trim());
        op.setOpResult(sent ? 0 : 1);
        op.setOpRemark(sent ? null : "指令下发失败：设备不可达或超时（可到设备台账核对在线态）");
        op.setOperatorId(operator.getUserId());
        op.setOperatorName(operator.getUserName());
        op.setOpCreatetime(System.currentTimeMillis() / 1000);
        gateManualOpDao.insert(op);
        log.info("手动抬杆留痕：deviceNo={}, result={}, operator={}, reason={}",
                deviceNo, sent ? "成功" : "设备不可达", operator.getUserName(), reason);
        return sent;
    }

    @Override
    public PageUtils queryPage(GateManualOpForm form) {
        IPage<GateManualOpEntity> page = new Query<GateManualOpEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        gateManualOpDao.selectPage(page, new LambdaQueryWrapper<GateManualOpEntity>()
                .like(StringUtils.hasText(form.getDeviceNo()), GateManualOpEntity::getDeviceNo, form.getDeviceNo())
                .eq(StringUtils.hasText(form.getPlateNo()), GateManualOpEntity::getPlateNo, form.getPlateNo())
                .eq(form.getOpResult() != null, GateManualOpEntity::getOpResult, form.getOpResult())
                .orderByDesc(GateManualOpEntity::getOpCreatetime));
        return new PageUtils(page);
    }
}
