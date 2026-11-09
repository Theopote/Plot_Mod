package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.core.material.MaterialMix;

/**
 * 从路网解析道路工程属性的辅助方法。
 */
public final class RoadModelUtils {
    private RoadModelUtils() {
    }

    public static Road resolveRoad(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null || edge.getRoadId() == null) {
            return null;
        }
        return network.getRoadForEdge(edge);
    }

    public static ResolvedCrossSection resolveCrossSection(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig defaults) {
        return com.plot.plugin.road.model.section.VariableCrossSectionResolver.resolveForEdge(
            network,
            edge,
            defaults
        );
    }

    /**
     * 在指定道路节点桩号处解析横断面（路口材质/条带分类用交叉点宽度，而非 Edge 起点）。
     */
    public static ResolvedCrossSection resolveCrossSectionAtNode(
            RoadNetwork network,
            RoadEdge edge,
            String nodeId,
            RoadSystemConfig defaults) {
        if (network == null || edge == null || defaults == null) {
            return ResolvedCrossSection.fromConfig(defaults);
        }
        Road road = resolveRoad(network, edge);
        if (road == null) {
            return ResolvedCrossSection.fromConfig(defaults);
        }
        if (nodeId != null
                && com.plot.plugin.road.model.section.VariableCrossSectionResolver.hasVariableSections(road)
                && com.plot.plugin.road.station.RoadStationing.isStationable(network, road)) {
            java.util.OptionalDouble station =
                com.plot.plugin.road.station.RoadStationing.stationAtNode(network, road, edge, nodeId);
            if (station.isPresent()) {
                return com.plot.plugin.road.model.section.VariableCrossSectionResolver.resolve(
                    network, road, station.getAsDouble(), defaults);
            }
        }
        return resolveCrossSection(network, edge, defaults);
    }

    /**
     * 自路口节点沿进口向外 {@code alongCanvas}（画布距离）处的横断面。
     */
    public static ResolvedCrossSection resolveCrossSectionAlongApproach(
            RoadNetwork network,
            RoadEdge edge,
            String nodeId,
            double alongCanvas,
            RoadSystemConfig defaults) {
        if (network == null || edge == null || defaults == null) {
            return ResolvedCrossSection.fromConfig(defaults);
        }
        Road road = resolveRoad(network, edge);
        if (road == null
                || !com.plot.plugin.road.model.section.VariableCrossSectionResolver.hasVariableSections(road)
                || !com.plot.plugin.road.station.RoadStationing.isStationable(network, road)
                || nodeId == null) {
            return resolveCrossSection(network, edge, defaults);
        }
        java.util.OptionalDouble nodeStation =
            com.plot.plugin.road.station.RoadStationing.stationAtNode(network, road, edge, nodeId);
        if (nodeStation.isEmpty()) {
            return resolveCrossSection(network, edge, defaults);
        }
        boolean outwardIncreases = com.plot.plugin.road.RoadJunctionGeometry.outwardIncreasesStation(
            network, road, edge, nodeId);
        double baseInstance = com.plot.plugin.road.station.RoadStationing.toInstanceChainage(
            network, road, nodeStation.getAsDouble());
        double along = Math.max(0.0, alongCanvas);
        double instanceStation = outwardIncreases ? baseInstance + along : baseInstance - along;
        double station = com.plot.plugin.road.station.RoadStationing.toCanonicalChainage(
            network, road, Math.max(0.0, instanceStation));
        return com.plot.plugin.road.model.section.VariableCrossSectionResolver.resolve(
            network, road, station, defaults);
    }

    public static RoadCrossSection resolveCrossSectionTemplate(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getCrossSection() : RoadCrossSection.fromConfig(defaults);
    }

    public static int getEffectiveWidth(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).carriagewayWidth;
    }

    public static String getEffectiveMaterial(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        MaterialMix mix = resolveCrossSection(network, edge, defaults).carriagewayMaterial;
        return mix != null ? mix.getPrimaryMaterial() : null;
    }

    public static boolean getEffectiveIncludeSidewalk(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).includeSidewalk;
    }

    public static int getEffectiveSidewalkWidth(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).sidewalkWidth;
    }

    public static String getEffectiveSidewalkMaterial(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).sidewalkMaterial;
    }

    public static boolean getEffectiveIncludeShoulder(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).includeShoulder;
    }

    public static int getEffectiveShoulderWidth(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).shoulderWidth;
    }

    public static String getEffectiveShoulderMaterial(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).shoulderMaterial;
    }

    public static Integer getStreetlightSpacing(RoadNetwork network, RoadEdge edge) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getStreetlightSpacing() : null;
    }

    public static float getEffectiveMaxSlope(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig defaults) {
        return getEffectiveMaxSlope(network, edge, defaults, 0.0);
    }

    public static float getEffectiveMaxSlope(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig defaults,
            double distanceAlongEdge) {
        if (edge.getSlopeOverrides() != null) {
            for (RoadEdge.SlopeOverride override : edge.getSlopeOverrides()) {
                if (distanceAlongEdge >= override.startDistance && distanceAlongEdge <= override.endDistance) {
                    return override.maxSlope;
                }
            }
        }
        Road road = resolveRoad(network, edge);
        return road != null ? road.getEffectiveMaxSlope(defaults) : defaults.getMaxSlope();
    }
}
