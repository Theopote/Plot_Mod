package com.plot.plugin.road.crossing;

/** 纵剖面等 UI 层对 {@link RoadStackCrossing} 的稳定引用前缀。 */
public final class RoadStackCrossingRef {
    public static final String PREFIX = "stack-crossing:";

    private RoadStackCrossingRef() {
    }

    public static String toRef(String stackCrossingId) {
        return PREFIX + stackCrossingId;
    }

    public static boolean isStackCrossingRef(String ref) {
        return ref != null && ref.startsWith(PREFIX);
    }

    public static String stackCrossingIdFromRef(String ref) {
        if (!isStackCrossingRef(ref)) {
            return null;
        }
        return ref.substring(PREFIX.length());
    }
}
