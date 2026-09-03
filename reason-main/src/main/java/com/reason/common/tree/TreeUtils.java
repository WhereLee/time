package com.reason.common.tree;

import com.reason.common.tree.node.ITreeNode;
import com.reason.common.tree.node.TreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * list 转成树形结构（实体类需要实现 ITreeNode 接口）
 */
@Slf4j
public class TreeUtils {
    //所有节点
    private static HashMap<Long,TreeNode> treeNodesMap = null;
    //处理过的节点的ID
    private static HashSet<Long> treeNodeIdsSet = null;
    //所有带children的根节点
    private static HashMap<Long,TreeNode> topNodesMap = null;

    /**
     * list 转成树形结构
     * @param list
     * @return
     */
    public static List<TreeNode> listToTree(List<? extends ITreeNode> list){
//        log.info(JSON.toJSONString(list));
        if (list == null || list.isEmpty()) {
            return null;
        }

        treeNodesMap = new LinkedHashMap<>();
        treeNodeIdsSet = new HashSet<>();
        topNodesMap = new LinkedHashMap<>();
        TreeNode treeNode = null;
        //遍历list,将所有节点存入 treeNodesMap
        for (ITreeNode item : list) {
            treeNode = new TreeNode(item);
            treeNodesMap.put(treeNode.getNodeId(),treeNode);
        }

        //遍历 treeNodesMap
        Iterator<TreeNode> iterator = treeNodesMap.values().iterator();
        while (iterator.hasNext()){
            //递归查找topNodes
            treeNode = iterator.next();
            findParent(treeNode);
        }

        //所有根节点 topNodesMap 转成 list
        List<TreeNode> result = new ArrayList<>();
        Iterator<TreeNode> ite = topNodesMap.values().iterator();
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
    public static void findParent(TreeNode treeNode){
//        log.info("treeNode.getNodeId():"+treeNode.getNodeId());

        //set里已经存在该节点ID，表示该节点已处理过
        if (treeNodeIdsSet.contains(treeNode.getNodeId())) {
            return;
        }
        //对于处理过的节点，把节点ID放入set
        treeNodeIdsSet.add(treeNode.getNodeId());

        //取父节点
        TreeNode parentNode = treeNodesMap.get(treeNode.getNodeFid());
        if (parentNode == null) {
            //父节点不存在，则当前节点为topNode
            topNodesMap.put(treeNode.getNodeId(),treeNode);
            return;
        }

        //父节点存在，则把当前节点加入到父节点中
        if (topNodesMap.containsKey(parentNode.getNodeId())) {
            parentNode = topNodesMap.get(parentNode.getNodeId());
        }
        if (parentNode.getChildren() == null) {
            parentNode.setChildren(new ArrayList<>());
        }
        parentNode.getChildren().add(treeNode);

        //再查找更上一级
        findParent(parentNode);
    }
}
