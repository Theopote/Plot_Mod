package com.plot.plugin.road.graph;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.*;

/**
 * 检测平面距离过近的多个交叉点（可能是应合并的重复路口）。
 */
public final class NearbyJunctionClusterAnalyzer {
    /** 建议合并审查的默认距离阈值（方块 / 画布单位）。 */
    public static final double DEFAULT_CLUSTER_DISTANCE_BLOCKS = 3.0;

    private NearbyJunctionClusterAnalyzer() {
    }

    public record NearbyJunctionCluster(
            List<String> nodeIds,
            double maxSpanBlocks) {
        public NearbyJunctionCluster {
            nodeIds = List.copyOf(nodeIds);
        }

        public boolean contains(String nodeId) {
            return nodeIds.contains(nodeId);
        }
    }

    public static List<NearbyJunctionCluster> findClusters(RoadNetwork network) {
        return findClusters(network, DEFAULT_CLUSTER_DISTANCE_BLOCKS);
    }

    public static List<NearbyJunctionCluster> findClusters(RoadNetwork network, double maxDistanceBlocks) {
        if (network == null || maxDistanceBlocks <= 0.0) {
            return List.of();
        }
        List<RoadNode> junctions = listJunctionNodes(network);
        if (junctions.size() < 2) {
            return List.of();
        }

        Set<String> unvisited = new LinkedHashSet<>();
        for (RoadNode junction : junctions) {
            unvisited.add(junction.getId());
        }

        List<NearbyJunctionCluster> clusters = new ArrayList<>();
        while (!unvisited.isEmpty()) {
            String seedId = unvisited.iterator().next();
            RoadNode seed = network.getNode(seedId);
            if (seed == null) {
                unvisited.remove(seedId);
                continue;
            }

            Set<String> component = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(seedId);
            component.add(seedId);

            while (!queue.isEmpty()) {
                String currentId = queue.removeFirst();
                unvisited.remove(currentId);
                RoadNode current = network.getNode(currentId);
                if (current == null) {
                    continue;
                }
                for (RoadNode other : junctions) {
                    if (component.contains(other.getId())) {
                        continue;
                    }
                    if (current.getPosition().distance(other.getPosition()) <= maxDistanceBlocks) {
                        component.add(other.getId());
                        queue.add(other.getId());
                    }
                }
            }

            if (component.size() >= 2) {
                List<RoadNode> members = component.stream()
                    .map(network::getNode)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(RoadNode::getId))
                    .toList();
                clusters.add(new NearbyJunctionCluster(
                    members.stream().map(RoadNode::getId).toList(),
                    spanBlocks(members)));
            }
        }
        clusters.sort(Comparator.comparingDouble(NearbyJunctionCluster::maxSpanBlocks));
        return List.copyOf(clusters);
    }

    public static NearbyJunctionCluster clusterContaining(
            RoadNetwork network,
            String nodeId,
            double maxDistanceBlocks) {
        if (nodeId == null) {
            return null;
        }
        for (NearbyJunctionCluster cluster : findClusters(network, maxDistanceBlocks)) {
            if (cluster.contains(nodeId)) {
                return cluster;
            }
        }
        return null;
    }

    public static double distanceBlocks(RoadNode a, RoadNode b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        return a.getPosition().distance(b.getPosition());
    }

    private static List<RoadNode> listJunctionNodes(RoadNetwork network) {
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 2) {
                continue;
            }
            if (node.isJunction() || node.getDegree() >= 3 || RoadGraphQueries.isSimpleCrossing(node, network)) {
                junctions.add(node);
            }
        }
        return junctions;
    }

    private static double spanBlocks(List<RoadNode> members) {
        double max = 0.0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                max = Math.max(max, distanceBlocks(members.get(i), members.get(j)));
            }
        }
        return max;
    }
}
