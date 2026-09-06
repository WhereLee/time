package com.reason.modules.sys.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.Result;
import com.reason.modules.sys.entity.SysDicIplistEntity;
import com.reason.modules.sys.service.SysDictionaryService;
import com.reason.modules.sys.vo.SysDicIplistVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "IP黑白名单管理")
@RestController
@RequestMapping("sys/iplist")
public class SysIplistController {
    @Autowired
    private SysDictionaryService sysDictionaryService;

    /**
     * 信息
     */
    @Operation(summary = "详细查询", description = "查询IP黑白名单配置；权限说明：sys:iplist:info 查询")
    @ApiOperationSupport(order = 20)
    @SysLog(module = "IP黑白名单模块",func = "查询",value = "查询IP黑白名单配置")
    @GetMapping("/info")
    @PreAuthorize("hasAuthority('sys:iplist:info')")
    public Result<SysDicIplistEntity> info(){
        SysDicIplistEntity iplist = sysDictionaryService.getIpList();

        return Result.ok(iplist);
    }

    /**
     * 修改
     */
    @Operation(summary = "配置接口", description = "配置IP黑白名单，权限说明：sys:iplist:update 修改")
    @ApiOperationSupport(order = 50)
    @SysLog(module = "IP黑白名单模块",func = "配置",value = "配置IP黑白名单")
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('sys:iplist:update')")
    public Result update(@RequestBody SysDicIplistVO iplistVO){
        sysDictionaryService.setIpList(iplistVO);

        return Result.ok();
    }
}
