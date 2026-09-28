package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;

/** Product-facing vertical strategy (terrain adaptive vs flat). */
public enum RoadVerticalStrategy {
    TERRAIN_ADAPTIVE,
    FLAT;

    public static RoadVerticalStrategy fromRoad(Road road) {
        if (road != null && road.getVerticalMode() == RoadVerticalMode.FLAT) {
            return FLAT;
        }
        return TERRAIN_ADAPTIVE;
    }

    public void applyToRoad(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (road == null) {
            return;
        }
        if (this == FLAT) {
            FlatVerticalIntentSupport.enableFlat(network, road, config);
        } else {
            FlatVerticalIntentSupport.enableTerrainAdaptive(network, road, config);
        }
    }

    public String labelKey() {
        return this == FLAT
            ? "plugin.road.vertical_strategy_flat"
            : "plugin.road.vertical_strategy_terrain_adaptive";
    }

    public String label() {
        return PlotI18n.tr(labelKey());
    }
}
