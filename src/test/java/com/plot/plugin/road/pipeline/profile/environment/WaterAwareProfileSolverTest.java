package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.plugin.road.pipeline.profile.terrain.GradeLimitedProfileSolver;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterAwareProfileSolverTest {

    @Test
    void waterMinimumElevationClampsDesign() {
        TerrainFollowPreset preset = TerrainFollowPreset.STANDARD;
        List<Double> trend = List.of(68.0, 68.0, 70.0, 70.0, 68.0);
        List<Integer> ground = List.of(68, 68, 55, 55, 68);
        List<Double> distances = constantDistances(4, 15.0);
        List<Float> slopes = constantSlopes(4, 8.0f);
        EnvironmentProfile environment = bridgeEnvironment();
        List<WaterCrossing> crossings = WaterCrossingClassifier.classify(
            WaterCrossingDetector.detect(environment),
            WaterCrossingSettings.defaults(),
            preset,
            60.0);
        VerticalStationConstraints.StationElevationBounds bounds = VerticalStationConstraints.toBounds(
            VerticalStationConstraints.build(
                environment,
                crossings,
                trend,
                WaterCrossingSettings.defaults()));

        GradeLimitedProfileSolver.DesignSolveResult solved = GradeLimitedProfileSolver.solveDesignProfile(
            trend,
            ground,
            distances,
            slopes,
            null,
            null,
            preset,
            1.0f,
            bounds);

        for (int i = 0; i < solved.designElevations().size(); i++) {
            if (environment.samples().get(i).hasWater()) {
                assertTrue(
                    solved.designElevations().get(i) >= 70.0 - 1e-6,
                    "water station " + i + " must clear water surface + 1 block");
            }
        }
    }

    @Test
    void causewayAllowedOnShallowPond() {
        TerrainFollowPreset preset = TerrainFollowPreset.STANDARD;
        List<Double> trend = List.of(64.0, 64.5, 65.0, 64.5, 64.0);
        List<Integer> ground = List.of(64, 63, 62, 63, 64);
        List<Double> distances = constantDistances(4, 1.0);
        List<Float> slopes = constantSlopes(4, 8.0f);
        EnvironmentProfile environment = shallowPondEnvironment();
        WaterCrossingSettings settings = WaterCrossingSettings.defaults();
        List<WaterCrossing> crossings = WaterCrossingClassifier.classify(
            WaterCrossingDetector.detect(environment),
            settings,
            preset,
            4.0);
        assertEquals(WaterCrossingStrategy.CAUSEWAY, crossings.getFirst().strategy());
        List<VerticalStationConstraint> constraints = VerticalStationConstraints.build(
            environment, crossings, trend, settings);
        assertEquals(63.0, constraints.get(2).minimumElevation(), 1e-6);
        VerticalStationConstraints.StationElevationBounds bounds =
            VerticalStationConstraints.toBounds(constraints);
        GradeLimitedProfileSolver.DesignSolveResult solved = GradeLimitedProfileSolver.solveDesignProfile(
            trend,
            ground,
            distances,
            slopes,
            null,
            null,
            preset,
            1.0f,
            bounds);
        assertTrue(solved.designElevations().get(2) >= 63.0 - 1e-6,
            "causeway road surface must stay at or above water surface");
    }

    @Test
    void tunnelNotAllowedByDefault() {
        TerrainFollowPreset preset = TerrainFollowPreset.STANDARD;
        List<Double> trend = List.of(60.0, 58.0, 56.0, 58.0, 60.0);
        List<Integer> ground = List.of(55, 55, 55, 55, 55);
        List<Double> distances = constantDistances(4, 15.0);
        List<Float> slopes = constantSlopes(4, 8.0f);
        EnvironmentProfile environment = bridgeEnvironment();
        WaterCrossingSettings settings = WaterCrossingSettings.defaults();
        List<WaterCrossing> crossings = WaterCrossingClassifier.classify(
            WaterCrossingDetector.detect(environment),
            settings,
            preset,
            60.0);
        VerticalStationConstraints.StationElevationBounds bounds = VerticalStationConstraints.toBounds(
            VerticalStationConstraints.build(environment, crossings, trend, settings));

        GradeLimitedProfileSolver.DesignSolveResult solved = GradeLimitedProfileSolver.solveDesignProfile(
            trend,
            ground,
            distances,
            slopes,
            null,
            null,
            preset,
            1.0f,
            bounds);

        for (int i = 0; i < solved.designElevations().size(); i++) {
            Integer water = environment.samples().get(i).waterSurfaceY();
            if (water != null) {
                assertTrue(
                    solved.designElevations().get(i) >= water + settings.waterRoadClearanceBlocks() - 1e-6,
                    "underwater road must stay disabled by default");
            }
        }
    }

    private static EnvironmentProfile bridgeEnvironment() {
        return new EnvironmentProfile(
            List.of(
                EnvironmentSample.land(0.0, 68),
                EnvironmentSample.land(15.0, 68),
                new EnvironmentSample(30.0, 55, 69, 14, SurfaceContext.DEEP_WATER),
                new EnvironmentSample(45.0, 55, 69, 14, SurfaceContext.DEEP_WATER),
                EnvironmentSample.land(60.0, 68)),
            List.of(0.0, 15.0, 30.0, 45.0, 60.0));
    }

    private static EnvironmentProfile shallowPondEnvironment() {
        return new EnvironmentProfile(
            List.of(
                EnvironmentSample.land(0.0, 64),
                new EnvironmentSample(1.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                new EnvironmentSample(2.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                new EnvironmentSample(3.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                EnvironmentSample.land(4.0, 64)),
            List.of(0.0, 1.0, 2.0, 3.0, 4.0));
    }

    private static List<Double> constantDistances(int count, double distance) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(ignored -> distance)
            .toList();
    }

    private static List<Float> constantSlopes(int count, float slope) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(ignored -> slope)
            .toList();
    }
}
