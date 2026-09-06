package com.reason.modules.sys.controller;


import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.sys.entity.SysParamEntity;
import com.reason.modules.sys.form.SysParamForm;
import com.reason.modules.sys.service.SysParamService;
import com.reason.modules.sys.vo.SysParamVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 
 *
 * @date 2020-04-29 10:10:57
 */
@Tag(name = "参数管理")
@RestController
@RequestMapping("sys/param")
public class SysParamController {
    @Autowired
    private SysParamService sysParamService;

    /**
     * 列表-分页
     */
    @Operation(summary = "列表查询", description = "查询所有参数信息-分页；权限说明：sys:param:list 查询")
    @ApiOperationSupport(order = 1,ignoreParameters = {"areaId","sqlFilter"})
    @SysLog(module = "参数模块",func = "查询",value = "分页查询参数")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('sys:param:list')")
    public Result<PageUtils> list(SysParamForm form){
        PageUtils page = sysParamService.queryPage(form);

        return Result.ok(page);
    }


    /**
     * 信息
     */
    @Operation(summary = "详细查询", description = "查询所选参数详细信息；权限说明：sys:param:info 查询")
    @ApiOperationSupport(order = 20)
    @SysLog(module = "参数模块",func = "查询",value = "查询参数详细")
    @GetMapping("/info/{paramId}")
    @PreAuthorize("hasAuthority('sys:param:info')")
    public Result<SysParamEntity> info(@PathVariable("paramId") Long paramId){
		SysParamEntity param = sysParamService.getById(paramId);

        return Result.ok(param);
    }

    /**
     * 新增
     */
    @Operation(summary = "新增接口", description = "新增参数，权限说明：sys:param:save 新增")
    @ApiOperationSupport(order = 40,ignoreParameters = {"paramId"})
    @SysLog(module = "参数模块",func = "新增",value = "新增参数")
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('sys:param:save')")
    public Result save(@RequestBody SysParamVO paramVO){
        sysParamService.saveParam(paramVO);

        return Result.ok();
    }

    /**
     * 修改
     */
    @Operation(summary = "修改接口", description = "修改参数，权限说明：sys:param:update 修改")
    @ApiOperationSupport(order = 50,ignoreParameters = {"paramName","paramKey","paramComment"})
    @SysLog(module = "参数模块",func = "修改",value = "修改参数")
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('sys:param:update')")
    public Result update(@RequestBody SysParamVO paramVO){
		sysParamService.updateParam(paramVO);

        return Result.ok();
    }

    /**
     * 关闭
     */
    @Operation(summary = "关闭接口", description = "关闭参数，权限说明：sys:param:close 关闭")
    @ApiOperationSupport(order = 70)
    @SysLog(module = "参数模块",func = "关闭",value = "关闭参数")
    @PostMapping("/close/{paramId}")
    @PreAuthorize("hasAuthority('sys:param:close')")
    public Result close(@PathVariable("paramId") Long paramId){
        sysParamService.openOrClose(new SysParamEntity(paramId,1));

        return Result.ok();
    }

    /**
     * 开放
     */
    @Operation(summary = "开放接口", description = "开放参数，权限说明：sys:param:open 开放")
    @ApiOperationSupport(order = 80)
    @SysLog(module = "参数模块",func = "开放",value = "开放参数")
    @PostMapping("/open/{paramId}")
    @PreAuthorize("hasAuthority('sys:param:open')")
    public Result open(@PathVariable("paramId") Long paramId){
        sysParamService.openOrClose(new SysParamEntity(paramId,0));

        return Result.ok();
    }

}
