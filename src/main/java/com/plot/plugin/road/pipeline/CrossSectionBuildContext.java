package com.plot.plugin.road.pipeline;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.station.RoadStationing;

/**
 * 单条边生成时的横断面解析上下文：无变量断面时返回固定值，否则按桩号阶梯解析。
 */
public final class CrossSectionBuildContext {

    private final ResolvedCrossSection fallback;
    private final RoadNetwork network;
    private final Road road;
    private final double segmentStartStation;
    private final RoadSystemConfig config;

    private CrossSectionBuildContext(
            ResolvedCrossSection fallback,
            RoadNetwork network,
            Road road,
            double segmentStartStation,
            RoadSystemConfig config) {
        this.fallback = fallback;
        this.network = network;
        this.road = road;
        this.segmentStartStation = segmentStartStation;
        this.config = config;
    }

    public static CrossSectionBuildContext fixed(ResolvedCrossSection section) {
        return new CrossSectionBuildContext(section, null, null, 0.0, null);
    }

    public static CrossSectionBuildContext forEdge(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config) {
        ResolvedCrossSection start = VariableCrossSectionResolver.resolveForEdge(network, edge, config);
        if (network == null || edge == null || config == null) {
            return fixed(start);
        }
        String roadId = edge.getRoadId();
        Road road = roadId != null ? network.getRoad(roadId) : null;
        if (road == null
            || !VariableCrossSectionResolver.hasVariableSections(road)
            || !RoadStationing.isStationable(network, road)) {
            return fixed(start);
        }
        double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
        if (segmentStart < 0.0) {
            return fixed(start);
        }
        return new CrossSectionBuildContext(start, network, road, segmentStart, config);
    }

    public boolean isVariable() {
        return network != null
            && road != null
            && config != null
            && VariableCrossSectionResolver.hasVariableSections(road);
    }

    public ResolvedCrossSection fallback() {
        return fallback;
    }

    public double segmentStartStation() {
        return segmentStartStation;
    }

    public ResolvedCrossSection resolve(double chainageMeters) {
        if (!isVariable()) {
            return fallback;
        }
        return VariableCrossSectionResolver.resolve(network, road, chainageMeters, config);
    }
}
