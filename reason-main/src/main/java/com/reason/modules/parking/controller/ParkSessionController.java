package com.reason.modules.parking.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.parking.form.ParkSessionForm;
import com.reason.modules.parking.service.ParkSessionService;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 停车会话查询（管理端只读：会话由设备入场/取消/出场事务产生，不提供写接口）
 *
 * <p>权限串与 sys_menu 205 注册的 park:session:list 对应。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "停车会话查询")
@RestController
@RequestMapping("parking/session")
public class ParkSessionController extends AbstractController {

    @Autowired
    private ParkSessionService parkSessionService;

    /**
     * 会话分页查询
     */
    @Operation(summary = "会话分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('park:session:list')")
    public Result<PageUtils> page(ParkSessionForm form) {
        return Result.ok(parkSessionService.queryPage(form));
    }
}
