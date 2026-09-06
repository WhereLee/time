package com.reason.common.aspect;

import com.reason.common.annotation.DataFilter;
import com.reason.modules.sys.security.LoginUserHolder;
import com.reason.common.utils.StringUtils;
import com.reason.modules.sys.dao.SysMenuDao;
import com.reason.modules.sys.dao.SysRoleDao;
import com.reason.modules.sys.dao.SysUserDao;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.form.CommonForm;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单、区域数据权限控制，切面处理类
 */
@Slf4j
@Aspect
@Component
public class DataFilterAspect {
    @Autowired
    private SysRoleDao sysRoleDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysMenuDao sysMenuDao;

    @Pointcut("@annotation(com.reason.common.annotation.DataFilter)")
    public void pointCut() {}

    /**
     * 菜单数据权限 前置处理
     * @param point
     * @throws Throwable
     */
    @Before("pointCut()")
    public void dataFilter(JoinPoint point) throws Throwable {
        CommonForm form = (CommonForm) point.getArgs()[0];

        SysUserEntity user = LoginUserHolder.getLoginUser();
        //进行数据过滤
        form.setSqlFilter(getSqlFilter(user,point));
        //log.info("sqlFilter:{}",form.getSqlFilter());
    }

    /**
     * 获取过滤的SQL
     * @param user
     * @param point
     * @return
     */
    public String getSqlFilter(SysUserEntity user,JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        DataFilter dataFilter = signature.getMethod().getAnnotation(DataFilter.class);
        //获取表的别名
        String tableAlias = dataFilter.tableAlias();
        if(StringUtils.isNotBlank(tableAlias)){
            tableAlias +=  ".";
        }

        //角色、菜单、小区 数据权限
        boolean roleFilter = dataFilter.roleFilter();
        boolean menuFilter = dataFilter.menuFilter();
        boolean userFilter = dataFilter.userFilter();

        StringBuilder sqlFilter = new StringBuilder();

        //拼接 数据权限限制
        if (roleFilter) {
            //不是开发员和系统管理员
            if (!user.devOrSysAdmin()) {
                //角色ID列表
                Set<Long> roleIdSet = new HashSet<>();

                //2021年11月17日 只能查询自己有的角色和自己建的角色
                //查询用户所拥有的角色权限
                List<Long> userRoleIdList = sysRoleDao.queryRoleIdByUserId(user.getUserId());
                if (userRoleIdList != null && userRoleIdList.size() > 0) {
                    roleIdSet.addAll(userRoleIdList);
                }

                //查询用户自己创建的角色
                List<Long> userRoleIdList2 = sysRoleDao.queryRoleIdByCreator(user.getUserId());
                if (userRoleIdList2 != null && userRoleIdList2.size() > 0) {
                    roleIdSet.addAll(userRoleIdList2);
                }

                //拼接
                if (roleIdSet.size() > 0) {
                    sqlFilter.append(" (");
                    sqlFilter.append(tableAlias).append(dataFilter.roleId()).append(" in(").append(StringUtils.join(roleIdSet, ",")).append(")");
                    sqlFilter.append(")");
                } else {//没有角色权限
                    sqlFilter.append(" (");
                    sqlFilter.append(tableAlias).append(dataFilter.roleId()).append(" = 0");
                    sqlFilter.append(")");
                }
            }

        } else if (menuFilter) {
            //不是开发员
            if (!user.developer()) {
                //菜单ID列表
                Set<Long> menuIdSet = new HashSet<>();

                //查询用户所拥有的菜单权限
                List<Long> userMenuIdList = sysMenuDao.queryMenuIdByUserId(user.getUserId());
                if (userMenuIdList != null && userMenuIdList.size() > 0) {
                    menuIdSet.addAll(userMenuIdList);
                }

                //拼接
                if (menuIdSet.size() > 0) {
                    sqlFilter.append(" (");
                    sqlFilter.append(tableAlias).append(dataFilter.menuId()).append(" in(").append(StringUtils.join(menuIdSet, ",")).append(")");
                    sqlFilter.append(")");
                } else {//没有菜单权限
                    sqlFilter.append(" (");
                    sqlFilter.append(tableAlias).append(dataFilter.menuId()).append(" = 0");
                    sqlFilter.append(")");
                }
            }

        }  else if (userFilter) {
            //不是开发员和系统管理员
            if (!user.devOrSysAdmin()) {
                //用户ID列表
                Set<Long> userIdSet = new HashSet<>();

                //2021年11月17日 只能查询自己的用户和自己建的用户
                //查询自己的用户信息
                userIdSet.add(user.getUserId());

                //查询用户自己创建的用户
                List<Long> userIdList = sysUserDao.queryUserIdByCreator(user.getUserId());
                if (userIdList != null && userIdList.size() > 0) {
                    userIdSet.addAll(userIdList);
                }

                sqlFilter.append(" (");
                sqlFilter.append(tableAlias).append(dataFilter.userId()).append(" in(").append(StringUtils.join(userIdSet, ",")).append(")");
                sqlFilter.append(")");
            }

        }

        if("".equals(sqlFilter.toString().trim())){
            return null;
        }

        return sqlFilter.toString();
    }

}
