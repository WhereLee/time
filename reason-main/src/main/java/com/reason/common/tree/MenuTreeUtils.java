package com.reason.common.tree;

import com.reason.modules.sys.entity.SysMenuEntity;

import java.util.*;

/**
 * 菜单 list 转成树形结构 此方法不通用
 */
public class MenuTreeUtils {
    //所有节点
    private static HashMap<Long,SysMenuEntity> treeNodesMap = null;
    //处理过的节点的ID
    private static HashSet<Long> treeNodeIdsSet = null;
    //所有带children的根节点
    private static HashMap<Long,SysMenuEntity> topNodesMap = null;

    /**
     * list 转成树形结构
     * @param list
     * @return
     */
    public static List<SysMenuEntity> listToTree(List<SysMenuEntity> list){
//        log.info(JSON.toJSONString(list));
        if (list == null || list.isEmpty()) {
            return null;
        }

        treeNodesMap = new LinkedHashMap<>();
        treeNodeIdsSet = new HashSet<>();
        topNodesMap = new LinkedHashMap<>();
        //遍历list,将所有节点存入 treeNodesMap
        for (SysMenuEntity treeNode : list) {
            treeNodesMap.put(treeNode.getMenuId(),treeNode);
        }

        //遍历 treeNodesMap
        Iterator<SysMenuEntity> iterator = treeNodesMap.values().iterator();
        SysMenuEntity treeNode = null;
        while (iterator.hasNext()){
            //递归查找topNodes
            treeNode = iterator.next();
            findParent(treeNode);
        }

        //所有根节点 topNodesMap 转成 list
        List<SysMenuEntity> result = new ArrayList<>();
        Iterator<SysMenuEntity> ite = topNodesMap.values().iterator();
        while (ite.hasNext()) {
            result.add(ite.next());
        }

        return result;
    }

    /**
     * 查找父节点
     * @param treeNode
     * @return
     */
    public static void findParent(SysMenuEntity treeNode){
//        log.info("treeNode.getNodeId():"+treeNode.getNodeId());

        //set里已经存在该节点ID，表示该节点已处理过
        if (treeNodeIdsSet.contains(treeNode.getMenuId())) {
            return;
        }
        //对于处理过的节点，把节点ID放入set
        treeNodeIdsSet.add(treeNode.getMenuId());

        //取父节点
        SysMenuEntity parentNode = treeNodesMap.get(treeNode.getMenuFid());
        if (parentNode == null) {
            //父节点不存在，则当前节点为topNode
            topNodesMap.put(treeNode.getMenuId(),treeNode);
            return;
        }

        //父节点存在，则把当前节点加入到父节点中
        if (topNodesMap.containsKey(parentNode.getMenuId())) {
            parentNode = topNodesMap.get(parentNode.getMenuId());
        }
        if (parentNode.getChildren() == null) {
            parentNode.setChildren(new ArrayList<>());
        }
        parentNode.getChildren().add(treeNode);

        //再查找更上一级
        findParent(parentNode);
    }
}
