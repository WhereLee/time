package com.reason.modules.sys.vo;

import com.reason.common.utils.StringUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.*;

@Schema(description = "菜单VO")
@Data
public class SysMenuVO {
    @Schema(description = "菜单ID")
    @NotNull(message = "菜单主键不能为空",groups = {UpdateGroup.class})
    private Long menuId;

    @Schema(description = "菜单类型 0-目录 1-菜单 2-按钮")
    @Min(value = 0,message = "菜单类型必须0-2之间",groups = {AddGroup.class,UpdateGroup.class})
    @Max(value = 2,message = "菜单类型必须0-2之间",groups = {AddGroup.class,UpdateGroup.class})
    @NotNull(message="菜单类型不能为空",groups = {AddGroup.class})
    private Integer menuType;

    @Schema(description = "菜单名称")
    @Size(max = 64,message = "菜单名称过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotBlank(message="菜单名称不能为空",groups = {AddGroup.class})
    private String menuName;

    public void setMenuName(String menuName) {
        this.menuName = StringUtils.replaceBlank(menuName);
    }

    @Schema(description = "菜单授权 英文逗号隔开", example = "user:list,user:create")
    @Size(max = 256,message = "菜单授权过长",groups = {AddGroup.class,UpdateGroup.class})
    private String menuPerms;   //授权(多个用逗号分隔，如：user:list,user:create)

    public void setMenuPerms(String menuPerms) {
        this.menuPerms = StringUtils.replaceBlank(menuPerms);
    }

    @Schema(description = "菜单/按钮图标")
    @Size(max = 512,message = "菜单/按钮图标过长",groups = {AddGroup.class,UpdateGroup.class})
    private String menuIcon;    //菜单/按钮的图标

    public void setMenuIcon(String menuIcon) {
        this.menuIcon = StringUtils.replaceBlank(menuIcon);
    }

    @Schema(description = "菜单图片地址")
    @Size(max = 512,message = "菜单图片地址过长",groups = {AddGroup.class,UpdateGroup.class})
    private String menuPic;

    public void setMenuPic(String menuPic) {
        this.menuPic = StringUtils.replaceBlank(menuPic);
    }

    @Schema(description = "菜单默认图片地址")
    @Size(max = 512,message = "菜单默认图片地址标过长",groups = {AddGroup.class,UpdateGroup.class})
    private String menuDefPic;

    public void setMenuDefPic(String menuDefPic) {
        this.menuDefPic = StringUtils.replaceBlank(menuDefPic);
    }

    @Schema(description = "菜单URL-显示到地址栏")
    @Size(max = 512,message = "菜单URL过长",groups = {AddGroup.class,UpdateGroup.class})
    private String menuUrl;     //菜单URL

    public void setMenuUrl(String menuUrl) {
        this.menuUrl = StringUtils.replaceBlank(menuUrl);
    }

    @Schema(description = "页面路径-实际使用的访问地址")
    @Size(max = 512,message = "页面路径过长",groups = {AddGroup.class,UpdateGroup.class})
    private String menuPage;

    public void setMenuPage(String menuPage) {
        this.menuPage = StringUtils.replaceBlank(menuPage);
    }

    @Schema(description = "父级ID，没有，则0")
    //@NotNull(message = "父级ID不能为空",groups = {AddGroup.class})
    private Long menuFid;

    @Schema(description = "菜单排序")
    private Integer menuOrdernum;   //菜单的排序

}
