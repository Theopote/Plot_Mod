package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
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
        if (road == null || network == null) {
            return "";
        }
        double length = RoadStationing.canonicalLength(network, road);
        return PlotI18n.tr(
            "plugin.road.profile_road_summary_line",
            RoadEdgeListHelper.formatRoadLabel(network, road),
            RoadUiFormat.format(length),
            RoadVerticalStrategy.fromRoad(road).label());
    }

    public static Road resolveActiveRoad(RoadNetwork network, RoadUiContext ctx) {
        if (network == null || ctx == null) {
            return null;
        }
        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        if (primary != null && RoadStationing.isStationable(network, primary)) {
            return primary;
        }
        List<Road> roads = listStationableRoads(network);
        return roads.isEmpty() ? null : roads.getFirst();
    }
}
