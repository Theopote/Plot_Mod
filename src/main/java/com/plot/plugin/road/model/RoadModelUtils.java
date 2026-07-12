package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;

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
        return network.getRoad(edge.getRoadId());
    }

    public static ResolvedCrossSection resolveCrossSection(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        if (road != null) {
            return road.getCrossSection().resolve(defaults);
        }
        return ResolvedCrossSection.fromConfig(defaults);
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
        return resolveCrossSection(network, edge, defaults).carriagewayMaterial;
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

    public static boolean getEffectiveIncludeDrainage(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        return resolveCrossSection(network, edge, defaults).includeDrain;
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
