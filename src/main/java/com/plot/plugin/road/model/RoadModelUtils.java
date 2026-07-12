package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;

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

    public static int getEffectiveWidth(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getEffectiveWidth(defaults) : defaults.getRoadWidth();
    }

    public static String getEffectiveMaterial(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getEffectiveMaterial(defaults) : defaults.getSelectedMaterial();
    }

    public static boolean getEffectiveIncludeSidewalk(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getEffectiveIncludeSidewalk(defaults) : defaults.isIncludeSidewalk();
    }

    public static int getEffectiveSidewalkWidth(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getEffectiveSidewalkWidth(defaults) : defaults.getSidewalkWidth();
    }

    public static String getEffectiveSidewalkMaterial(RoadNetwork network, RoadEdge edge, RoadSystemConfig defaults) {
        Road road = resolveRoad(network, edge);
        return road != null ? road.getEffectiveSidewalkMaterial(defaults) : defaults.getSelectedSidewalkMaterial();
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
