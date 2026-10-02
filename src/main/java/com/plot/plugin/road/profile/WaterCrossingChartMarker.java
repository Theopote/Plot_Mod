package com.plot.plugin.road.profile;

import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy;

/** Water crossing span annotation for profile charts. */
public record WaterCrossingChartMarker(
        double startStation,
        double endStation,
        WaterCrossingStrategy strategy) {
}
