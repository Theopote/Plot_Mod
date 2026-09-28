package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.RoadVerticalMode;

/**
 * 判断是否对当前道路启用设计纵断面求解。
 */
public final class VerticalAlignmentProfileSupport {

    private VerticalAlignmentProfileSupport() {
    }

    public static boolean shouldUseVerticalAlignment(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return false;
        }
        RoadVerticalMode mode = road.getVerticalMode();
        if (!RoadStationing.isStationable(network, road)) {
            return false;
        }
        if (mode == RoadVerticalMode.FLAT) {
            return FlatVerticalIntentSupport.resolveIntent(network, road) != null;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        return mode == RoadVerticalMode.MANUAL_PROFILE
            && VerticalAlignmentGeometry.isEvaluable(alignment);
    }
}
