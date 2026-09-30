package com.plot.plugin.road.crossing;

/** 纵剖面等 UI 层对 {@link RoadCrossing} 的稳定引用前缀。 */
public final class RoadCrossingRef {
    public static final String PREFIX = "crossing:";

    private RoadCrossingRef() {
    }

    public static String toRef(String crossingId) {
        return PREFIX + crossingId;
    }

    public static boolean isCrossingRef(String ref) {
        return ref != null && ref.startsWith(PREFIX);
    }

    public static String crossingIdFromRef(String ref) {
        if (!isCrossingRef(ref)) {
            return null;
        }
        return ref.substring(PREFIX.length());
    }
}
