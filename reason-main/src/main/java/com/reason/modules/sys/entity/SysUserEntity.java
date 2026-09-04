package com.reason.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.List;

import com.reason.common.utils.AESUtil;
import com.reason.common.utils.Constant;
import com.reason.common.utils.PasswordCodec;
import com.reason.common.utils.StringUtils;
import com.reason.modules.sys.vo.SysPasswordVO;
import com.reason.modules.sys.vo.SysUserVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * 
 * 
 * @date 2020-04-22 14:30:49
 */
@Schema(description = "用户对象")
@Data
@TableName("sys_user")
public class SysUserEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主键ID
	 */
	@Schema(description = "用户ID")
	@TableId
	private Long userId;
	/**
	 * 用户名
	 */
	@Schema(description = "用户名称")
	private String userName;
	/**
	 * 用户密码（BCrypt 存储；历史数据为 SHA-256+盐，登录成功后渐进升级）
	 */
	@Schema(description = "用户密码")
	private String userPassword;
	/**
	 * 盐值，系统自动产生
	 */
	@Schema(description = "盐值")
	private String userSalt;
	/**
	 * 用户的真实名字
	 */
	@Schema(description = "用户真实姓名")
	private String userRealname;
	/**
	 * 邮箱
	 */
	@Schema(description = "邮箱")
	private String userEmail;
	/**
	 * 电话号码
	 */
	@Schema(description = "电话号码")
	private String userPhone;
	/**
	 * 企业微信用户ID
	 */
	@Schema(description = "企业微信用户ID")
	private String userQyweixinId;
	/**
	 * 最后一次登录时间戳
	 */
	@Schema(description = "最后一次登录时间戳（秒）")
	private Long userLogintime;
	/**
	 * 最后一次登录IP
	 */
	@Schema(description = "最后一次登录IP")
	private String userLoginip;
	/**
	 * 密码变更时间戳 秒  初始为第一次登录时间
	 */
	@Schema(description = "密码变更时间戳 秒  初始为第一次登录时间")
	private Long userPwdChangetime;
	/**
	 * 创建人 对应user表id 
	 */
	@Schema(description = "创建人")
	private Long userCreator;
	/**
	 * 创建时的时间戳，单位为秒
	 */
	@Schema(description = "创建时间戳（秒）")
	private Long userCreatetime;
	/**
	 * 更新时的时间戳，单位为秒
	 */
	@Schema(description = "更新时间戳（秒）")
	private Long userUpdatetime;
	/**
	 * 0-正常，1-关闭，关闭后，将不能登录系统
	 */
	@Schema(description = "回收标志 0-正常 1-回收 回收后不能登录系统")
	private Integer userRecycle;
	/**
	 * 0-正常，>0-删除
	 */
	@Schema(description = "状态标志 0-正常 >0-删除")
	//@TableLogic
	private Long userStatus;

	@Schema(description = "角色ID列表")
	@TableField(exist=false)
	private List<Long> roleIdList;

	@Schema(description = "角色列表")
	@TableField(exist=false)
	private List<SysRoleEntity> roleList;

	@Schema(description = "创建人名称")
	@TableField(exist=false)
	private String creatorName;

	@Schema(description = "用户最大角色id 1-开发员角色 2-系统管理员角色 其他-其他管理员角色 null-没有角色")
	@TableField(exist=false)
	private Long roleType;

	@Schema(description = "用户角色名称 多个用逗号分隔")
	@TableField(exist=false)
	private String roleNames;


	public SysUserEntity() {}

	/**
	 * 开放/关闭用户
	 * @param userId
	 * @param userRecycle
	 */
	public SysUserEntity(Long userId, Integer userRecycle) {
		this.userId = userId;
		this.userRecycle = userRecycle;
		this.userUpdatetime = System.currentTimeMillis()/1000;
	}

	//更新登录时间
	public SysUserEntity(Long userId,Long userLogintime, String userLoginip, Long userPwdChangetime){
		this.userId = userId;
		this.userLogintime = userLogintime;
		this.userLoginip = userLoginip;
		this.userPwdChangetime = userPwdChangetime;
	}

	/**
	 * 新增或修改
	 * 修改时不能修改用户类型，不能修改密码
	 * @param userVO 前端传入参数对象
	 * @param type 1-新增 2-修改
	 * @param userCreator 创建人
	 */
	public SysUserEntity(SysUserVO userVO,Integer type,Long userCreator) {
		this.userName = userVO.getUserName();
		String userRealname = userVO.getUserRealname();
		if (StringUtils.isBlank(userRealname))
			this.userRealname = "";
		if (StringUtils.isNotBlank(userRealname) && userRealname.indexOf("*") == -1)
			this.userRealname = userRealname;
		String userPhone = userVO.getUserPhone();
		if (StringUtils.isBlank(userPhone))
			this.userPhone = "";
		if (StringUtils.isNotBlank(userPhone) && userPhone.indexOf("*") == -1)
			this.userPhone = AESUtil.encrypt(userPhone, Constant.KEY);
		this.userQyweixinId = userVO.getUserQyweixinId();
		this.userEmail = userVO.getUserEmail();
		this.roleIdList = userVO.getRoleIdList();
		if (type == 1) {
			this.userSalt = RandomStringUtils.randomAlphanumeric(20);
			this.userPassword = PasswordCodec.encode(userVO.getUserPassword());
			this.userCreator = userCreator;
			this.userCreatetime = System.currentTimeMillis()/1000;
			this.userUpdatetime = System.currentTimeMillis()/1000;
		} else if (type == 2) {
			this.userId = userVO.getUserId();
			this.userUpdatetime = System.currentTimeMillis()/1000;
		}
	}

	/**
	 * 重置密码
	 * @param passwordVO
	 */
	public SysUserEntity(SysPasswordVO passwordVO) {
		this.userId = passwordVO.getUserId();
		this.userSalt = RandomStringUtils.randomAlphanumeric(20);
		this.userPassword = PasswordCodec.encode(passwordVO.getPassword());
		this.userUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 删除-将status=id
	 * @param userId
	 */
	public SysUserEntity(Long userId) {
		this.userId = userId;
		this.userStatus = userId;
		this.userUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 判断是否开放
	 * @return true：是
	 */
	public boolean open() {
		return (userRecycle != null && userRecycle == 0);
	}

	/**
	 * 判断数据是否有效，即未删除
	 * @return true：是
	 */
	public boolean valid() {
		return (userStatus != null && userStatus == 0);
	}

	/**
	 * 判断是否是开发员
	 * @return true：是
	 */
	public boolean developer() {
		//return (userType != null && userType == 0);
		return (roleType != null && roleType == 1L);
	}

	/**
	 * 判断是否是系统管理员
	 * @return true：是
	 */
	public boolean sysAdmin() {
		//return (userType != null && userType == 1);
		return (roleType != null && roleType == 2L);
	}

	/**
	 * 判断是否是其他管理员
	 * @return true：是
	 */
	public boolean otherAdmin() {
		//return (userType != null && userType == 2);
		return (roleType != null && roleType > 2L);
	}

	/**
	 * 判断是否是开发员或系统管理员
	 * @return true：是
	 */
	public boolean devOrSysAdmin() {
		//return (userType != null && (userType == 0 || userType == 1));
		return (roleType != null && (roleType == 1L || roleType == 2L));
	}
}
