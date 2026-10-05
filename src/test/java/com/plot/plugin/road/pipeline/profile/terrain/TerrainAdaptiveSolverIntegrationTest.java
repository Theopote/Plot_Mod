package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.ProfileSolveSupport;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.profile.ProfileChartGuideSemantics;
import com.plot.plugin.road.profile.RoadProfileChartAssembler;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端：合成地形 + {@link RoadProfileSolver} FIT_TERRAIN v2 管线验收。
 */
class TerrainAdaptiveSolverIntegrationTest {

    @Test
    void mountainRoadProducesSmoothTrendAndGradeLimitedTargets() {
        Fixture fixture = mountainRoadFixture(250.0, 10.0);

        ProfileSolveResult result = solve(fixture);
        List<Integer> ground = result.profileGroundHeights();
        List<Integer> guide = result.profileGuideLine();
        List<Integer> targets = result.profileBuildHeights();

        assertTrue(ground.size() >= 20, "mountain road should sample many stations");
        assertInteriorVariation(targets);
        assertTrue(maxAdjacentDelta(guide) <= maxAdjacentDelta(ground) + 1e-6,
            "trend should be at least as smooth as raw terrain");
        assertTrue(maxAdjacentDelta(targets) <= 2.0,
            () -> "grade-limited targets should stay within per-station slope budget, got "
                + maxAdjacentDelta(targets));
        assertEquals(0, countLongFlatRuns(targets, 5),
            "long mountain road should not show flat-then-jump target pattern");
    }

    @Test
    void terrainFollowPresetsIncreaseGroundTrackingFromGentleToTight() {
        Fixture fixture = stepRoadFixture(100.0, 10.0, 8.0f);

        ProfileSolveResult gentle = solve(fixture.withPreset(TerrainFollowPreset.GENTLE));
        ProfileSolveResult standard = solve(fixture.withPreset(TerrainFollowPreset.STANDARD));
        ProfileSolveResult tight = solve(fixture.withPreset(TerrainFollowPreset.TIGHT));
        List<Integer> ground = gentle.profileGroundHeights();

        double gentleError = meanAbsoluteError(gentle.profileGuideLine(), ground);
        double standardError = meanAbsoluteError(standard.profileGuideLine(), ground);
        double tightError = meanAbsoluteError(tight.profileGuideLine(), ground);

        assertTrue(gentleError >= standardError - 0.5,
            () -> "gentle trend should track ground less closely than standard: "
                + gentleError + " vs " + standardError);
        assertTrue(standardError >= tightError - 0.5,
            () -> "standard trend should track ground less closely than tight: "
                + standardError + " vs " + tightError);
        assertNotEquals(gentle.profileGuideLine(), tight.profileGuideLine());
    }

    @Test
    void stepTerrainSeparatesRawTrendAndRoadLayers() {
        Fixture fixture = stepRoadFixture(100.0, 10.0, 8.0f);
        ProfileSolveResult result = solve(fixture);

        List<Integer> ground = result.profileGroundHeights();
        List<Integer> guide = result.profileGuideLine();
        List<Integer> targets = result.profileBuildHeights();

        assertNotEquals(ground, guide);
        assertTrue(maxAdjacentDelta(guide) < maxAdjacentDelta(ground));
        assertTrue(maxAdjacentDelta(targets) < 15.0);
        assertTrue(meanAbsoluteError(targets, guide) <= meanAbsoluteError(targets, ground) + 1.0,
            "road profile should follow trend at least as closely as raw ground on step terrain");
    }

    @Test
    void chartAssemblerExposesRawTrendAndRoadLayersForFitTerrain() {
        Fixture fixture = stepRoadFixture(100.0, 10.0, 8.0f);
        ProfileSolveResult solved = solve(fixture);
        RoadGenerationResult edgeResult = RoadProfileSolver.toProfileSnapshot(solved);

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(fixture.edge().getId(), edgeResult);

        RoadProfileChartData chart = RoadProfileChartAssembler.assemble(
            fixture.network(),
            fixture.road(),
            fixture.config(),
            edgeResults).orElseThrow();

        assertTrue(chart.hasCompleteRoadProfile());
        assertNotEquals(chart.groundElevations(), chart.guideElevations());
        assertEquals(ProfileChartGuideSemantics.TERRAIN_TREND,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.FIT_TERRAIN));
        assertFalse(chart.previewElevations().isEmpty());
    }

    @Test
    void autoSmoothGuideDiffersFromFitTerrainTrendOnStepTerrain() {
        Fixture fixture = stepRoadFixture(100.0, 10.0, 8.0f);

        ProfileSolveResult fit = solve(fixture);
        fixture.road().setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        ProfileSolveResult smooth = solve(fixture);

        assertNotEquals(fit.profileGuideLine(), smooth.profileGuideLine(),
            "AUTO_SMOOTH should keep fill-factor guide, not terrain trend filtering");
        assertEquals(ProfileChartGuideSemantics.GUIDE_LINE,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.AUTO_SMOOTH));
        assertEquals(ProfileChartGuideSemantics.TERRAIN_TREND,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.FIT_TERRAIN));
    }

    @Test
    void manualProfileModeDoesNotExposeTerrainTrendGuideSemantics() {
        assertEquals(ProfileChartGuideSemantics.NONE,
            ProfileChartGuideSemantics.fromVerticalMode(RoadVerticalMode.MANUAL_PROFILE));
    }

    @Test
    void cutFillBalanceReducesFillBiasOnAscendingTerrain() {
        Fixture fixture = ascendingTerrainFixture(100.0, 10.0, 8.0f);
        fixture.config().setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);

        ProfileSolveResult gentle = solve(fixture.withPreset(TerrainFollowPreset.GENTLE));
        ProfileSolveResult tight = solve(fixture.withPreset(TerrainFollowPreset.TIGHT));

        long gentleImbalance = Math.abs(estimateBalance(gentle));
        long tightImbalance = Math.abs(estimateBalance(tight));
        assertTrue(gentleImbalance <= tightImbalance,
            () -> "gentle balance weight should reduce cut/fill imbalance: "
                + gentleImbalance + " vs " + tightImbalance);
        assertTrue(meanBuild(gentle.profileBuildHeights()) >= meanBuild(tight.profileBuildHeights()),
            "gentle preset should raise fill-lagging profile more than tight");
    }

    private static long estimateBalance(ProfileSolveResult result) {
        List<Integer> ground = result.profileGroundHeights();
        List<Integer> build = result.profileBuildHeights();
        long cut = 0L;
        long fill = 0L;
        for (int i = 0; i < Math.min(ground.size(), build.size()); i++) {
            int diff = build.get(i) - ground.get(i);
            if (diff > 0) {
                fill += diff;
            } else if (diff < 0) {
                cut += -diff;
            }
        }
        return fill - cut;
    }

    private static double meanBuild(List<Integer> buildHeights) {
        return buildHeights.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    private static Fixture ascendingTerrainFixture(double lengthMeters, double stepMeters, float maxSlope) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ascending");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(maxSlope);
        road.setTerrainStyle(RoadTerrainStyle.BALANCED);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(lengthMeters, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(lengthMeters, 0)),
            road.getId());
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(maxSlope);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);
        return new Fixture(
            network,
            road,
            edge,
            config,
            sampledSegments(lengthMeters, stepMeters),
            ascendingTerrain(),
            TerrainFollowPreset.STANDARD);
    }

    private static TerrainSampler ascendingTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64 + (int) Math.round(point.x * 0.12);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static ProfileSolveResult solve(Fixture fixture) {
        return RoadProfileSolver.solveForEdge(
            fixture.segments(),
            fixture.terrain(),
            fixture.network(),
            fixture.edge(),
            fixture.config(),
            2.5,
            null,
            null,
            fixture.support());
    }

    private static Fixture mountainRoadFixture(double lengthMeters, double stepMeters) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("mountain");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(10.0f);
        road.setTerrainStyle(RoadTerrainStyle.BALANCED);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(lengthMeters, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(lengthMeters, 0)),
            road.getId());
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(10.0f);
        return new Fixture(
            network,
            road,
            edge,
            config,
            sampledSegments(lengthMeters, stepMeters),
            mountainTerrain(),
            TerrainFollowPreset.STANDARD);
    }

    private static Fixture stepRoadFixture(double lengthMeters, double stepMeters, float maxSlope) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("step");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(maxSlope);
        road.setTerrainStyle(RoadTerrainStyle.BALANCED);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(lengthMeters, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(lengthMeters, 0)),
            road.getId());
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(maxSlope);
        return new Fixture(
            network,
            road,
            edge,
            config,
            sampledSegments(lengthMeters, stepMeters),
            stepTerrain(lengthMeters / 2.0),
            TerrainFollowPreset.STANDARD);
    }

    private static TerrainSampler mountainTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64 + (int) Math.round(
                    8.0 * Math.sin(point.x / 25.0) + point.x * 0.04);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static TerrainSampler stepTerrain(double stepAtX) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return point.x < stepAtX ? 60 : 75;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static List<PathSegment> sampledSegments(double lengthMeters, double stepMeters) {
        Vec2d start = new Vec2d(0, 0);
        Vec2d end = new Vec2d(lengthMeters, 0);
        List<PathSegment> result = new ArrayList<>();
        Vec2d delta = end.subtract(start);
        int count = Math.max(1, (int) Math.round(lengthMeters / stepMeters));
        for (int i = 0; i < count; i++) {
            result.add(new PathSegment(
                start.add(delta.multiply((double) i / count)),
                start.add(delta.multiply((double) (i + 1) / count))));
        }
        return result;
    }

    private static void assertInteriorVariation(List<Integer> elevations) {
        int min = elevations.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = elevations.stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(max - min >= 2, "profile should vary along the road");
    }

    private static int countLongFlatRuns(List<Integer> elevations, int minRunLength) {
        int longest = 0;
        int current = 1;
        for (int i = 1; i < elevations.size(); i++) {
            if (elevations.get(i).equals(elevations.get(i - 1))) {
                current++;
            } else {
                longest = Math.max(longest, current);
                current = 1;
            }
        }
        longest = Math.max(longest, current);
        return longest >= minRunLength ? longest : 0;
    }

    private static double maxAdjacentDelta(List<Integer> elevations) {
        double max = 0.0;
        for (int i = 1; i < elevations.size(); i++) {
            max = Math.max(max, Math.abs(elevations.get(i) - elevations.get(i - 1)));
        }
        return max;
    }

    private static double meanAbsoluteError(List<Integer> actual, List<Integer> reference) {
        double sum = 0.0;
        int count = Math.min(actual.size(), reference.size());
        for (int i = 0; i < count; i++) {
            sum += Math.abs(actual.get(i) - reference.get(i));
        }
        return count > 0 ? sum / count : Double.POSITIVE_INFINITY;
    }

    private record Fixture(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadSystemConfig config,
            List<PathSegment> segments,
            TerrainSampler terrain,
            TerrainFollowPreset preset) {

        ProfileSolveSupport support() {
            return ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        }

        Fixture withPreset(TerrainFollowPreset newPreset) {
            road.setTerrainStyle(switch (newPreset) {
                case GENTLE -> com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH;
                case STANDARD -> com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED;
                case TIGHT -> com.plot.plugin.road.terrain.RoadTerrainStyle.FOLLOW;
            });
            return new Fixture(network, road, edge, config, segments, terrain, newPreset);
        }
    }
}
