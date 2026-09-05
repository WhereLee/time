package com.reason.modules.charging.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.service.BenefitRecordService;
import com.reason.modules.charging.form.BenefitForm;
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
 * 免停权益查询（管理端只读：跨方凭证签发/核销/过期全生命周期追踪）

<p>权限串与 sys_menu 309 注册的 charge:benefit:list 对应。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "权益记录")
@RestController
@RequestMapping("charging/benefit")
public class BenefitController extends AbstractController {

    @Autowired
    private BenefitRecordService benefitRecordService;

    /**
     * 权益分页查询（含核销回写：核销会话/订单/时间可追溯）
     */
    @Operation(summary = "权益分页查询")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('charge:benefit:list')")
    public Result<PageUtils> page(BenefitForm form) {
        return Result.ok(benefitRecordService.queryPage(form));
    }
}
