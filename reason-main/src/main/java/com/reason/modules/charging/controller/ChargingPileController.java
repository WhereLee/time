package com.reason.modules.charging.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.service.ChargingPileService;
import com.reason.modules.charging.form.ChargingPileForm;
import com.reason.modules.charging.service.ChargingPileService;
import com.reason.modules.charging.form.ChargingPileVO;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 桩位台账管理（无物理删除：停用=删除；充电中禁编辑）

<p>权限串与 sys_menu 301-304 注册的 charge:pile:* 一一对应；
分页查询为高频低价值操作，不记操作日志（写操作经 @SysLog 审计）。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "桩位管理")
@RestController
@RequestMapping("charging/pile")
public class ChargingPileController extends AbstractController {

    @Autowired
    private ChargingPileService chargingPileService;

    /**
     * 桩分页查询
     */
    @Operation(summary = "桩分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('charge:pile:list')")
    public Result<PageUtils> page(ChargingPileForm form) {
        return Result.ok(chargingPileService.queryPage(form));
    }

    /**
     * 桩详情
     */
    @Operation(summary = "桩详情")
    @ApiOperationSupport(order = 2)
    @GetMapping("/info/{pileId}")
    @PreAuthorize("hasAuthority('charge:pile:info')")
    public Result<ChargingPileEntity> info(@PathVariable("pileId") Long pileId) {
        return Result.ok(chargingPileService.getById(pileId));
    }

    /**
     * 新增桩（建档绑车位）
     */
    @Operation(summary = "新增桩")
    @ApiOperationSupport(order = 3)
    @SysLog(module = "充电管理", func = "新增", value = "新增充电桩")
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('charge:pile:save')")
    public Result save(@RequestBody ChargingPileVO vo) {
        chargingPileService.savePile(vo, getUserId());
        return Result.ok();
    }

    /**
     * 修改桩（充电中禁止编辑/停用）
     */
    @Operation(summary = "修改桩")
    @ApiOperationSupport(order = 4)
    @SysLog(module = "充电管理", func = "修改", value = "修改充电桩")
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('charge:pile:update')")
    public Result update(@RequestBody ChargingPileVO vo) {
        chargingPileService.updatePile(vo, getUserId());
        return Result.ok();
    }
}
