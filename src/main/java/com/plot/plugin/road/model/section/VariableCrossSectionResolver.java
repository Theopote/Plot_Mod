package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;

/**
 * 沿桩号解析有效横断面。
 * <p>
 * {@link VariableCrossSectionTransitionMode#STEP}：阶梯函数（取不大于查询桩号的最大断面桩号）。
 * {@link VariableCrossSectionTransitionMode#SMOOTH}：相邻桩号间对宽度等连续量线性插值。
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

    /**
     * 返回当前生效的断面模板（阶梯语义：不大于 chainage 的最近桩号；之前用道路默认）。
     * 平滑模式下生成/预览请用 {@link #resolve}，本方法仍返回键控模板以便编辑与描述。
     */
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
        if (road == null) {
            return ResolvedCrossSection.fromConfig(defaults);
        }
        RoadCrossSection base = road.getCrossSection() != null ? road.getCrossSection() : new RoadCrossSection();
        RoadVariableCrossSections variable = road.getVariableCrossSections();
        if (variable == null || variable.isEmpty()) {
            return base.resolve(defaults);
        }
        List<StationCrossSection> sorted = variable.sortedStations();
        if (sorted.isEmpty()) {
            return base.resolve(defaults);
        }

        if (variable.getTransitionMode() != VariableCrossSectionTransitionMode.SMOOTH
                || sorted.size() == 1) {
            return resolveTemplate(road, chainage).resolve(defaults);
        }

        StationCrossSection prev = null;
        StationCrossSection next = null;
        for (StationCrossSection entry : sorted) {
            if (entry.getStation() <= chainage + EPSILON) {
                prev = entry;
            } else {
                next = entry;
                break;
            }
        }
        if (prev == null) {
            return base.resolve(defaults);
        }
        if (next == null) {
            return prev.getCrossSection().resolve(defaults);
        }
        double span = next.getStation() - prev.getStation();
        double t = span > EPSILON ? (chainage - prev.getStation()) / span : 0.0;
        if (t <= 0.0) {
            return prev.getCrossSection().resolve(defaults);
        }
        if (t >= 1.0) {
            return next.getCrossSection().resolve(defaults);
        }
        return ResolvedCrossSection.lerpContinuous(
            prev.getCrossSection().resolve(defaults),
            next.getCrossSection().resolve(defaults),
            t);
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

    public static String describe(StationCrossSection entry, ChainageDisplayContext display) {
        if (entry == null || display == null) {
            return "";
        }
        RoadCrossSection section = entry.getCrossSection();
        int width = section.getCarriageway().getWidth() != null
            ? section.getCarriageway().getWidth()
            : 0;
        int lanes = section.getCarriageway().getEffectiveLaneCount();
        return display.format(entry.getStation())
            + " W=" + width + " L=" + lanes;
    }
}
