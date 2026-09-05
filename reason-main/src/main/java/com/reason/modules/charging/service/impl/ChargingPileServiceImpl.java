package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.charging.dao.ChargingPileDao;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.enums.PileState;
import com.reason.modules.charging.form.ChargingPileForm;
import com.reason.modules.charging.form.ChargingPileVO;
import com.reason.modules.charging.service.ChargingPileService;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.enums.ParkSpaceState;
import com.reason.modules.parking.service.ParkSessionService;
import com.reason.modules.parking.service.ParkSpaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 充电桩台账服务实现
 *
 * <p>绑定位校验走 parking 跨上下文能力（IService 只读 + 进行中会话查询），不直连 park_space 表；
 * 唯一性双保险（预查重快速失败 + DB 唯一索引兜底）与 P4 车位台账同模式。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Service("chargingPileService")
public class ChargingPileServiceImpl extends ServiceImpl<ChargingPileDao, ChargingPileEntity>
        implements ChargingPileService {

    @Autowired
    private ChargingPileDao chargingPileDao;

    /**
     * 跨上下文只读能力：车位存在性/停用态（IService 通用查询，不直连表）
     */
    @Autowired
    private ParkSpaceService parkSpaceService;

    /**
     * 跨上下文只读能力：车位进行中停车会话（充电位绑定须车位空闲）
     */
    @Autowired
    private ParkSessionService parkSessionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePile(ChargingPileVO vo, Long operatorUserId) {
        String pileNo = normalize(vo.getPileNo(), "桩编号不能为空");
        if (vo.getSpaceId() == null) {
            throw new RRException("绑定车位不能为空");
        }
        //车位校验：存在 + 未停用 + 无进行中停车（装桩施工语义：占用中禁绑）
        assertBindableSpace(vo.getSpaceId());

        //编号唯一预查重（快速失败层；DB 唯一索引为最终判官）
        Long dup = chargingPileDao.selectCount(new LambdaQueryWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getPileNo, pileNo));
        if (dup != null && dup > 0) {
            throw new RRException("桩编号已存在：" + pileNo);
        }

        long now = System.currentTimeMillis() / 1000;
        ChargingPileEntity pile = new ChargingPileEntity();
        pile.setPileNo(pileNo);
        pile.setSpaceId(vo.getSpaceId());
        pile.setPileState(PileState.IDLE.getCode());
        pile.setPileCreator(operatorUserId);
        pile.setPileCreatetime(now);
        chargingPileDao.insert(pile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePile(ChargingPileVO vo, Long operatorUserId) {
        if (vo.getPileId() == null) {
            throw new RRException("桩id不能为空");
        }
        ChargingPileEntity pile = chargingPileDao.selectById(vo.getPileId());
        if (pile == null) {
            throw new RRException("充电桩不存在：" + vo.getPileId());
        }
        //充电中禁编辑（含停用/改绑/换号）：充电中状态仅由充电会话事务产生与释放
        if (PileState.of(pile.getPileState()) == PileState.CHARGING) {
            throw new RRException("桩充电中，禁止编辑：请先结束充电（桩编号：" + pile.getPileNo() + "）");
        }
        if (vo.getPileState() != null && vo.getPileState() == PileState.CHARGING.getCode()) {
            throw new RRException("充电中状态不接受手动置位");
        }

        String pileNo = normalize(vo.getPileNo(), "桩编号不能为空");
        if (vo.getSpaceId() == null) {
            throw new RRException("绑定车位不能为空");
        }
        //改绑车位时校验新车位；车位不变时跳过（避免自身占用误伤）
        if (!vo.getSpaceId().equals(pile.getSpaceId())) {
            assertBindableSpace(vo.getSpaceId());
        }
        //编号唯一预查重（排除自身）
        Long dup = chargingPileDao.selectCount(new LambdaQueryWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getPileNo, pileNo)
                .ne(ChargingPileEntity::getPileId, vo.getPileId()));
        if (dup != null && dup > 0) {
            throw new RRException("桩编号已存在：" + pileNo);
        }
        //状态只接受 0-空闲/2-停用（充电中由会话事务管理）
        int targetState = vo.getPileState() == null ? pile.getPileState() : vo.getPileState();
        if (targetState != PileState.IDLE.getCode() && targetState != PileState.DISABLED.getCode()) {
            throw new RRException("非法的桩状态：" + targetState);
        }

        ChargingPileEntity update = new ChargingPileEntity();
        update.setPileId(vo.getPileId());
        update.setPileNo(pileNo);
        update.setSpaceId(vo.getSpaceId());
        update.setPileState(targetState);
        update.setPileUpdatetime(System.currentTimeMillis() / 1000);
        chargingPileDao.updateById(update);
    }

    @Override
    public PageUtils queryPage(ChargingPileForm form) {
        IPage<ChargingPileEntity> page = new Query<ChargingPileEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        chargingPileDao.selectPage(page, new LambdaQueryWrapper<ChargingPileEntity>()
                .like(org.springframework.util.StringUtils.hasText(form.getPileNo()),
                        ChargingPileEntity::getPileNo, form.getPileNo())
                .eq(form.getSpaceId() != null, ChargingPileEntity::getSpaceId, form.getSpaceId())
                .eq(form.getPileState() != null, ChargingPileEntity::getPileState, form.getPileState())
                .orderByDesc(ChargingPileEntity::getPileId));
        return new PageUtils(page);
    }

    /**
     * 绑定位校验：车位存在 + 未停用 + 无进行中停车会话（占用中禁绑=装桩施工语义）
     */
    private void assertBindableSpace(Long spaceId) {
        ParkSpaceEntity space = parkSpaceService.getById(spaceId);
        if (space == null) {
            throw new RRException("绑定车位不存在：" + spaceId);
        }
        if (ParkSpaceState.of(space.getSpaceState()) == ParkSpaceState.DISABLED) {
            throw new RRException("绑定车位已禁用，不能绑桩：" + space.getSpaceNo());
        }
        if (parkSessionService.getOngoingBySpaceId(spaceId) != null) {
            throw new RRException("绑定车位有进行中停车会话，不能绑桩：" + space.getSpaceNo());
        }
    }

    private String normalize(String value, String emptyMsg) {
        if (value == null || value.trim().isEmpty()) {
            throw new RRException(emptyMsg);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
