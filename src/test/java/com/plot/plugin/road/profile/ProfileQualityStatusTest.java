package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileQualityStatusTest {

    @Test
    void reportsWarningsForSteepGrade() {
        RoadProfileChartData chart = new RoadProfileChartData(
            "r1",
            100.0,
            List.of(0.0, 50.0, 100.0),
            List.of(64.0, 64.0, 64.0),
            List.of(64.0, 80.0, 64.0),
            List.of(64.0, 80.0, 64.0),
            List.of(),
            List.of(64.0, 64.0, 64.0),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of());
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(chart, null, config);
        assertTrue(summary.hasWarnings());
        assertTrue(summary.dimensions().stream()
            .anyMatch(d -> d.labelKey().contains("grade_warning")));
    }

    @Test
    void flatProfileReportsAllGood() {
        RoadProfileChartData chart = new RoadProfileChartData(
            "r1",
            100.0,
            List.of(0.0, 50.0, 100.0),
            List.of(64.0, 64.0, 64.0),
            List.of(64.0, 65.0, 66.0),
            List.of(64.0, 65.0, 66.0),
            List.of(),
            List.of(64.0, 64.0, 64.0),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of());
        RoadSystemConfig config = new RoadSystemConfig("test");

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(chart, null, config);
        assertFalse(summary.hasWarnings());
    }
}
