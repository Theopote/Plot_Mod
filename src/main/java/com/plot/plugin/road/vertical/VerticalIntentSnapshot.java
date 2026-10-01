package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.Set;

/** 拓扑拆分前捕获的道路级垂直意图，供各分量独立裁剪后应用。 */
public final class VerticalIntentSnapshot {

    private final RoadVerticalMode verticalMode;
    private final FlatVerticalIntent flatIntent;

    private VerticalIntentSnapshot(RoadVerticalMode verticalMode, FlatVerticalIntent flatIntent) {
        this.verticalMode = verticalMode;
        this.flatIntent = flatIntent;
    }

    public static VerticalIntentSnapshot capture(Road road) {
        if (road == null) {
            return new VerticalIntentSnapshot(null, null);
        }
        RoadVerticalMode mode = road.getStoredVerticalMode();
        if (mode == null && road.getVerticalMode() != RoadVerticalMode.AUTO_SMOOTH) {
            mode = road.getVerticalMode();
        }
        FlatVerticalIntent intent = road.getFlatVerticalIntent() != null
            ? road.getFlatVerticalIntent().copy()
            : null;
        return new VerticalIntentSnapshot(mode, intent);
    }

    public void applyTo(RoadNetwork network, Road road, Set<String> edgeIds) {
        RoadVerticalIntentTransforms.applyCapturedIntent(
            road, verticalMode, flatIntent, network, edgeIds);
    }
}
