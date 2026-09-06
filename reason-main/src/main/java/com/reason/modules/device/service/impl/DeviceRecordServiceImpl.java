package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.StringUtils;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceRecordForm;
import com.reason.modules.device.service.DeviceRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设备台账服务实现
 *
 * <p>块1 只落"档案"：登记 + 分页查询。登记建档状态恒为 0-未接入——状态是设备说的，
 * 不是登记人填的（反馈闭环铁律的起点，后续块由事件接收侧驱动更新）。</p>
 */
@Slf4j
@Service("deviceRecordService")
public class DeviceRecordServiceImpl extends ServiceImpl<DeviceRecordDao, DeviceRecordEntity>
        implements DeviceRecordService {

    @Override
    public PageUtils queryPage(DeviceRecordForm form) {
        int pageNum = form.getPage() == null ? 1 : Integer.parseInt(form.getPage());
        int limit = form.getLimit() == null ? 10 : Integer.parseInt(form.getLimit());

        IPage<DeviceRecordEntity> page = this.page(
                new Page<>(pageNum, limit),
                new LambdaQueryWrapper<DeviceRecordEntity>()
                        .like(StringUtils.isNotBlank(form.getDeviceNo()), DeviceRecordEntity::getDeviceNo, form.getDeviceNo())
                        .like(StringUtils.isNotBlank(form.getDeviceName()), DeviceRecordEntity::getDeviceName, form.getDeviceName())
                        .eq(form.getDeviceType() != null, DeviceRecordEntity::getDeviceType, form.getDeviceType())
                        .eq(form.getDeviceState() != null, DeviceRecordEntity::getDeviceState, form.getDeviceState())
                        .orderByDesc(DeviceRecordEntity::getDeviceCreatetime)
        );
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
        entity.setDeviceRemark(form.getDeviceRemark());
        entity.setDeviceCreator(userId);
        long now = System.currentTimeMillis() / 1000;
        entity.setDeviceCreatetime(now);
        entity.setDeviceUpdatetime(now);
        this.save(entity);
    }

    @Override
    public void updateStateByEvent(String deviceNo, int stateCode) {
        //档案必须存在：未登记设备的上报 = 配置错位（模拟器里有、平台没建档），显式失败让设备侧发现
        DeviceRecordEntity record = this.getOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (record == null) {
            throw new RRException("未登记的设备上报事件: " + deviceNo);
        }
        //只更新状态与更新时间（单一时钟源：时间戳以服务器为准）；相同状态重复上报幂等无害
        DeviceRecordEntity update = new DeviceRecordEntity();
        update.setDeviceId(record.getDeviceId());
        update.setDeviceState(stateCode);
        update.setDeviceUpdatetime(System.currentTimeMillis() / 1000);
        this.updateById(update);
        log.info("设备事件驱动状态更新 deviceNo={} state={}", deviceNo, stateCode);
    }
}
