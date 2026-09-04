package com.reason.modules.parking.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.form.ParkSpaceForm;
import com.reason.modules.parking.service.ParkSpaceService;
import com.reason.modules.parking.vo.ParkSpaceVO;
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
 * 车位管理（无物理删除：禁用=删除）
 *
 * <p>权限串与 sys_menu 200-203 注册的 park:space:* 一一对应；
 * 分页查询为高频低价值操作，不记操作日志（写操作经 @SysLog 审计）。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "车位管理")
@RestController
@RequestMapping("parking/space")
public class ParkSpaceController extends AbstractController {

    @Autowired
    private ParkSpaceService parkSpaceService;

    /**
     * 车位分页查询
     */
    @Operation(summary = "车位分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('park:space:list')")
    public Result<PageUtils> page(ParkSpaceForm form) {
        return Result.ok(parkSpaceService.queryPage(form));
    }

    /**
     * 车位详情
     */
    @Operation(summary = "车位详情")
    @ApiOperationSupport(order = 2)
    @GetMapping("/info/{spaceId}")
    @PreAuthorize("hasAuthority('park:space:info')")
    public Result<ParkSpaceEntity> info(@PathVariable("spaceId") Long spaceId) {
        return Result.ok(parkSpaceService.getById(spaceId));
    }

    /**
     * 新增车位
     */
    @Operation(summary = "新增车位")
    @ApiOperationSupport(order = 3)
    @SysLog(module = "停车管理", func = "新增", value = "新增车位")
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('park:space:save')")
    public Result save(@RequestBody ParkSpaceVO vo) {
        parkSpaceService.saveSpace(vo, getUserId());
        return Result.ok();
    }

    /**
     * 修改车位（占用中禁止编辑/禁用）
     */
    @Operation(summary = "修改车位")
    @ApiOperationSupport(order = 4)
    @SysLog(module = "停车管理", func = "修改", value = "修改车位")
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('park:space:update')")
    public Result update(@RequestBody ParkSpaceVO vo) {
        parkSpaceService.updateSpace(vo, getUserId());
        return Result.ok();
    }
}
