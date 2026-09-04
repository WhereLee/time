package com.reason.modules.parking.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.parking.form.ParkOrderForm;
import com.reason.modules.parking.service.ParkOrderService;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 停车订单查询（管理端只读：订单由出场事务生成后不可变更）
 *
 * <p>权限串与 sys_menu 207 注册的 park:order:list 对应。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "停车订单查询")
@RestController
@RequestMapping("parking/order")
public class ParkOrderController extends AbstractController {

    @Autowired
    private ParkOrderService parkOrderService;

    /**
     * 订单分页查询（金额单位为分：unitPriceFen/amountFen，展示换算由前端负责）
     */
    @Operation(summary = "订单分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('park:order:list')")
    public Result<PageUtils> page(ParkOrderForm form) {
        return Result.ok(parkOrderService.queryPage(form));
    }
}
