package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadConstructionType;

/** Construction run span annotation for profile charts. */
public record ConstructionRunChartMarker(
        double startStation,
        double endStation,
        RoadConstructionType type) {
}
