package com.plot.plugin.road.graph;

import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

/**
 * 显式道路端点连接：唯一允许将两条道路端点合并为共享拓扑节点的入口。
 */
public final class RoadExplicitConnector {
    private static final double NODE_TOLERANCE = 0.5;

    private RoadExplicitConnector() {
    }

    /**
     * 将两条边的指定端点合并到同一节点（通常取 B 端节点）。
     *
     * @return 是否发生拓扑变更
     */
    public static boolean connectEndpoints(
            RoadNetwork network,
            String edgeAId,
            boolean useStartA,
            String edgeBId,
            boolean useStartB) {
        if (network == null || edgeAId == null || edgeBId == null) {
            return false;
        }
        RoadEdge edgeA = network.getEdge(edgeAId);
        RoadEdge edgeB = network.getEdge(edgeBId);
        if (edgeA == null || edgeB == null || edgeA.getId().equals(edgeB.getId())) {
            return false;
        }
        String nodeAId = useStartA ? edgeA.getStartNodeId() : edgeA.getEndNodeId();
        String nodeBId = useStartB ? edgeB.getStartNodeId() : edgeB.getEndNodeId();
        if (nodeAId == null || nodeBId == null || nodeAId.equals(nodeBId)) {
            return false;
        }
        RoadNode nodeA = network.getNode(nodeAId);
        RoadNode nodeB = network.getNode(nodeBId);
        if (nodeA == null || nodeB == null) {
            return false;
        }
        if (!RoadGeometryUtils.pointsNear(nodeA.getPosition(), nodeB.getPosition(), NODE_TOLERANCE)) {
            return false;
        }
        return mergeEndpoint(network, edgeA, nodeAId, nodeBId);
    }

    private static boolean mergeEndpoint(
            RoadNetwork network,
            RoadEdge edge,
            String oldNodeId,
            String targetNodeId) {
        if (oldNodeId.equals(targetNodeId)) {
            return false;
        }
        RoadNode oldNode = network.getNode(oldNodeId);
        RoadNode targetNode = network.getNode(targetNodeId);
        if (oldNode == null || targetNode == null) {
            return false;
        }
        boolean relinked = false;
        if (edge.getStartNodeId().equals(oldNodeId)) {
            oldNode.removeEdge(edge.getId());
            edge.setStartNodeId(targetNodeId);
            targetNode.addEdge(edge.getId());
            relinked = true;
        } else if (edge.getEndNodeId().equals(oldNodeId)) {
            oldNode.removeEdge(edge.getId());
            edge.setEndNodeId(targetNodeId);
            targetNode.addEdge(edge.getId());
            relinked = true;
        }
        if (relinked && oldNode.getDegree() == 0 && !oldNodeId.equals(targetNodeId)) {
            network.removeNode(oldNodeId);
        }
        return relinked;
    }
}
