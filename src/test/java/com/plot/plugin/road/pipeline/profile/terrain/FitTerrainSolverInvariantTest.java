package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.ProfileSolveResult;
import com.plot.plugin.road.pipeline.profile.ProfileSolveSupport;
import com.plot.plugin.road.pipeline.profile.RoadProfileSolver;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final-output invariants for the full FIT_TERRAIN solver chain, including the
 * terminal double {@code projectFeasible()} pass.
 */
class FitTerrainSolverInvariantTest {

    @Test
    void finalProfileStillRespectsGradeChangeLimitAfterDoubleProjection() {
        TerrainFollowPreset preset = TerrainFollowPreset.STANDARD;
        List<Double> trend = List.of(
            62.0, 63.5, 65.0, 66.0, 67.5, 68.5, 67.0, 65.5, 64.0, 63.0, 64.5, 66.0, 68.0);
        List<Integer> ground = List.of(
            66, 67, 68, 69, 70, 71, 70, 69, 68, 67, 68, 69, 70);
        List<Double> distances = constantDistances(trend.size() - 1, 12.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);

        GradeLimitedProfileSolver.DesignSolveResult solved =
            GradeLimitedProfileSolver.solveDesignProfile(
                trend,
                ground,
                distances,
                slopes,
                62,
                68,
                preset,
                1.35f);

        FitTerrainProfileInvariants.assertDesignProfileInvariants(
            solved.designElevations(),
            ground,
            distances,
            slopes,
            preset,
            62,
            68,
            solved.manualEndpointsFeasible());

        double maxGradeChange = FitTerrainProfileInvariants.maxAdjacentGradeChange(
            solved.designElevations(), distances);
        assertTrue(maxGradeChange <= preset.maxGradeChangePercent() + 1e-6,
            () -> "double projection must leave grade-change cap intact, got " + maxGradeChange);
    }

    @Test
    void solverFinalInvariantsHoldOnConstrainedProfile() {
        TerrainFollowPreset preset = TerrainFollowPreset.STANDARD;
        List<Double> trend = List.of(
            60.0, 61.0, 62.5, 64.0, 65.5, 67.0, 68.0, 69.5, 71.0, 72.0, 73.0);
        List<Integer> ground = List.of(
            64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74);
        List<Double> distances = constantDistances(trend.size() - 1, 15.0);
        List<Float> slopes = constantSlopes(distances.size(), 8.0f);
        float fillFactor = 1.35f;

        List<Double> withoutCutFill = GradeLimitedProfileSolver.solveDesignProfile(
            trend, null, distances, slopes, 60, 72, preset, fillFactor).designElevations();
        GradeLimitedProfileSolver.DesignSolveResult solved =
            GradeLimitedProfileSolver.solveDesignProfile(
                trend, ground, distances, slopes, 60, 72, preset, fillFactor);

        FitTerrainProfileInvariants.assertDesignProfileInvariants(
            solved.designElevations(),
            ground,
            distances,
            slopes,
            preset,
            60,
            72,
            solved.manualEndpointsFeasible());

        long imbalanceWithout = Math.abs(FitTerrainProfileInvariants.cutFillImbalance(
            ground, withoutCutFill, fillFactor));
        long imbalanceWith = Math.abs(FitTerrainProfileInvariants.cutFillImbalance(
            ground, solved.designElevations(), fillFactor));
        assertTrue(imbalanceWith <= imbalanceWithout,
            () -> "cut/fill objective should not worsen imbalance: "
                + imbalanceWith + " vs " + imbalanceWithout);
    }

    @Test
    void fitTerrainFinalInvariantsHoldEndToEnd() {
        ScenarioFixture fixture = Scenario.MOUNTAIN.build(200.0, 10.0, 8.0f);
        ProfileSolveResult result = solve(fixture, 62, 70);

        List<Double> segmentDistances = FitTerrainProfileInvariants.segmentDistancesFromCumulative(
            result.profileDistances());
        List<Float> slopes = constantSlopes(segmentDistances.size(), fixture.maxSlope());

        FitTerrainProfileInvariants.assertDesignProfileInvariants(
            result.profileDesignElevations(),
            result.profileGroundHeights(),
            segmentDistances,
            slopes,
            fixture.preset(),
            62,
            70,
            result.manualEndpointConstraintFeasible());
        FitTerrainProfileInvariants.assertBuildRasterizationReasonable(
            result.profileDesignElevations(),
            result.profileBuildHeights());
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void fitTerrainScenarioSatisfiesFinalInvariants(Scenario scenario) {
        ScenarioFixture fixture = scenario.build(150.0, 10.0, 8.0f);
        ProfileSolveResult result = solve(fixture, null, null);

        List<Double> segmentDistances = FitTerrainProfileInvariants.segmentDistancesFromCumulative(
            result.profileDistances());
        List<Float> slopes = constantSlopes(segmentDistances.size(), fixture.maxSlope());

        FitTerrainProfileInvariants.assertDesignProfileInvariants(
            result.profileDesignElevations(),
            result.profileGroundHeights(),
            segmentDistances,
            slopes,
            fixture.preset(),
            null,
            null,
            result.manualEndpointConstraintFeasible());
        FitTerrainProfileInvariants.assertBuildRasterizationReasonable(
            result.profileDesignElevations(),
            result.profileBuildHeights());
    }

    private enum Scenario {
        MOUNTAIN {
            @Override
            TerrainSampler terrain() {
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
        },
        STEP {
            @Override
            TerrainSampler terrain() {
                return stepTerrain(75.0);
            }
        },
        ASCENDING {
            @Override
            TerrainSampler terrain() {
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
        },
        CREST_SAG {
            @Override
            TerrainSampler terrain() {
                return new TerrainSampler() {
                    @Override
                    public int sampleSurfaceY(Vec2d point) {
                        return 66 + (int) Math.round(4.0 * Math.sin(point.x / 18.0));
                    }

                    @Override
                    public boolean isSolidBlock(int x, int y, int z) {
                        return false;
                    }
                };
            }
        };

        ScenarioFixture build(double lengthMeters, double stepMeters, float maxSlope) {
            RoadNetwork network = new RoadNetwork();
            Road road = network.createRoad(name().toLowerCase());
            road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
            road.setMaxSlope(maxSlope);
            road.setTerrainFollowPreset(TerrainFollowPreset.STANDARD);
            RoadNode start = network.createNode(new Vec2d(0, 0));
            RoadNode end = network.createNode(new Vec2d(lengthMeters, 0));
            RoadEdge edge = network.createEdge(
                start.getId(),
                end.getId(),
                List.of(new Vec2d(0, 0), new Vec2d(lengthMeters, 0)),
                road.getId());
            RoadSystemConfig config = new RoadSystemConfig("test");
            config.setMaxSlope(maxSlope);
            config.setFillFactor(1.35f);
            return new ScenarioFixture(
                network,
                road,
                edge,
                config,
                sampledSegments(lengthMeters, stepMeters),
                terrain(),
                TerrainFollowPreset.STANDARD,
                maxSlope);
        }

        abstract TerrainSampler terrain();
    }

    private record ScenarioFixture(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadSystemConfig config,
            List<PathSegment> segments,
            TerrainSampler terrain,
            TerrainFollowPreset preset,
            float maxSlope) {

        ProfileSolveSupport support() {
            return ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        }
    }

    private static ProfileSolveResult solve(
            ScenarioFixture fixture,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        return RoadProfileSolver.solveForEdge(
            fixture.segments(),
            fixture.terrain(),
            fixture.network(),
            fixture.edge(),
            fixture.config(),
            2.5,
            manualStartHeight,
            manualEndHeight,
            fixture.support());
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

    private static List<Double> constantDistances(int count, double distance) {
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            distances.add(distance);
        }
        return distances;
    }

    private static List<Float> constantSlopes(int count, float slope) {
        List<Float> slopes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slopes.add(slope);
        }
        return slopes;
    }
}
