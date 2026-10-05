package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
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
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario matrix A–L for water-aware FIT_TERRAIN profile solving.
 */
class WaterCrossingScenarioTest {

    @ParameterizedTest
    @EnumSource(value = ScenarioId.class, names = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "L"})
    void dryAndDirectionalScenariosSatisfyInvariants(ScenarioId scenario) {
        ScenarioFixture fixture = scenario.build(150.0, 10.0, 8.0f);
        ProfileSolveResult result = solve(fixture, null, null);
        assertScenarioInvariants(fixture, result, null, null);
    }

    @Test
    void scenarioJ_constrainedEndpointsOverRiverMaintainBridgeClearance() {
        ScenarioFixture fixture = ScenarioId.J.build(120.0, 10.0, 8.0f);
        ProfileSolveResult result = solve(fixture, 70, 70);
        assertScenarioInvariants(fixture, result, 70, 70);
        for (int i = 0; i < result.profileDesignElevations().size(); i++) {
            Integer water = result.profileWaterHeights().get(i);
            if (water != null) {
                assertTrue(
                    result.profileDesignElevations().get(i) >= water + 1 - 1e-6,
                    "river station must stay above water + clearance");
            }
        }
        assertEquals(70.0, result.profileDesignElevations().getFirst(), 1e-6);
        assertEquals(70.0, result.profileDesignElevations().getLast(), 1e-6);
    }

    @Test
    void scenarioK_multiSegmentPathStubStillSolvable() {
        ScenarioFixture fixture = ScenarioId.K.build(120.0, 8.0, 8.0f);
        ProfileSolveResult result = solve(fixture, null, null);
        assertTrue(result.heightInfos().size() >= 3);
        assertScenarioInvariants(fixture, result, null, null);
    }

    @Test
    void scenarioL_reversedEdgeMatchesForwardSolve() {
        ScenarioFixture forward = ScenarioId.L.build(100.0, 10.0, 8.0f);
        ProfileSolveResult forwardResult = solve(forward, 62, 68);
        List<PathSegment> reversedSegments = new ArrayList<>();
        for (int i = forward.segments().size() - 1; i >= 0; i--) {
            PathSegment segment = forward.segments().get(i);
            reversedSegments.add(new PathSegment(segment.end, segment.start));
        }
        ScenarioFixture reversed = new ScenarioFixture(
            forward.network(),
            forward.road(),
            forward.edge(),
            forward.config(),
            reversedSegments,
            forward.terrain(),
            forward.preset(),
            forward.maxSlope());
        ProfileSolveResult reversedResult = solve(reversed, 68, 62);
        List<Double> forwardDesign = forwardResult.profileDesignElevations();
        List<Double> reversedDesign = new ArrayList<>(reversedResult.profileDesignElevations());
        Collections.reverse(reversedDesign);
        assertEquals(forwardDesign.size(), reversedDesign.size());
        for (int i = 0; i < forwardDesign.size(); i++) {
            assertEquals(forwardDesign.get(i), reversedDesign.get(i), 1e-6,
                "station " + i + " should match under edge reversal");
        }
    }

    private static void assertScenarioInvariants(
            ScenarioFixture fixture,
            ProfileSolveResult result,
            Integer manualStart,
            Integer manualEnd) {
        List<Double> segmentDistances = FitTerrainProfileInvariants.segmentDistancesFromCumulative(
            result.profileDistances());
        List<Float> slopes = constantSlopes(segmentDistances.size(), fixture.maxSlope());
        FitTerrainProfileInvariants.assertFullProfileInvariants(
            result,
            segmentDistances,
            slopes,
            fixture.preset(),
            manualStart,
            manualEnd,
            RoadConstructionHeuristics.cutToFillBalanceRatio(fixture.config()),
            com.plot.plugin.road.pipeline.profile.environment.WaterCrossingSettings.defaults().waterRoadClearanceBlocks());
    }

    private enum ScenarioId {
        A {
            @Override
            TerrainSampler terrain() {
                return flatTerrain(64);
            }
        },
        B {
            @Override
            TerrainSampler terrain() {
                return slopeTerrain(0.04);
            }
        },
        C {
            @Override
            TerrainSampler terrain() {
                return slopeTerrain(0.12);
            }
        },
        D {
            @Override
            TerrainSampler terrain() {
                return descendingTerrain(0.12);
            }
        },
        E {
            @Override
            TerrainSampler terrain() {
                return waveTerrain(4.0, 18.0, 66);
            }
        },
        F {
            @Override
            TerrainSampler terrain() {
                return waveTerrain(5.0, 24.0, 64);
            }
        },
        G {
            @Override
            TerrainSampler terrain() {
                return stepTerrain(75.0, 4);
            }
        },
        H {
            @Override
            TerrainSampler terrain() {
                return stepTerrain(75.0, 12);
            }
        },
        I {
            @Override
            TerrainSampler terrain() {
                return waveTerrain(3.0, 12.0, 65);
            }
        },
        J {
            @Override
            TerrainSampler terrain() {
                return riverTerrain(68, 55, 69, 35.0, 85.0);
            }
        },
        K {
            @Override
            TerrainSampler terrain() {
                return slopeTerrain(0.05);
            }

            @Override
            List<PathSegment> segments(double lengthMeters, double stepMeters) {
                return sampledSegments(lengthMeters, stepMeters / 2.0);
            }
        },
        L {
            @Override
            TerrainSampler terrain() {
                return slopeTerrain(0.08);
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
            config.setTerrainAdaptation(
                com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN);
            return new ScenarioFixture(
                network,
                road,
                edge,
                config,
                segments(lengthMeters, stepMeters),
                terrain(),
                TerrainFollowPreset.STANDARD,
                maxSlope);
        }

        List<PathSegment> segments(double lengthMeters, double stepMeters) {
            return sampledSegments(lengthMeters, stepMeters);
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

    private static TerrainSampler flatTerrain(int elevation) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return elevation;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static TerrainSampler slopeTerrain(double slopePerMeter) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64 + (int) Math.round(point.x * slopePerMeter);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static TerrainSampler descendingTerrain(double slopePerMeter) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 80 - (int) Math.round(point.x * slopePerMeter);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static TerrainSampler waveTerrain(double amplitude, double wavelength, int base) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return base + (int) Math.round(amplitude * Math.sin(point.x / wavelength));
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static TerrainSampler stepTerrain(double stepAtX, int stepHeight) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return point.x < stepAtX ? 60 : 60 + stepHeight;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };
    }

    private static TerrainSampler riverTerrain(
            int landY,
            int bedY,
            int waterY,
            double riverStartX,
            double riverEndX) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return inRiver(planPoint.x) ? bedY : landY;
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return inRiver(planPoint.x) ? OptionalInt.of(waterY) : OptionalInt.empty();
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= (inRiver(worldX) ? bedY : landY);
            }

            private boolean inRiver(double x) {
                return x >= riverStartX && x <= riverEndX;
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

    private static List<Float> constantSlopes(int count, float slope) {
        List<Float> slopes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slopes.add(slope);
        }
        return slopes;
    }
}
