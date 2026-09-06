package com.reason.common.tree.node;

/**
 * 要转化成树形结构的实体类 需要实现此接口
 */
public interface ITreeNode {
    //节点ID
    public Long getNodeId();
    //节点来源 0：web端；1：app端
    public Integer getNodeOrigin();
    //节点名称
    public String getNodeName();
    //节点类型 0：菜单；1：按钮
    public Integer getNodeType();
    //节点图标
    public String getNodeIcon();
    //节点链接 1.菜单链接：前端网页链接2.按钮链接：即访问接口
    public String getNodeUrl();
    //父节点ID
    public Long getNodeFid();
    //节点说明（按钮 比如：user:create），事先定义
    public String getNodePermission();
    //节点权限 0-没有，1-有
    public Integer getNodeChecked();
}
