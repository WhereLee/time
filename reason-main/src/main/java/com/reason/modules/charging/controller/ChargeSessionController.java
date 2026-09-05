package com.reason.modules.charging.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.charging.entity.ChargeSessionEntity;
import com.reason.modules.charging.service.ChargeSessionService;
import com.reason.modules.charging.form.ChargeSessionForm;
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
 * 充电会话查询（管理端只读：会话由设备上报事务生成后不可变更）

<p>权限串与 sys_menu 305 注册的 charge:session:list 对应。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "充电会话")
@RestController
@RequestMapping("charging/session")
public class ChargeSessionController extends AbstractController {

    @Autowired
    private ChargeSessionService chargeSessionService;

    /**
     * 会话分页查询
     */
    @Operation(summary = "会话分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('charge:session:list')")
    public Result<PageUtils> page(ChargeSessionForm form) {
        return Result.ok(chargeSessionService.queryPage(form));
    }
}
