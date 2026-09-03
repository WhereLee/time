package com.reason.modules.sys.controller;

import com.alibaba.fastjson2.JSONObject;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Constant;
import com.reason.common.utils.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 其他接口
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
@Tag(name = "公共接口")
@RestController
public class SysCommController extends AbstractController {
    @Value("${realmName}")
    private String realmName;

    @PostMapping("/test1")
    public Result test1(@RequestBody JSONObject object) {
        log.info("object:{}", object);

        JSONObject data = new JSONObject();
        data.put("policeSubstation", "古塘街道");
        data.put("villageName", "界牌社区");
        data.put("provider", "33028206001190000469");
        data.put("name", "0469结构枪_青少年宫路-科技路");
        data.put("longitude", "");
        data.put("latitude", "");
        data.put("deviceType", "人脸");

        return Result.ok(data);
    }

    @Operation(summary = "图片上传-菜单", description = "图片上传-菜单，返回相对路径；不需要权限")
    @ApiOperationSupport(order = 10)
    @SysLog(module = "其他模块",func = "上传",value = "菜单图片上传")
    @PostMapping("/pic/upload/menu")
    public Result<String> menuPicUpload(@RequestParam(value="file",required=false) MultipartFile file){
        //1.校验
        if (file == null || file.isEmpty()) {
            throw new RRException("请选择图片");
        }

        //2.上传路径
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        String today = simpleDateFormat.format(new Date());

        String path = Constant.MENU_PIC_UPLOAD_PATH+"/"+today;
        File dir = new File(path);
        if (!dir.exists()){
            dir.mkdirs();
        }

        //3.图片重命名
        String picture = System.currentTimeMillis()+"_"+UUID.randomUUID().toString().replace("-","") +".png";
        String picUrl = realmName+path.substring(1)+"/"+picture;

        //4.保存图片
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(new File(path, picture)));
            out.write(file.getBytes());
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("图片保存失败:"+e.getMessage());
        } finally {
            try {
                if(null != out)
                    out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return Result.ok(picUrl);
    }

    @Operation(summary = "图片上传", description = "图片上传，返回相对路径；不需要权限")
    @ApiOperationSupport(order = 30)
    @SysLog(module = "其他模块",func = "上传",value = "图片上传")
    @PostMapping("/pic/upload")
    public Result<String> picUpload(@RequestParam(value="file",required=false) MultipartFile file){
        //1.校验
        if (file == null || file.isEmpty()) {
            throw new RRException("请选择图片");
        }

        //2.上传路径
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        String today = simpleDateFormat.format(new Date());

        String path = Constant.WORK_PIC_UPLOAD_PATH+"/"+today;
        File dir = new File(path);
        if (!dir.exists()){
            dir.mkdirs();
        }

        //3.图片重命名
        String picture = System.currentTimeMillis()+"_"+UUID.randomUUID().toString().replace("-","") +".png";
        String picUrl = realmName+path.substring(1)+"/"+picture;

        //4.保存图片
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(new File(path, picture)));
            out.write(file.getBytes());
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("图片保存失败:"+e.getMessage());
        } finally {
            try {
                if(null != out)
                    out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return Result.ok(picUrl);
    }

}
