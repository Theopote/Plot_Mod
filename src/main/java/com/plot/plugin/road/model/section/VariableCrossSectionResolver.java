package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;

/**
 * 沿桩号解析有效横断面：阶梯函数（取不大于查询桩号的最大断面桩号）。
 */
public final class VariableCrossSectionResolver {

    private static final double EPSILON = 1e-9;

    private VariableCrossSectionResolver() {
    }

    public static boolean hasVariableSections(Road road) {
        return road != null
            && road.getVariableCrossSections() != null
            && !road.getVariableCrossSections().isEmpty();
    }

    public static RoadCrossSection resolveTemplate(Road road, double chainage) {
        if (road == null) {
            return new RoadCrossSection();
        }
        RoadCrossSection base = road.getCrossSection();
        RoadVariableCrossSections variable = road.getVariableCrossSections();
        if (variable == null || variable.isEmpty()) {
            return base != null ? base : new RoadCrossSection();
        }
        List<StationCrossSection> sorted = variable.sortedStations();
        if (sorted.isEmpty()) {
            return base != null ? base : new RoadCrossSection();
        }

        StationCrossSection active = null;
        for (StationCrossSection entry : sorted) {
            if (entry.getStation() <= chainage + EPSILON) {
                active = entry;
            } else {
                break;
            }
        }
        if (active != null) {
            return active.getCrossSection();
        }
        return base != null ? base : new RoadCrossSection();
    }

    public static ResolvedCrossSection resolve(
            RoadNetwork network,
            Road road,
            double chainage,
            RoadSystemConfig defaults) {
        return resolveTemplate(road, chainage).resolve(defaults);
    }

    public static ResolvedCrossSection resolveForEdge(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig defaults) {
        if (network == null || edge == null) {
            return ResolvedCrossSection.fromConfig(defaults);
        }
        String roadId = edge.getRoadId();
        Road road = roadId != null ? network.getRoad(roadId) : null;
        if (road == null) {
            return ResolvedCrossSection.fromConfig(defaults);
        }
        if (RoadStationing.isStationable(network, road)) {
            double station = RoadStationing.segmentStartStation(network, road, edge.getId());
            if (station >= 0.0) {
                return resolve(network, road, station, defaults);
            }
        }
        return road.getCrossSection().resolve(defaults);
    }

    public static String describe(StationCrossSection entry, RoadStationFormat format) {
        RoadCrossSection section = entry.getCrossSection();
        int width = section.getCarriageway().getWidth() != null
            ? section.getCarriageway().getWidth()
            : 0;
        int lanes = section.getCarriageway().getEffectiveLaneCount();
        return RoadStationing.format(entry.getStation(), format)
            + " W=" + width + " L=" + lanes;
    }
}
