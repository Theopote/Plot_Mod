package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkInvariantValidator;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 道路节点列表的搜索、分类过滤与稳定排序（Edit Tab 全节点面板）。
 */
public final class RoadNodeListHelper {

    public record NodeFilter(
            boolean junction,
            boolean endpoint,
            boolean manualElevation,
            boolean gradeSeparated,
            boolean invalid) {

        public boolean isActive() {
            return junction || endpoint || manualElevation || gradeSeparated || invalid;
        }
    }

    private RoadNodeListHelper() {
    }

    public static List<RoadNode> filterAndSort(
            RoadNetwork network,
            Iterable<RoadNode> nodes,
            String searchText,
            NodeFilter filter) {
        if (network == null || nodes == null) {
            return List.of();
        }
        String query = searchText != null ? searchText.trim().toLowerCase(Locale.ROOT) : "";
        Set<String> invalidNodeIds = filter != null && filter.invalid()
            ? RoadNetworkInvariantValidator.collectInvalidNodeIds(network)
            : Set.of();
        List<RoadNode> result = new ArrayList<>();
        for (RoadNode node : nodes) {
            if (!matchesSearch(network, node, query)) {
                continue;
            }
            if (filter != null && !matchesFilter(network, node, filter, invalidNodeIds)) {
                continue;
            }
            result.add(node);
        }
        result.sort(comparator(network));
        return result;
    }

    public static boolean isEndpoint(RoadNode node) {
        return node != null && node.getDegree() == 1;
    }

    private static boolean matchesFilter(
            RoadNetwork network,
            RoadNode node,
            NodeFilter filter,
            Set<String> invalidNodeIds) {
        if (!filter.isActive()) {
            return true;
        }
        if (filter.junction() && node.isJunction()) {
            return true;
        }
        if (filter.endpoint() && isEndpoint(node)) {
            return true;
        }
        if (filter.manualElevation() && node.getManualElevation() != null) {
            return true;
        }
        if (filter.gradeSeparated() && node.isGradeSeparated()) {
            return true;
        }
        return filter.invalid() && invalidNodeIds.contains(node.getId());
    }

    private static boolean matchesSearch(RoadNetwork network, RoadNode node, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (node.getId().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        Vec2d pos = node.getPosition();
        String coordLabel = String.format(Locale.ROOT, "(%.0f, %.0f)", pos.x, pos.y).toLowerCase(Locale.ROOT);
        if (coordLabel.contains(query)) {
            return true;
        }
        String degreeLabel = "deg=" + node.getDegree();
        if (degreeLabel.contains(query)) {
            return true;
        }
        for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            String name = road.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            if (roadId.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static Comparator<RoadNode> comparator(RoadNetwork network) {
        return Comparator
            .comparingInt((RoadNode node) -> node.isJunction() ? 0 : 1)
            .thenComparing(Comparator.comparingInt((RoadNode node) -> distinctRoadCount(network, node)).reversed())
            .thenComparingDouble(node -> node.getPosition().x)
            .thenComparingDouble(node -> node.getPosition().y)
            .thenComparing(RoadNode::getId);
    }

    private static int distinctRoadCount(RoadNetwork network, RoadNode node) {
        return network.getDistinctRoadIdsAtNode(node.getId()).size();
    }
}
