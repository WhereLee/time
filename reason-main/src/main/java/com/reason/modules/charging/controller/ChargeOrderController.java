package com.reason.modules.charging.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import com.reason.modules.charging.service.ChargeOrderService;
import com.reason.modules.charging.form.ChargeOrderForm;
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
 * 充电订单查询（管理端只读：订单由结算事务生成后不可变更）

<p>权限串与 sys_menu 307 注册的 charge:order:list 对应；金额单位为分（展示换算由前端负责）。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "充电订单")
@RestController
@RequestMapping("charging/order")
public class ChargeOrderController extends AbstractController {

    @Autowired
    private ChargeOrderService chargeOrderService;

    /**
     * 订单分页查询（两段费率金额快照齐全：电费/服务费/总额，单位分）
     */
    @Operation(summary = "订单分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('charge:order:list')")
    public Result<PageUtils> page(ChargeOrderForm form) {
        return Result.ok(chargeOrderService.queryPage(form));
    }
}
