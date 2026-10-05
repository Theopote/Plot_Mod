package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;

/** 纵断面质量指标紧凑摘要（产品三态）。 */
public final class ProfileQualitySummary {

    private ProfileQualitySummary() {
    }

    public static void render(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config,
            boolean previewStale) {
        ProfileQualityStatus.render(chart, road, config, previewStale);
    }

    public static String compactLine(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config) {
        return ProfileQualityStatus.compactLine(chart, road, config);
    }
}
