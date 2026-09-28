package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

/** Resolves the evaluable vertical alignment for profile solving and overlays. */
public final class RoadVerticalAlignmentResolver {

    private RoadVerticalAlignmentResolver() {
    }

    public static RoadVerticalAlignment resolve(
            RoadNetwork network,
            Road road,
            double maxGradePercent) {
        if (road == null) {
            return null;
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent == null) {
                return null;
            }
            return FlatProfileCompiler.compile(network, road, intent, maxGradePercent);
        }
        return road.getVerticalAlignment();
    }

    public static RoadVerticalAlignment resolveSynced(
            RoadNetwork network,
            Road road,
            double maxGradePercent) {
        if (road != null && road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntentSupport.syncCompiledAlignment(network, road, maxGradePercent);
        }
        return road != null ? road.getVerticalAlignment() : null;
    }
}
