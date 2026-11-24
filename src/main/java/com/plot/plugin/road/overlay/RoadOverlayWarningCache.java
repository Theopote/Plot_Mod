package com.plot.plugin.road.overlay;

import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.centerline.RoadCenterlineViolation;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashSet;
import java.util.Set;

/**
 * 道路叠加层警告态缓存：避免每帧 {@link RoadCenterlineShapeValidator#validate} 全路网扫描。
 * 实例归属 {@link com.plot.plugin.road.ui.RoadUiContext}，随 UI 上下文生命周期失效。
 */
public final class RoadOverlayWarningCache {
    private long cachedRevision = Long.MIN_VALUE;
    private RoadNetwork cachedNetwork;
    private Set<String> cachedWarnings = Set.of();

    public Set<String> warnings(RoadNetwork network, long networkRevision) {
        if (network == null) {
            return Set.of();
        }
        if (network == cachedNetwork && networkRevision == cachedRevision) {
            return cachedWarnings;
        }
        Set<String> warnings = new HashSet<>();
        for (RoadCenterlineViolation violation : RoadCenterlineShapeValidator.validate(network)) {
            if (violation.roadId() != null) {
                warnings.add(violation.roadId());
            }
        }
        cachedNetwork = network;
        cachedRevision = networkRevision;
        cachedWarnings = Set.copyOf(warnings);
        return cachedWarnings;
    }

    public void invalidate() {
        cachedRevision = Long.MIN_VALUE;
        cachedNetwork = null;
        cachedWarnings = Set.of();
    }
}
