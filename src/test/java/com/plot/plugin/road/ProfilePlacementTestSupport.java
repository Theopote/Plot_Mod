package com.plot.plugin.road;

import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 设计 Y → 方块 Y → 最终 ROAD 放置 Y 三层断言。 */
public final class ProfilePlacementTestSupport {

    private ProfilePlacementTestSupport() {
    }

    public static void assertDesignBlockPlacementChain(
            RoadVerticalAlignment design,
            VoxelProfileCompileResult compile,
            RoadBenchmarkHarness.PreviewRun preview,
            double designTolerance,
            int blockTolerance) {
        assertTrue(compile.feasible(), compile.failureReason());
        RoadGenerationResult edge = preview.edgeResults().values().iterator().next();
        int compared = 0;
        for (BuildHeightSample sample : compile.samples()) {
            if (sample.station() < 1.0) {
                continue;
            }
            double designY = VerticalAlignmentGeometry.elevationAt(design, sample.station())
                .orElse(Double.NaN);
            assertTrue(Double.isFinite(designY), "design elevation at station " + sample.station());
            assertEquals(designY, sample.designElevation(), designTolerance,
                "compile design elevation at station " + sample.station());
            assertEquals(sample.buildY(), sample.designElevation(), blockTolerance,
                "block Y vs design at station " + sample.station());

            Integer pavementY = pavementYNear(edge, (int) Math.round(sample.station()));
            if (pavementY == null) {
                continue;
            }
            assertEquals(sample.buildY(), pavementY.intValue(),
                "placement vs block at station " + sample.station());
            compared++;
        }
        assertTrue(compared >= 5, "expected multiple station comparisons");
    }

    /**
     * 同一建造方案：voxel compile buildY、采样 profileBuildSamples、最终 ROAD 放置在关键桩号上一致。
     */
    public static void assertBuildSchemeParityAtStations(
            VoxelProfileCompileResult compile,
            RoadGenerationResult samplingEdge,
            RoadGenerationResult placementEdge,
            double[] stations,
            int stationWindow) {
        assertTrue(compile.feasible(), compile.failureReason());
        for (double station : stations) {
            int compileY = compile.buildProfile().elevationAtWorldStation(station);
            int samplingY = nearestBuildSampleY(samplingEdge, station);
            Integer pavementY = pavementYNear(placementEdge, (int) Math.round(station), stationWindow);
            assertEquals(compileY, samplingY,
                "sampling vs compile at station " + station);
            if (pavementY != null) {
                assertEquals(compileY, pavementY.intValue(),
                    "placement vs compile at station " + station);
            }
        }
    }

    public static int nearestBuildSampleY(RoadGenerationResult edge, double station) {
        List<BuildHeightSample> samples = edge.profileBuildSamples;
        assertTrue(samples != null && !samples.isEmpty(), "missing profileBuildSamples");
        BuildHeightSample best = samples.getFirst();
        double bestDist = Math.abs(best.station() - station);
        for (BuildHeightSample candidate : samples) {
            double dist = Math.abs(candidate.station() - station);
            if (dist < bestDist) {
                best = candidate;
                bestDist = dist;
            }
        }
        return best.buildY();
    }

    public static OptionalInt buildYAtStation(VoxelProfileCompileResult compile, double station) {
        if (compile == null || compile.buildProfile() == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(compile.buildProfile().elevationAtWorldStation(station));
    }

    public static Integer pavementYNear(RoadGenerationResult edge, int x) {
        return pavementYNear(edge, x, 1);
    }

    public static Integer pavementYNear(RoadGenerationResult edge, int x, int window) {
        for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() >= x - window && pos.getX() <= x + window && Math.abs(pos.getZ()) <= 2) {
                return pos.getY();
            }
        }
        return null;
    }
}
