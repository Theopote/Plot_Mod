package com.plot.plugin.road.solid;

import java.util.EnumMap;
import java.util.Map;

/**
 * 跨层 BlockPos 冲突时的固定优先级（数值越大越优先保留）。
 */
public final class RoadSolidLayerPriority {
    private static final Map<RoadSolidLayer, Integer> PRIORITY = buildPriorityMap();

    private RoadSolidLayerPriority() {
    }

    private static Map<RoadSolidLayer, Integer> buildPriorityMap() {
        Map<RoadSolidLayer, Integer> map = new EnumMap<>(RoadSolidLayer.class);
        map.put(RoadSolidLayer.SUBGRADE, 0);
        map.put(RoadSolidLayer.SHOULDER, 1);
        map.put(RoadSolidLayer.SIDEWALK, 2);
        map.put(RoadSolidLayer.BIKE_LANE, 3);
        map.put(RoadSolidLayer.ROAD, 3);
        map.put(RoadSolidLayer.MEDIAN, 4);
        map.put(RoadSolidLayer.MARKING, 5);
        map.put(RoadSolidLayer.GUARDRAIL, 6);
        map.put(RoadSolidLayer.RETAINING_WALL, 6);
        map.put(RoadSolidLayer.BRIDGE, 7);
        map.put(RoadSolidLayer.TUNNEL, 8);
        map.put(RoadSolidLayer.STREETLIGHT, 9);
        return Map.copyOf(map);
    }

    public static int of(RoadSolidLayer layer) {
        return PRIORITY.getOrDefault(layer, 1);
    }

    public static int compare(RoadSolidLayer left, RoadSolidLayer right) {
        return Integer.compare(of(left), of(right));
    }
}
