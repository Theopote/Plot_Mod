package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.JunctionBlocks;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 重叠路口世界列的设计标高权威规划：在合并/接缝之前一次算定。
 * <p>
 * 规则与接缝阶段历史行为一致：
 * <ul>
 *   <li>立交 / 大跨度 / 无拓扑连接 → 各用自身 Y，并互相保护</li>
 *   <li>同层且拓扑相连 → 相连子图内协商 max</li>
 * </ul>
 * {@link JunctionSeamReconciliationPass} 只消费本规划，不再自行裁决。
 */
public final class ColumnDesignElevationPlanner {
    private ColumnDesignElevationPlanner() {
    }

    public record ColumnClaim(String nodeId, int targetY, int junctionY, boolean gradeSeparated) {
    }

    public record ColumnElevationPlan(
            Map<String, Map<ColumnKey, Integer>> designYByJunction,
            Map<ColumnKey, Set<Integer>> preservedYsByColumn) {
        public static ColumnElevationPlan empty() {
            return new ColumnElevationPlan(Map.of(), Map.of());
        }

        public int designY(String nodeId, ColumnKey column, int fallback) {
            if (nodeId == null || column == null || designYByJunction == null) {
                return fallback;
            }
            Map<ColumnKey, Integer> byColumn = designYByJunction.get(nodeId);
            if (byColumn == null) {
                return fallback;
            }
            return byColumn.getOrDefault(column, fallback);
        }

        public Set<Integer> preservedYs(ColumnKey column) {
            if (column == null || preservedYsByColumn == null) {
                return Set.of();
            }
            return preservedYsByColumn.getOrDefault(column, Set.of());
        }
    }

    public static ColumnElevationPlan plan(
            Map<String, JunctionBlocks> junctionResults,
            RoadNetwork network) {
        Map<ColumnKey, List<ColumnClaim>> claimsByColumn = collectClaims(junctionResults, network);
        if (claimsByColumn.isEmpty()) {
            return ColumnElevationPlan.empty();
        }

        Map<String, Map<ColumnKey, Integer>> designYByJunction = new HashMap<>();
        Map<ColumnKey, Set<Integer>> preservedYsByColumn = new HashMap<>();

        for (Map.Entry<ColumnKey, List<ColumnClaim>> entry : claimsByColumn.entrySet()) {
            ColumnKey column = entry.getKey();
            List<ColumnClaim> claims = entry.getValue();
            if (claims.size() == 1) {
                ColumnClaim only = claims.getFirst();
                designYByJunction
                    .computeIfAbsent(only.nodeId(), ignored -> new HashMap<>())
                    .put(column, only.targetY());
                continue;
            }

            boolean keepBoth = shouldKeepBoth(claims);
            if (keepBoth || network == null || !anyConnectedPair(claims, network)) {
                Set<Integer> preserved = preservedYsByColumn.computeIfAbsent(column, ignored -> new HashSet<>());
                for (ColumnClaim claim : claims) {
                    designYByJunction
                        .computeIfAbsent(claim.nodeId(), ignored -> new HashMap<>())
                        .put(column, claim.targetY());
                    preserved.add(claim.targetY());
                }
                continue;
            }

            List<Set<String>> components = connectedComponents(claims, network);
            Map<String, Integer> claimY = new HashMap<>();
            for (ColumnClaim claim : claims) {
                claimY.put(claim.nodeId(), claim.targetY());
            }
            Set<Integer> preserved = preservedYsByColumn.computeIfAbsent(column, ignored -> new HashSet<>());
            Set<String> negotiated = new HashSet<>();
            for (Set<String> component : components) {
                if (component.size() < 2) {
                    continue;
                }
                int negotiatedY = Integer.MIN_VALUE;
                for (String id : component) {
                    negotiatedY = Math.max(negotiatedY, claimY.getOrDefault(id, Integer.MIN_VALUE));
                }
                for (String id : component) {
                    designYByJunction
                        .computeIfAbsent(id, ignored -> new HashMap<>())
                        .put(column, negotiatedY);
                    negotiated.add(id);
                }
                preserved.add(negotiatedY);
            }
            for (ColumnClaim claim : claims) {
                if (negotiated.contains(claim.nodeId())) {
                    continue;
                }
                designYByJunction
                    .computeIfAbsent(claim.nodeId(), ignored -> new HashMap<>())
                    .put(column, claim.targetY());
                preserved.add(claim.targetY());
            }
        }

        Map<ColumnKey, Set<Integer>> frozenPreserved = new HashMap<>();
        for (var entry : preservedYsByColumn.entrySet()) {
            frozenPreserved.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        Map<String, Map<ColumnKey, Integer>> frozenDesign = new HashMap<>();
        for (var entry : designYByJunction.entrySet()) {
            frozenDesign.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return new ColumnElevationPlan(
            Map.copyOf(frozenDesign),
            Map.copyOf(frozenPreserved));
    }

    private static Map<ColumnKey, List<ColumnClaim>> collectClaims(
            Map<String, JunctionBlocks> junctionResults,
            RoadNetwork network) {
        Map<ColumnKey, List<ColumnClaim>> claimsByColumn = new HashMap<>();
        if (junctionResults == null || junctionResults.isEmpty()) {
            return claimsByColumn;
        }
        for (Map.Entry<String, JunctionBlocks> entry : junctionResults.entrySet()) {
            String nodeId = entry.getKey();
            JunctionBlocks blocks = entry.getValue();
            if (nodeId == null || blocks == null || blocks.elevationField() == null) {
                continue;
            }
            JunctionValidationManifest manifest = blocks.validationManifest();
            int junctionY = manifest != null ? manifest.junctionY() : blocks.elevationField().junctionY();
            boolean gradeSeparated = isGradeSeparatedClaim(network, nodeId, manifest);
            for (Map.Entry<ColumnKey, Integer> target : blocks.elevationField().targetYByColumn().entrySet()) {
                if (target.getKey() == null || target.getValue() == null) {
                    continue;
                }
                claimsByColumn
                    .computeIfAbsent(target.getKey(), ignored -> new ArrayList<>())
                    .add(new ColumnClaim(nodeId, target.getValue(), junctionY, gradeSeparated));
            }
        }
        return claimsByColumn;
    }

    private static boolean shouldKeepBoth(List<ColumnClaim> claims) {
        for (ColumnClaim claim : claims) {
            if (claim.gradeSeparated()) {
                return true;
            }
        }
        int minJy = Integer.MAX_VALUE;
        int maxJy = Integer.MIN_VALUE;
        for (ColumnClaim claim : claims) {
            minJy = Math.min(minJy, claim.junctionY());
            maxJy = Math.max(maxJy, claim.junctionY());
        }
        return maxJy - minJy > JunctionFilletElevationLoft.SAME_LEVEL_Y_SPAN;
    }

    static boolean isGradeSeparatedClaim(
            RoadNetwork network,
            String nodeId,
            JunctionValidationManifest manifest) {
        if (manifest != null && manifest.hasGradeSeparation()) {
            return true;
        }
        if (network == null || nodeId == null) {
            return false;
        }
        RoadNode node = network.getNode(nodeId);
        return node != null && (node.isGradeSeparated() || node.isStackGradeSeparated());
    }

    private static boolean anyConnectedPair(List<ColumnClaim> claims, RoadNetwork network) {
        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                if (nodesShareEdge(network, claims.get(i).nodeId(), claims.get(j).nodeId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Set<String>> connectedComponents(List<ColumnClaim> claims, RoadNetwork network) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (ColumnClaim claim : claims) {
            adjacency.put(claim.nodeId(), new HashSet<>());
        }
        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                String a = claims.get(i).nodeId();
                String b = claims.get(j).nodeId();
                if (nodesShareEdge(network, a, b)) {
                    adjacency.get(a).add(b);
                    adjacency.get(b).add(a);
                }
            }
        }
        List<Set<String>> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ColumnClaim claim : claims) {
            if (visited.contains(claim.nodeId())) {
                continue;
            }
            Set<String> component = floodComponent(claim.nodeId(), adjacency);
            visited.addAll(component);
            components.add(component);
        }
        return components;
    }

    private static Set<String> floodComponent(String start, Map<String, Set<String>> adjacency) {
        Set<String> component = new HashSet<>();
        List<String> stack = new ArrayList<>();
        stack.add(start);
        while (!stack.isEmpty()) {
            String id = stack.removeLast();
            if (!component.add(id)) {
                continue;
            }
            stack.addAll(adjacency.getOrDefault(id, Set.of()));
        }
        return component;
    }

    private static boolean nodesShareEdge(RoadNetwork network, String a, String b) {
        if (network == null || a == null || b == null || a.equals(b)) {
            return false;
        }
        for (RoadEdge edge : network.getEdgesAtNode(a)) {
            if (edge == null) {
                continue;
            }
            if (a.equals(edge.getStartNodeId()) && b.equals(edge.getEndNodeId())) {
                return true;
            }
            if (b.equals(edge.getStartNodeId()) && a.equals(edge.getEndNodeId())) {
                return true;
            }
        }
        return false;
    }
}
