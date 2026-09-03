package com.reason.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.List;

import com.reason.modules.sys.vo.SysMenuVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 
 * 
 * @date 2020-04-22 14:30:49
 */
@Schema(description = "菜单对象")
@Data
@TableName("sys_menu")
public class SysMenuEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主键ID
	 */
	@Schema(description = "菜单ID")
	@TableId
	private Long menuId;
	/**
	 * 1-WEB端使用的菜/按钮 2-APP端使用的菜/按钮
	 */
	@Schema(description = "菜单分类 1-WEB端使用的菜/按钮 2-APP端使用的菜/按钮")
	private Integer menuOrigin;
	/**
	 * 0-目录 1-菜单 2-按钮
	 */
	@Schema(description = "菜单类型 0-目录 1-菜单 2-按钮")
	private Integer menuType;
	/**
	 * 菜单/按钮名字，名字允许重复，可以是中文
	 */
	@Schema(description = "菜单名称")
	private String menuName;
	/**
	 * 授权(多个用逗号分隔，如：user:list,user:create)
	 */
	@Schema(description = "菜单授权 英文逗号隔开", example = "user:list,user:create")
	private String menuPerms;
	/**
	 * 菜单/按钮的图标
	 */
	@Schema(description = "菜单/按钮图标")
	private String menuIcon;
	/**
	 * 菜单图片地址
	 */
	@Schema(description = "菜单图片地址")
	private String menuPic;
	/**
	 * 菜单默认图片地址
	 */
	@Schema(description = "菜单默认图片地址")
	private String menuDefPic;
	/**
	 * 菜单URL
	 */
	@Schema(description = "菜单URL-显示到地址栏")
	private String menuUrl;
	/**
	 * 页面路径
	 */
	@Schema(description = "页面路径-实际使用的访问地址")
	private String menuPage;
	/**
	 * 父级id，没有则是0
	 */
	@Schema(description = "父级ID 0-没有父级")
	private Long menuFid;
	/**
	 * 父级ids 比如：0,1,11
	 */
	@Schema(description = "父级IDs-所有父级-0,1,11格式")
	private String menuFids;
	/**
	 * 菜单的排序
	 */
	@Schema(description = "菜单排序")
	private Integer menuOrdernum;
	/**
	 * 创建时间戳，单位秒
	 */
	@Schema(description = "创建时间戳（秒）")
	private Long menuCreatetime;
	/**
	 * 更新时间戳，单位秒
	 */
	@Schema(description = "更新时间戳（秒）")
	private Long menuUpdatetime;
	/**
	 * 0-正常，>0-删除
	 */
	@Schema(description = "状态标志 0-正常 >0-删除")
	//@TableLogic
	private Long menuStatus;
	/**
	 * 子节点
	 */
	@Schema(description = "子节点")
	@TableField(exist=false)
	private List<SysMenuEntity> children;
	/**
	 * 父级菜单名称
	 */
	@Schema(description = "父级菜单名称")
	@TableField(exist=false)
	private String menuFidName;

	public SysMenuEntity() {}

	/**
	 * 新增或修改
	 * @param menuVO 前端传入参数对象
	 * @param type 1-新增 2-修改
	 */
	public SysMenuEntity(SysMenuVO menuVO,Integer type) {
		this.menuType = menuVO.getMenuType();
		this.menuName = menuVO.getMenuName();
		this.menuPerms = menuVO.getMenuPerms();
		this.menuIcon = menuVO.getMenuIcon();
		this.menuPic = menuVO.getMenuPic();
		this.menuDefPic = menuVO.getMenuDefPic();
		this.menuUrl = menuVO.getMenuUrl();
		this.menuPage = menuVO.getMenuPage();
		this.menuFid = menuVO.getMenuFid();
		this.menuOrdernum = menuVO.getMenuOrdernum();
		if (type == 1) {
			this.menuCreatetime = System.currentTimeMillis()/1000;
			this.menuUpdatetime = System.currentTimeMillis()/1000;
		} else if (type == 2) {
			this.menuId = menuVO.getMenuId();
			this.menuUpdatetime = System.currentTimeMillis()/1000;
		}
	}

	/**
	 * 删除-将status=id
	 * @param menuId
	 */
	public SysMenuEntity(Long menuId) {
		this.menuId = menuId;
		this.menuStatus = menuId;
		this.menuUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 判断数据是否有效，即未删除
	 * @return true：是
	 */
	public boolean isValid() {
		return (menuStatus != null && menuStatus == 0);
	}

}
