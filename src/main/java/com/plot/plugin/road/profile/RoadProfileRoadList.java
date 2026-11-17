package com.plot.plugin.road.profile;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadLengthDisplay;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiFormat;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 纵断面 UI 用的道路列表与摘要格式化。 */
public final class RoadProfileRoadList {

    private RoadProfileRoadList() {
    }

    public static List<Road> listStationableRoads(RoadNetwork network) {
        List<Road> roads = new ArrayList<>();
        if (network == null) {
            return roads;
        }
        for (Road road : network.getRoads().values()) {
            if (RoadStationing.isStationable(network, road)) {
                roads.add(road);
            }
        }
        roads.sort(Comparator.comparing(
            road -> RoadEdgeListHelper.formatRoadLabel(network, road),
            String.CASE_INSENSITIVE_ORDER));
        return roads;
    }

    public static String formatProfileRoadSummary(RoadNetwork network, Road road) {
        return formatProfileRoadSummary(network, road, null);
    }

    public static String formatProfileRoadSummary(
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        if (road == null || network == null) {
            return "";
        }
        String lengthText = coordinates != null
            ? RoadUiFormat.formatDistance(RoadLengthDisplay.roadBlocks(network, road, coordinates))
            : RoadUiFormat.formatDistance(RoadStationing.canonicalLength(network, road));
        return PlotI18n.tr(
            "plugin.road.profile_road_summary_line",
            RoadEdgeListHelper.formatRoadLabel(network, road),
            lengthText,
            RoadVerticalStrategy.fromRoad(road).label());
    }

    /**
     * 当前选中的可桩号道路；无选择或多选时返回 primary（若可桩号），绝不回退到列表首条。
     * 空选返回 {@code null}，避免侧栏假装已选中。
     */
    public static Road resolveActiveRoad(RoadNetwork network, RoadUiContext ctx) {
        if (network == null || ctx == null) {
            return null;
        }
        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        if (primary != null && RoadStationing.isStationable(network, primary)) {
            return primary;
        }
        return null;
    }

    /** 概览列表：多选时仅选中道路；否则全部可桩号道路（空选时只读浏览）。 */
    public static List<Road> listOverviewRoads(RoadNetwork network, RoadUiContext ctx) {
        List<Road> all = listStationableRoads(network);
        if (ctx == null || network == null) {
            return all;
        }
        var selectedIds = ctx.networkManager().getSelectedRoadIds();
        if (selectedIds == null || selectedIds.size() <= 1) {
            return all;
        }
        List<Road> filtered = new ArrayList<>();
        for (Road road : all) {
            if (road != null && selectedIds.contains(road.getId())) {
                filtered.add(road);
            }
        }
        return filtered;
    }
}
