package com.reason.common.tree.node;

import java.io.Serializable;
import java.util.List;

/**
 * 树 节点
 */
public class TreeNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long nodeId;    //节点ID
    private Integer nodeOrigin; //节点来源 0：web端；1：app端
    private String nodeName;    //节点名称
    private Integer nodeType;   //节点类型 0：菜单；1：按钮
    private String nodeIcon;    //节点图标
    private String nodeUrl;     //节点链接 1.菜单链接：前端网页链接2.按钮链接：即访问接口
    private Long nodeFid;   //父节点ID
    private String nodePermission;  //节点说明（按钮 比如：user:create），事先定义
    private Integer nodeChecked;    //节点权限 0-没有，1-有

    private List<TreeNode> children;    //子节点

    /**
     * 构造节点
     * @param obj
     */
    public TreeNode(ITreeNode obj){
        this.nodeId = obj.getNodeId();
        this.nodeOrigin = obj.getNodeOrigin();
        this.nodeName = obj.getNodeName();
        this.nodeType = obj.getNodeType();
        this.nodeIcon = obj.getNodeIcon();
        this.nodeUrl = obj.getNodeUrl();
        this.nodeFid = obj.getNodeFid();
        this.nodePermission = obj.getNodePermission();
        this.nodeChecked = obj.getNodeChecked();
    }

    /**
     * 无参构造方法
     */
    public TreeNode(){}

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    public Integer getNodeOrigin() {
        return nodeOrigin;
    }

    public void setNodeOrigin(Integer nodeOrigin) {
        this.nodeOrigin = nodeOrigin;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Integer getNodeType() {
        return nodeType;
    }

    public void setNodeType(Integer nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeIcon() {
        return nodeIcon;
    }

    public void setNodeIcon(String nodeIcon) {
        this.nodeIcon = nodeIcon;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }

    public Long getNodeFid() {
        return nodeFid;
    }

    public void setNodeFid(Long nodeFid) {
        this.nodeFid = nodeFid;
    }

    public String getNodePermission() {
        return nodePermission;
    }

    public void setNodePermission(String nodePermission) {
        this.nodePermission = nodePermission;
    }

    public Integer getNodeChecked() {
        return nodeChecked;
    }

    public void setNodeChecked(Integer nodeChecked) {
        this.nodeChecked = nodeChecked;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<TreeNode> children) {
        this.children = children;
    }
}
