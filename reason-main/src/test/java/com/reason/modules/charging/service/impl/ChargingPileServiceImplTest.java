package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.common.exception.RRException;
import com.reason.modules.charging.dao.ChargingPileDao;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.enums.PileState;
import com.reason.modules.charging.form.ChargingPileVO;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.enums.ParkSpaceState;
import com.reason.modules.parking.service.ParkSessionService;
import com.reason.modules.parking.service.ParkSpaceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充电桩台账服务单元测试（建档/编辑规则矩阵）
 *
 * <p>绑定规则：车位存在 + 未停用 + 无进行中停车会话；充电中禁编辑；编号唯一双保险。</p>
 */
@DisplayName("充电桩台账服务")
@ExtendWith(MockitoExtension.class)
class ChargingPileServiceImplTest {

    private static final long PILE_ID = 1L;
    private static final String PILE_NO = "PILE-001";
    private static final long SPACE_ID = 10L;

    @Mock
    private ChargingPileDao chargingPileDao;
    @Mock
    private ParkSpaceService parkSpaceService;
    @Mock
    private ParkSessionService parkSessionService;
    @InjectMocks
    private ChargingPileServiceImpl chargingPileService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChargingPileEntity.class);
    }

    @Test
    @DisplayName("建档成功：编号大写归一 + 车位可绑 + 初始空闲态")
    void 建档成功() {
        when(parkSpaceService.getById(SPACE_ID)).thenReturn(space(ParkSpaceState.IDLE));
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID)).thenReturn(null);
        when(chargingPileDao.selectCount(any())).thenReturn(0L);

        chargingPileService.savePile(vo(null, " pile-001 ", SPACE_ID, null), 2L);

        ArgumentCaptor<ChargingPileEntity> captor = ArgumentCaptor.forClass(ChargingPileEntity.class);
        verify(chargingPileDao).insert(captor.capture());
        ChargingPileEntity inserted = captor.getValue();
        assertThat(inserted.getPileNo()).isEqualTo(PILE_NO);
        assertThat(inserted.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(inserted.getPileState()).isEqualTo(PileState.IDLE.getCode());
        assertThat(inserted.getPileCreator()).isEqualTo(2L);
    }

    @Test
    @DisplayName("建档：编号为空/车位为空 → 业务异常")
    void 建档入参为空() {
        assertThatThrownBy(() -> chargingPileService.savePile(vo(null, "  ", SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("桩编号不能为空");
        assertThatThrownBy(() -> chargingPileService.savePile(vo(null, PILE_NO, null, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("绑定车位不能为空");
    }

    @Test
    @DisplayName("建档：车位不存在/已停用/占用中 → 均拒绝绑桩")
    void 建档车位不可绑() {
        when(parkSpaceService.getById(SPACE_ID)).thenReturn(null);
        assertThatThrownBy(() -> chargingPileService.savePile(vo(null, PILE_NO, SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位不存在");

        when(parkSpaceService.getById(SPACE_ID)).thenReturn(space(ParkSpaceState.DISABLED));
        assertThatThrownBy(() -> chargingPileService.savePile(vo(null, PILE_NO, SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已禁用");

        when(parkSpaceService.getById(SPACE_ID)).thenReturn(space(ParkSpaceState.IDLE));
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID))
                .thenReturn(new ParkSessionService.OngoingParkSession(100L, "浙B12345", "B-001"));
        assertThatThrownBy(() -> chargingPileService.savePile(vo(null, PILE_NO, SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("进行中停车会话");
    }

    @Test
    @DisplayName("建档：编号重复 → 拒绝（DB 唯一索引为最终判官）")
    void 建档编号重复() {
        when(parkSpaceService.getById(SPACE_ID)).thenReturn(space(ParkSpaceState.IDLE));
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID)).thenReturn(null);
        when(chargingPileDao.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> chargingPileService.savePile(vo(null, PILE_NO, SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("编辑成功：停用（0→2）+ 改绑车位 + 编号不变时不重复校验占用")
    void 编辑成功() {
        ChargingPileEntity pile = pileEntity(PileState.IDLE);
        when(chargingPileDao.selectById(PILE_ID)).thenReturn(pile);
        when(chargingPileDao.selectCount(any())).thenReturn(0L);

        chargingPileService.updatePile(vo(PILE_ID, PILE_NO, SPACE_ID, PileState.DISABLED.getCode()), 2L);

        ArgumentCaptor<ChargingPileEntity> captor = ArgumentCaptor.forClass(ChargingPileEntity.class);
        verify(chargingPileDao).updateById(captor.capture());
        ChargingPileEntity updated = captor.getValue();
        assertThat(updated.getPileState()).isEqualTo(PileState.DISABLED.getCode());
        assertThat(updated.getPileNo()).isEqualTo(PILE_NO);
        assertThat(updated.getSpaceId()).isEqualTo(SPACE_ID);
    }

    @Test
    @DisplayName("编辑：桩不存在 → 业务异常")
    void 编辑桩不存在() {
        when(chargingPileDao.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> chargingPileService.updatePile(vo(999L, PILE_NO, SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("充电桩不存在");
    }

    @Test
    @DisplayName("编辑：充电中禁编辑（含停用/改绑）→ 业务异常")
    void 编辑充电中禁止() {
        when(chargingPileDao.selectById(PILE_ID)).thenReturn(pileEntity(PileState.CHARGING));

        assertThatThrownBy(() -> chargingPileService.updatePile(vo(PILE_ID, PILE_NO, SPACE_ID, PileState.DISABLED.getCode()), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("充电中，禁止编辑");
    }

    @Test
    @DisplayName("编辑：手动置位充电中状态 → 拒绝（充电中由会话事务管理）")
    void 编辑手动置位充电中被拒() {
        when(chargingPileDao.selectById(PILE_ID)).thenReturn(pileEntity(PileState.IDLE));

        assertThatThrownBy(() -> chargingPileService.updatePile(vo(PILE_ID, PILE_NO, SPACE_ID, PileState.CHARGING.getCode()), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("不接受手动置位");
    }

    @Test
    @DisplayName("编辑：改绑到不可绑车位 → 拒绝（仅车位变化时校验）")
    void 编辑改绑校验() {
        ChargingPileEntity pile = pileEntity(PileState.IDLE);
        pile.setSpaceId(999L);   //原绑车位
        when(chargingPileDao.selectById(PILE_ID)).thenReturn(pile);
        when(parkSpaceService.getById(SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> chargingPileService.updatePile(vo(PILE_ID, PILE_NO, SPACE_ID, null), 2L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位不存在");
    }

    // ---------- 夹具 ----------

    private ChargingPileVO vo(Long pileId, String pileNo, Long spaceId, Integer state) {
        ChargingPileVO vo = new ChargingPileVO();
        vo.setPileId(pileId);
        vo.setPileNo(pileNo);
        vo.setSpaceId(spaceId);
        vo.setPileState(state);
        return vo;
    }

    private ParkSpaceEntity space(ParkSpaceState state) {
        ParkSpaceEntity space = new ParkSpaceEntity();
        space.setSpaceId(SPACE_ID);
        space.setSpaceNo("B-001");
        space.setSpaceState(state.getCode());
        return space;
    }

    private ChargingPileEntity pileEntity(PileState state) {
        ChargingPileEntity pile = new ChargingPileEntity();
        pile.setPileId(PILE_ID);
        pile.setPileNo(PILE_NO);
        pile.setSpaceId(SPACE_ID);
        pile.setPileState(state.getCode());
        return pile;
    }
}
