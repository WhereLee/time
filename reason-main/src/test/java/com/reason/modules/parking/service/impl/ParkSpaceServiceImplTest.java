package com.reason.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reason.common.exception.RRException;
import com.reason.common.utils.PageUtils;
import com.reason.modules.parking.dao.ParkSpaceDao;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.enums.ParkSpaceState;
import com.reason.modules.parking.form.ParkSpaceForm;
import com.reason.modules.parking.vo.ParkSpaceVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 车位台账服务测试：唯一性双保险 + 占用中禁编辑规则 + 状态机不可手动置位
 *
 * @date 2026-09-05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("车位台账服务")
class ParkSpaceServiceImplTest {

    private static final long SPACE_ID = 1L;
    private static final long OPERATOR = 10L;

    @Mock
    private ParkSpaceDao parkSpaceDao;
    @InjectMocks
    private ParkSpaceServiceImpl parkSpaceService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ParkSpaceEntity.class);
    }

    // ---------- 新增 ----------

    @Test
    @DisplayName("新增成功：编号统一大写、默认空闲、建档人/时间戳落位")
    void 新增成功() {
        when(parkSpaceDao.selectCount(any())).thenReturn(0L);

        ParkSpaceVO vo = new ParkSpaceVO();
        vo.setSpaceNo("a-001");
        vo.setSpaceArea("A区-东侧");
        parkSpaceService.saveSpace(vo, OPERATOR);

        ArgumentCaptor<ParkSpaceEntity> captor = ArgumentCaptor.forClass(ParkSpaceEntity.class);
        verify(parkSpaceDao).insert(captor.capture());
        ParkSpaceEntity space = captor.getValue();
        assertThat(space.getSpaceNo()).isEqualTo("A-001");
        assertThat(space.getSpaceState()).isEqualTo(ParkSpaceState.IDLE.getCode());
        assertThat(space.getSpaceCreator()).isEqualTo(OPERATOR);
        assertThat(space.getSpaceCreatetime()).isNotNull();
        assertThat(space.getSpaceUpdatetime()).isNotNull();
    }

    @Test
    @DisplayName("新增：编号重复（预查重）→ 拒绝")
    void 新增编号重复() {
        when(parkSpaceDao.selectCount(any())).thenReturn(1L);

        ParkSpaceVO vo = voWithNo("A-001");
        assertThatThrownBy(() -> parkSpaceService.saveSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位编号已存在");
        verify(parkSpaceDao, never()).insert(any(ParkSpaceEntity.class));
    }

    @Test
    @DisplayName("新增：编号为空 → 拒绝")
    void 新增编号为空() {
        ParkSpaceVO vo = new ParkSpaceVO();
        assertThatThrownBy(() -> parkSpaceService.saveSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位编号不能为空");
    }

    @Test
    @DisplayName("新增：状态置为占用 → 拒绝（占用仅由入场事务产生）")
    void 新增禁止伪造占用态() {
        ParkSpaceVO vo = voWithNo("A-002");
        vo.setSpaceState(ParkSpaceState.OCCUPIED.getCode());
        assertThatThrownBy(() -> parkSpaceService.saveSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("仅可为空闲或禁用");
    }

    @Test
    @DisplayName("新增：并发窗口内撞唯一索引 → DB 兜底转业务异常")
    void 新增撞唯一索引兜底() {
        when(parkSpaceDao.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("dup")).when(parkSpaceDao).insert(any(ParkSpaceEntity.class));

        ParkSpaceVO vo = voWithNo("A-001");
        assertThatThrownBy(() -> parkSpaceService.saveSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位编号已存在");
    }

    // ---------- 修改 ----------

    @Test
    @DisplayName("修改成功：全量更新（改号/区域/状态 0→2 互切）+ 更新时间戳")
    void 修改成功() {
        when(parkSpaceDao.selectById(SPACE_ID)).thenReturn(idleSpace());

        ParkSpaceVO vo = voWithNo("A-001");
        vo.setSpaceId(SPACE_ID);
        vo.setSpaceArea("B区");
        vo.setSpaceState(ParkSpaceState.DISABLED.getCode());   //禁用=删除
        parkSpaceService.updateSpace(vo, OPERATOR);

        ArgumentCaptor<ParkSpaceEntity> captor = ArgumentCaptor.forClass(ParkSpaceEntity.class);
        verify(parkSpaceDao).updateById(captor.capture());
        ParkSpaceEntity space = captor.getValue();
        assertThat(space.getSpaceNo()).isEqualTo("A-001");
        assertThat(space.getSpaceState()).isEqualTo(ParkSpaceState.DISABLED.getCode());
        assertThat(space.getSpaceArea()).isEqualTo("B区");
        assertThat(space.getSpaceUpdatetime()).isNotNull();
    }

    @Test
    @DisplayName("修改：车位占用中 → 拒绝一切编辑与禁用")
    void 修改占用中拒绝() {
        when(parkSpaceDao.selectById(SPACE_ID)).thenReturn(spaceWithState(ParkSpaceState.OCCUPIED));

        ParkSpaceVO vo = voWithNo("A-001");
        vo.setSpaceId(SPACE_ID);
        assertThatThrownBy(() -> parkSpaceService.updateSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("占用中");
        verify(parkSpaceDao, never()).updateById(any(ParkSpaceEntity.class));
    }

    @Test
    @DisplayName("修改：改号撞重（预查重）→ 拒绝")
    void 修改改号撞重() {
        when(parkSpaceDao.selectById(SPACE_ID)).thenReturn(idleSpace());
        when(parkSpaceDao.selectCount(any())).thenReturn(1L);

        ParkSpaceVO vo = voWithNo("A-999");
        vo.setSpaceId(SPACE_ID);
        assertThatThrownBy(() -> parkSpaceService.updateSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位编号已存在");
        verify(parkSpaceDao, never()).updateById(any(ParkSpaceEntity.class));
    }

    @Test
    @DisplayName("修改：车位不存在 → 拒绝")
    void 修改车位不存在() {
        when(parkSpaceDao.selectById(SPACE_ID)).thenReturn(null);

        ParkSpaceVO vo = voWithNo("A-001");
        vo.setSpaceId(SPACE_ID);
        assertThatThrownBy(() -> parkSpaceService.updateSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位不存在");
    }

    @Test
    @DisplayName("修改：状态置为占用 → 拒绝")
    void 修改禁止伪造占用态() {
        when(parkSpaceDao.selectById(SPACE_ID)).thenReturn(idleSpace());

        ParkSpaceVO vo = voWithNo("A-001");
        vo.setSpaceId(SPACE_ID);
        vo.setSpaceState(ParkSpaceState.OCCUPIED.getCode());
        assertThatThrownBy(() -> parkSpaceService.updateSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("占用由入场事务产生");
    }

    @Test
    @DisplayName("修改：改号撞唯一索引（并发窗口）→ DB 兜底转业务异常")
    void 修改撞唯一索引兜底() {
        when(parkSpaceDao.selectById(SPACE_ID)).thenReturn(idleSpace());
        when(parkSpaceDao.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("dup")).when(parkSpaceDao).updateById(any(ParkSpaceEntity.class));

        ParkSpaceVO vo = voWithNo("A-999");
        vo.setSpaceId(SPACE_ID);
        assertThatThrownBy(() -> parkSpaceService.updateSpace(vo, OPERATOR))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位编号已存在");
    }

    // ---------- 分页 ----------

    @Test
    @DisplayName("分页：条件筛选不崩且返回结构正确")
    void 分页查询() {
        IPage<ParkSpaceEntity> page = new Page<>(1, 10);
        when(parkSpaceDao.selectPage(any(), any())).thenReturn(page);

        ParkSpaceForm form = new ParkSpaceForm();
        form.setSpaceNo("A");
        form.setSpaceState(ParkSpaceState.IDLE.getCode());
        PageUtils result = parkSpaceService.queryPage(form);

        assertThat(result.getTotalCount()).isEqualTo(0);
        assertThat(result.getList()).isEmpty();
    }

    // ---------- helpers ----------

    private ParkSpaceVO voWithNo(String spaceNo) {
        ParkSpaceVO vo = new ParkSpaceVO();
        vo.setSpaceNo(spaceNo);
        return vo;
    }

    private ParkSpaceEntity idleSpace() {
        return spaceWithState(ParkSpaceState.IDLE);
    }

    private ParkSpaceEntity spaceWithState(ParkSpaceState state) {
        ParkSpaceEntity space = new ParkSpaceEntity();
        space.setSpaceId(SPACE_ID);
        space.setSpaceNo("A-001");
        space.setSpaceState(state.getCode());
        return space;
    }
}
