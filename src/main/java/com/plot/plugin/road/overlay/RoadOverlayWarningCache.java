package com.plot.plugin.road.overlay;

import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.centerline.RoadCenterlineViolation;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashSet;
import java.util.Set;

/**
 * 道路叠加层警告态缓存：避免每帧 {@link RoadCenterlineShapeValidator#validate} 全路网扫描。
 */
public final class RoadOverlayWarningCache {
    private static long cachedRevision = Long.MIN_VALUE;
    private static Set<String> cachedWarnings = Set.of();

    private RoadOverlayWarningCache() {
    }

    static Set<String> warnings(RoadNetwork network, long networkRevision) {
        if (network == null) {
            return Set.of();
        }
        if (networkRevision == cachedRevision) {
            return cachedWarnings;
        }
        Set<String> warnings = new HashSet<>();
        for (RoadCenterlineViolation violation : RoadCenterlineShapeValidator.validate(network)) {
            if (violation.roadId() != null) {
                warnings.add(violation.roadId());
            }
        }
        cachedRevision = networkRevision;
        cachedWarnings = Set.copyOf(warnings);
        return cachedWarnings;
    }

    public static void invalidate() {
        cachedRevision = Long.MIN_VALUE;
        cachedWarnings = Set.of();
    }
}
