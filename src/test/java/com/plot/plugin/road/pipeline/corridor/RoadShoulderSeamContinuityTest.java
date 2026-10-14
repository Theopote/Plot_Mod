package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoadShoulderSeamContinuityTest {

    @Test
    void straightRoadPlanHasNoRoadShoulderSeamGaps() {
        assertZeroSeamGaps(buildPlan(straightSegment(new Vec2d(0, 0), new Vec2d(30, 0)), CorridorTestFixtures.baseConfig()));
    }

    @Test
    void diagonalRoadPlanHasNoRoadShoulderSeamGaps() {
        assertZeroSeamGaps(buildPlan(straightSegment(new Vec2d(0, 0), new Vec2d(30, 30)), CorridorTestFixtures.baseConfig()));
    }

    @Test
    void sCurveRoadPlanHasNoRoadShoulderSeamGaps() {
        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 6),
            new Vec2d(16, -2),
            new Vec2d(24, 8),
            new Vec2d(32, 2));
        assertZeroSeamGaps(buildPlan(path, CorridorTestFixtures.baseConfig()));
    }

    @Test
    void straightRoadPreviewDoesNotClearDesignSurfaceAtShoulder() {
        RoadNetwork network = straightNetwork("seam-straight", 64);
        RoadGenerationResult edge = preview(network, CorridorTestFixtures.baseConfig(), new FlatTerrainSampler(70));
        assertEquals(0, edge.roadShoulderSeamGapCount);
        assertFalse(hasAirAtDesignSurface(edge, 15, 0, RoadSolidLayer.ROAD));
        assertFalse(hasAirAtDesignSurface(edge, 15, 3, RoadSolidLayer.SHOULDER));
    }

    @Test
    void waterCrossingPreviewKeepsShoulderContinuous() {
        RoadGenerationResult edge = preview(
            RoadGoldenScenarioFactory.r10WaterCrossing().network(),
            RoadGoldenScenarioFactory.r10WaterCrossing().config(),
            RoadGoldenScenarioFactory.r10WaterCrossing().terrain());
        assertEquals(0, edge.roadShoulderSeamGapCount);
    }

    private static void assertZeroSeamGaps(RoadCorridorPlan plan) {
        int seamGaps = RoadGenerationIntegrityValidator.countRoadShoulderSeamGaps(plan);
        assertEquals(0, seamGaps, "ROAD/SHOULDER band must not contain lateral holes");
    }

    private static RoadCorridorPlan buildPlan(List<Vec2d> path, RoadSystemConfig config) {
        List<PathSegment> segments = new ArrayList<>();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathSegment segment = new PathSegment(path.get(i - 1), path.get(i));
            segments.add(segment);
            heightInfos.add(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));
        }
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        return RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            List.of(RoadConstructionType.ROAD),
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(64),
            1.0,
            "seam-test",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));
    }

    private static List<Vec2d> straightSegment(Vec2d from, Vec2d to) {
        return RoadVoxelRasterizerHelper.sample(from, to);
    }

    private static RoadNetwork straightNetwork(String id, int elevation) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setIncludeShoulder(true);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, elevation),
            PointOfVerticalIntersection.of(40, elevation))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return network;
    }

    private static RoadGenerationResult preview(
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain) {
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return RoadBenchmarkHarness.generatePreview(network, generator, terrain)
            .edgeResults().values().iterator().next();
    }

    private static boolean hasAirAtDesignSurface(
            RoadGenerationResult edge,
            int x,
            int z,
            RoadSolidLayer layer) {
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != x || pos.getZ() != z) {
                continue;
            }
            if (edge.placementLayers.get(pos) != layer) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                return true;
            }
            return false;
        }
        return false;
    }

    /** Test-local span sampler to avoid coupling to production rasterizer visibility. */
    private static final class RoadVoxelRasterizerHelper {
        private RoadVoxelRasterizerHelper() {
        }

        static List<Vec2d> sample(Vec2d from, Vec2d to) {
            return com.plot.plugin.road.solid.RoadVoxelRasterizer.sampleSpanPoints(from, to);
        }
    }
}
