package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelProfileHardeningTest {

    @Test
    void fixedIntersectionSurvivesVoxelSmoothing() {
        List<BuildHeightSample> samples = samples(
            new int[] {64, 64, 65, 65, 64, 64, 64},
            64.0);
        // Lock the crest samples that sit on a junction so they cannot be flattened.
        VoxelProfileConstraints constraints = VoxelProfileConstraints.builder()
            .lockStation(2.0, 65)
            .lockStation(3.0, 65)
            .build();
        BitSet locked = VoxelProfileConstraintResolver.toLockedSampleIndices(samples, constraints);
        BuildHeightSequenceSmoother.SmoothingResult smoothed =
            BuildHeightSequenceSmoother.smooth(samples, ProfileCompileOptions.defaults(), locked);
        List<BuildHeightSample> enforced = VoxelProfileConstraintResolver.enforce(
            smoothed.samples(), constraints);
        assertEquals(65, enforced.get(2).buildY());
        assertEquals(65, enforced.get(3).buildY());
    }

    @Test
    void manualCrestSurvivesSmoothing() {
        List<BuildHeightSample> samples = new ArrayList<>();
        double[] design = {64, 64.5, 66.0, 66.0, 64.5, 64, 64};
        int[] build = {64, 65, 66, 66, 65, 64, 64};
        for (int i = 0; i < build.length; i++) {
            samples.add(new BuildHeightSample(i, design[i], build[i]));
        }
        // USER_LOCKED mid PVI freezes the crest sample.
        samples = List.copyOf(samples);
        VoxelProfileConstraints constraints = VoxelProfileConstraints.builder()
            .lockStation(2.0, 66)
            .lockStation(3.0, 66)
            .build();
        BitSet locked = VoxelProfileConstraintResolver.toLockedSampleIndices(samples, constraints);
        List<BuildHeightSample> result = VoxelProfileConstraintResolver.enforce(
            BuildHeightSequenceSmoother.smooth(samples, ProfileCompileOptions.defaults(), locked).samples(),
            constraints);
        assertEquals(66, result.get(2).buildY());
        assertEquals(66, result.get(3).buildY());
    }

    @Test
    void lockedPviConstraintIsResolvedFromAlignment() {
        List<BuildHeightSample> samples = samples(
            new int[] {64, 64, 65, 64, 64},
            64.0);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(
                2.0, 65.0, null, VerticalControlPointConstraint.USER_LOCKED),
            new PointOfVerticalIntersection(4.0, 64.0)));
        VoxelProfileConstraints constraints = VoxelProfileConstraintResolver.resolve(
            null, null, alignment, samples, List.of(), null);
        BitSet locked = VoxelProfileConstraintResolver.toLockedSampleIndices(samples, constraints);
        assertTrue(locked.get(2));
        List<BuildHeightSample> result = VoxelProfileConstraintResolver.enforce(
            BuildHeightSequenceSmoother.smooth(samples, ProfileCompileOptions.defaults(), locked).samples(),
            constraints);
        assertEquals(65, result.get(2).buildY());
    }

    @Test
    void previewKeyChangesOnGeometryAndTerrain() {
        RoadNetwork network = shortRoad("geom");
        Road road = network.getRoad("geom");
        ProfileCompileInputsFingerprint a = ProfileCompileInputsFingerprint.of(
            network, road, road.getVerticalAlignment(), List.of(),
            VoxelProfileConstraints.empty(), null, 1L, new RoadSystemConfig("a"),
            ProfileCompileOptions.defaults());
        ProfileCompileInputsFingerprint b = ProfileCompileInputsFingerprint.of(
            network, road, road.getVerticalAlignment(), List.of(),
            VoxelProfileConstraints.empty(), null, 2L, new RoadSystemConfig("a"),
            ProfileCompileOptions.defaults());
        assertNotEquals(a, b, "terrain revision must change fingerprint");

        RoadProfileIntersection ix = new RoadProfileIntersection(
            "n", "geom", "other", "other", 10, 10, 65, 70, null, true, true, 5, false);
        ProfileCompileInputsFingerprint c = ProfileCompileInputsFingerprint.of(
            network, road, road.getVerticalAlignment(), List.of(ix),
            VoxelProfileConstraints.empty(), null, 1L, new RoadSystemConfig("a"),
            ProfileCompileOptions.defaults());
        assertNotEquals(a, c, "intersection constraints must change fingerprint");
    }

    @Test
    void previewDoesNotCommitActiveDrag() {
        RoadNetwork network = shortRoad("draft");
        Road road = network.getRoad("draft");
        RoadVerticalAlignment original = road.getVerticalAlignment().copy();
        RoadNetwork snapshot = network.snapshot();
        Road snapRoad = snapshot.getRoad("draft");
        snapRoad.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(15.0, 70.0),
            new PointOfVerticalIntersection(30.0, 64.0))));

        // Working copy on original network remains unchanged.
        assertEquals(original.getPvis().get(1).getElevation(),
            network.getRoad("draft").getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(70.0, snapRoad.getVerticalAlignment().getPvis().get(1).getElevation());
    }

    @Test
    void multiEdgePreviewMatchesEveryRoadSurfaceY() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("multi");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(20.0, 64.0),
            new PointOfVerticalIntersection(40.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode mid = network.createNode(new Vec2d(20, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), mid.getId(), List.of(a.getPosition(), mid.getPosition()), road.getId());
        network.createEdge(mid.getId(), b.getId(), List.of(mid.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = config();
        RoadGenerator generator = generator(config);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        VoxelProfileCompileResult preview = RoadVoxelProfileCompiler.compile(
            network, road, config, generator, terrain, Map.of(), ProfileCompileOptions.defaults());
        assertTrue(preview.feasible());
        assertTrue(preview.hasSamples());

        RoadBenchmarkHarness.PreviewRun generated = RoadBenchmarkHarness.generatePreview(
            network, generator, terrain);
        assertFalse(generated.edgeResults().isEmpty());

        for (RoadGenerationResult edge : generated.edgeResults().values()) {
            assertTrue(edge.buildProfile != null && edge.buildProfile.isActive());
        }

        // Check multiple pavement X positions along both edges.
        int matches = 0;
        for (int x = 2; x <= 38; x += 4) {
            Integer pavementY = findRoadYNear(generated, x);
            if (pavementY == null) {
                continue;
            }
            int previewY = preview.buildProfile().elevationAtWorldStation(x);
            assertEquals(previewY, pavementY.intValue(), "surface Y mismatch at x=" + x);
            matches++;
        }
        assertTrue(matches >= 5, "expected multiple pavement samples across multi-edge road");
    }

    @Test
    void longRoadSmoothingRemainsStable() {
        int length = 200;
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("long");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(20.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(length / 2.0, 66.0),
            new PointOfVerticalIntersection((double) length, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(length, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        VoxelProfileCompileResult result = RoadVoxelProfileCompiler.compile(
            network, road, config(), generator(config()), new FlatTerrainSampler(64),
            Map.of(), ProfileCompileOptions.defaults());
        assertTrue(result.feasible());
        assertTrue(result.samples().size() > 100);
        int[] build = result.samples().stream().mapToInt(BuildHeightSample::buildY).toArray();
        assertTrue(BuildHeightSequenceSmoother.countShortReversals(build) <= 2);
    }

    private static Integer findRoadYNear(RoadBenchmarkHarness.PreviewRun generated, int x) {
        for (RoadGenerationResult edge : generated.edgeResults().values()) {
            for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                    continue;
                }
                if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                    continue;
                }
                if (pos.getX() >= x - 1 && pos.getX() <= x + 1 && Math.abs(pos.getZ()) <= 2) {
                    return pos.getY();
                }
            }
        }
        return null;
    }

    private static RoadNetwork shortRoad(String id) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(30.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), id);
        return network;
    }

    private static RoadSystemConfig config() {
        RoadSystemConfig config = new RoadSystemConfig("voxel-hardening");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);
        return config;
    }

    private static RoadGenerator generator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }

    private static List<BuildHeightSample> samples(int[] buildY, double designY) {
        List<BuildHeightSample> samples = new ArrayList<>(buildY.length);
        for (int i = 0; i < buildY.length; i++) {
            samples.add(new BuildHeightSample(i, designY, buildY[i]));
        }
        return samples;
    }
}
