package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 道路边列表的排序与过滤（概览 / 编辑 Tab 共用）。
 */
public final class RoadEdgeListHelper {

    public enum SortMode {
        INSERTION("plugin.road.sort.insertion"),
        LENGTH_ASC("plugin.road.sort.length_asc"),
        LENGTH_DESC("plugin.road.sort.length_desc"),
        START_X("plugin.road.sort.start_x"),
        START_Y("plugin.road.sort.start_y"),
        ROAD_GROUP("plugin.road.sort.road_group");

        private final String i18nKey;

        SortMode(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String label() {
            return PlotI18n.tr(i18nKey);
        }
    }

    public record CoordFilter(boolean enabled, double minX, double maxX, double minY, double maxY) {
        public boolean matches(Vec2d point) {
            if (!enabled || point == null) {
                return true;
            }
            if (point.x < minX || point.x > maxX) {
                return false;
            }
            return point.y >= minY && point.y <= maxY;
        }
    }

    private RoadEdgeListHelper() {
    }

    public record RoadGroup(String roadId, String label, List<RoadEdge> edges) {
        public RoadGroup {
            edges = List.copyOf(edges);
        }
    }

    public static String formatRoadLabel(RoadNetwork network, Road road) {
        if (road == null) {
            return PlotI18n.tr("plugin.road.unassigned_road");
        }
        String name = road.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        int segmentCount = road.getSegmentIds().size();
        int width = road.getWidth() != null ? road.getWidth() : 0;
        int lanes = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
        return PlotI18n.tr("plugin.road.road_label", road.getId().substring(0, Math.min(8, road.getId().length())),
            lanes, width, segmentCount);
    }

    public static String formatEdgeLabel(RoadNetwork network, RoadEdge edge) {
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        return String.format(Locale.ROOT, "(%.0f,%.0f) -> (%.0f,%.0f), %.1fm",
            start != null ? start.getPosition().x : 0,
            start != null ? start.getPosition().y : 0,
            end != null ? end.getPosition().x : 0,
            end != null ? end.getPosition().y : 0,
            edge.getLength());
    }

    public static List<RoadEdge> filterAndSort(
            RoadNetwork network,
            List<RoadEdge> insertionOrder,
            String searchText,
            SortMode sortMode,
            CoordFilter coordFilter) {
        String query = searchText != null ? searchText.trim().toLowerCase(Locale.ROOT) : "";
        List<RoadEdge> result = new ArrayList<>();
        for (RoadEdge edge : insertionOrder) {
            if (!matchesSearch(network, edge, query)) {
                continue;
            }
            if (!matchesCoordFilter(network, edge, coordFilter)) {
                continue;
            }
            result.add(edge);
        }
        Comparator<RoadEdge> comparator = comparatorFor(network, sortMode);
        if (comparator != null) {
            result.sort(comparator);
        }
        return result;
    }

    public static List<RoadGroup> groupByRoad(RoadNetwork network, List<RoadEdge> edges) {
        java.util.LinkedHashMap<String, List<RoadEdge>> grouped = new java.util.LinkedHashMap<>();
        for (RoadEdge edge : edges) {
            String roadId = edge.getRoadId() != null ? edge.getRoadId() : "";
            grouped.computeIfAbsent(roadId, key -> new ArrayList<>()).add(edge);
        }
        List<RoadGroup> groups = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Road road = entry.getKey().isBlank() ? null : network.getRoad(entry.getKey());
            groups.add(new RoadGroup(entry.getKey(), formatRoadLabel(network, road), entry.getValue()));
        }
        return groups;
    }

    private static boolean matchesSearch(RoadNetwork network, RoadEdge edge, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String label = formatEdgeLabel(network, edge).toLowerCase(Locale.ROOT);
        if (label.contains(query)) {
            return true;
        }
        return edge.getId().toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean matchesCoordFilter(RoadNetwork network, RoadEdge edge, CoordFilter filter) {
        if (filter == null || !filter.enabled()) {
            return true;
        }
        RoadNode start = network.getNode(edge.getStartNodeId());
        if (start == null) {
            return false;
        }
        return filter.matches(start.getPosition());
    }

    private static Comparator<RoadEdge> comparatorFor(RoadNetwork network, SortMode sortMode) {
        return switch (sortMode) {
            case INSERTION -> null;
            case LENGTH_ASC -> Comparator.comparingDouble(RoadEdge::getLength);
            case LENGTH_DESC -> Comparator.comparingDouble(RoadEdge::getLength).reversed();
            case START_X -> Comparator.comparingDouble(edge -> startX(network, edge));
            case START_Y -> Comparator.comparingDouble(edge -> startY(network, edge));
            case ROAD_GROUP -> Comparator.comparing(edge -> edge.getRoadId() != null ? edge.getRoadId() : "");
        };
    }

    private static double startX(RoadNetwork network, RoadEdge edge) {
        RoadNode start = network.getNode(edge.getStartNodeId());
        return start != null ? start.getPosition().x : 0;
    }

    private static double startY(RoadNetwork network, RoadEdge edge) {
        RoadNode start = network.getNode(edge.getStartNodeId());
        return start != null ? start.getPosition().y : 0;
    }
}
