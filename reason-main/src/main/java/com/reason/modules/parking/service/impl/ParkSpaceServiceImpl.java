package com.reason.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.utils.Constant;
import com.reason.common.exception.RRException;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.parking.dao.ParkSpaceDao;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.enums.ParkSpaceState;
import com.reason.modules.parking.form.ParkSpaceForm;
import com.reason.modules.parking.service.ParkSpaceService;
import com.reason.modules.parking.vo.ParkSpaceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 车位台账服务实现
 *
 * <p>唯一性模型与入场同构：业务层预查重是快速失败层（语义化错误），
 * DB 唯一索引 u_space_no 是最终兜底（并发窗口内撞索引转业务异常）。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Service("parkSpaceService")
public class ParkSpaceServiceImpl extends ServiceImpl<ParkSpaceDao, ParkSpaceEntity>
        implements ParkSpaceService {

    @Autowired
    private ParkSpaceDao parkSpaceDao;

    @Override
    public void saveSpace(ParkSpaceVO vo, Long operatorUserId) {
        if (vo == null) {
            throw new RRException("参数不能为空");
        }
        //1.规范化：编号统一大写（设备/手工录入口径一致）
        String spaceNo = normalize(vo.getSpaceNo(), "车位编号不能为空");
        //2.状态校验：新增仅允许 空闲/禁用（占用只能由入场事务产生，人工建档不可伪造占用态）
        Integer state = vo.getSpaceState() == null ? ParkSpaceState.IDLE.getCode() : vo.getSpaceState();
        if (state != ParkSpaceState.IDLE.getCode() && state != ParkSpaceState.DISABLED.getCode()) {
            throw new RRException("新增车位状态仅可为空闲或禁用");
        }

        long now = System.currentTimeMillis() / 1000;

        //3.编号唯一预查重（快速失败层）
        assertSpaceNoUnique(spaceNo, null);

        //4.落库；撞唯一索引（并发窗口内）转业务异常
        ParkSpaceEntity space = new ParkSpaceEntity();
        space.setSpaceNo(spaceNo);
        space.setSpaceArea(vo.getSpaceArea());
        space.setSpaceState(state);
        space.setSpaceCreator(operatorUserId);
        space.setSpaceCreatetime(now);
        space.setSpaceUpdatetime(now);
        try {
            parkSpaceDao.insert(space);
        } catch (DuplicateKeyException e) {
            throw new RRException("车位编号已存在：" + spaceNo);
        }
    }

    @Override
    public void updateSpace(ParkSpaceVO vo, Long operatorUserId) {
        if (vo == null || vo.getSpaceId() == null) {
            throw new RRException("车位id不能为空");
        }
        //1.查当前车位：不存在直接拒绝
        ParkSpaceEntity current = parkSpaceDao.selectById(vo.getSpaceId());
        if (current == null) {
            throw new RRException("车位不存在：" + vo.getSpaceId());
        }
        //2.占用中禁止一切编辑/禁用：编号漂移破坏进行中会话的冗余一致性；
        //  占用中禁用则是逻辑矛盾（车还停着位已停用）
        if (ParkSpaceState.of(current.getSpaceState()) == ParkSpaceState.OCCUPIED) {
            throw new RRException("车位占用中，不能编辑或禁用：" + current.getSpaceNo());
        }

        //3.规范化与状态校验（占用态不可经管理端手动置位）
        String spaceNo = normalize(vo.getSpaceNo(), "车位编号不能为空");
        Integer state = vo.getSpaceState() == null ? current.getSpaceState() : vo.getSpaceState();
        if (state != ParkSpaceState.IDLE.getCode() && state != ParkSpaceState.DISABLED.getCode()) {
            throw new RRException("车位状态仅可为空闲或禁用（占用由入场事务产生）");
        }

        //4.改号时唯一预查重（排除自身）
        if (!spaceNo.equals(current.getSpaceNo())) {
            assertSpaceNoUnique(spaceNo, vo.getSpaceId());
        }

        //5.全量更新（编号/区域/状态 0↔2 互切）
        long now = System.currentTimeMillis() / 1000;
        ParkSpaceEntity space = new ParkSpaceEntity();
        space.setSpaceId(vo.getSpaceId());
        space.setSpaceNo(spaceNo);
        space.setSpaceArea(vo.getSpaceArea());
        space.setSpaceState(state);
        space.setSpaceUpdatetime(now);
        try {
            parkSpaceDao.updateById(space);
        } catch (DuplicateKeyException e) {
            throw new RRException("车位编号已存在：" + spaceNo);
        }
    }

    @Override
    public PageUtils queryPage(ParkSpaceForm form) {
        IPage<ParkSpaceEntity> page = new Query<ParkSpaceEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        parkSpaceDao.selectPage(page, new LambdaQueryWrapper<ParkSpaceEntity>()
                        .like(StringUtils.hasText(form.getSpaceNo()), ParkSpaceEntity::getSpaceNo, form.getSpaceNo())
                        .like(StringUtils.hasText(form.getSpaceArea()), ParkSpaceEntity::getSpaceArea, form.getSpaceArea())
                        .eq(form.getSpaceState() != null, ParkSpaceEntity::getSpaceState, form.getSpaceState())
                        .orderByAsc(ParkSpaceEntity::getSpaceId));
        return new PageUtils(page);
    }

    /** 编号唯一预查重（快速失败层；updateId 非空时排除自身） */
    private void assertSpaceNoUnique(String spaceNo, Long excludeSpaceId) {
        Long count = parkSpaceDao.selectCount(new LambdaQueryWrapper<ParkSpaceEntity>()
                .eq(ParkSpaceEntity::getSpaceNo, spaceNo)
                .ne(excludeSpaceId != null, ParkSpaceEntity::getSpaceId, excludeSpaceId));
        if (count != null && count > 0) {
            throw new RRException("车位编号已存在：" + spaceNo);
        }
    }

    private String normalize(String value, String emptyMsg) {
        if (value == null || value.trim().isEmpty()) {
            throw new RRException(emptyMsg);
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
