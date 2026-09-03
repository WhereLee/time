package com.reason.modules.sys.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.utils.Result;
import com.reason.modules.sys.entity.SysLogEntity;
import com.reason.modules.sys.form.SysLogForm;
import com.reason.modules.sys.service.SysLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reason.common.utils.PageUtils;



/**
 * 失败日志 查询操作不记日志
 *
 * @date 2020-04-26 15:29:52
 */
@Tag(name = "失败日志")
@RestController
@RequestMapping("sys/errorlog")
public class SysErrorLogController extends AbstractController{
    @Autowired
    private SysLogService sysLogService;

    /**
     * 列表
     */
    @Operation(summary = "列表查询", description = "查询所有失败日志；权限说明：sys:errorlog:list")
    @ApiOperationSupport(order = 10,ignoreParameters = {"areaId","sqlFilter","logState"})
    @GetMapping("/list")
    @RequiresPermissions("sys:errorlog:list")
    public Result<PageUtils> list(SysLogForm form){
        form.setLogState(1);
        PageUtils page = sysLogService.queryPage(form);

        return Result.ok(page);
    }


    /**
     * 信息
     */
    /*@Operation(summary = "详细查询", description = "查询所选失败日志的详细信息，权限说明：sys:errorlog:info 查询")
    @ApiOperationSupport(order = 30)
    @GetMapping("/info/{errorId}")
    @RequiresPermissions("sys:errorlog:info")
    public Result<SysLogEntity> info(@PathVariable("errorId") Long errorId){
		SysLogEntity log = sysLogService.getInfo(errorId);

        return Result.ok(log);
    }*/

}
