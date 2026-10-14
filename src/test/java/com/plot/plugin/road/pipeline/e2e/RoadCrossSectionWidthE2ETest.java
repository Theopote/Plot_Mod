package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionWidthE2ETest {

    static Stream<RoadStyle> builtinStyles() {
        return RoadStyleCatalog.defaultStyles().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builtinStyles")
    void builtinPresetHardSurfaceWidthMatchesResolvedHalfWidth(RoadStyle style) {
        assertHardWidthWithinOneBlock(style);
    }

    @Test
    void highwayMedianColumnHasNoMarkingOverlay() {
        RoadStyle style = RoadStyleCatalog.highway();
        Preview preview = previewWithStyle(style);
        ResolvedCrossSection resolved = preview.resolved();

        int medianColumns = 0;
        int markingOnMedian = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : preview.edge().placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX() - 20) > 8) {
                continue;
            }
            if (entry.getValue() == RoadSolidLayer.MEDIAN && resolved.occupiesMedianColumn(pos.getZ())) {
                medianColumns++;
            }
            if (entry.getValue() == RoadSolidLayer.MARKING && resolved.occupiesMedianColumn(pos.getZ())) {
                markingOnMedian++;
            }
        }
        assertTrue(medianColumns > 0, "highway must place median columns");
        assertEquals(0, markingOnMedian, "markings must not occupy median columns");
    }

    @Test
    void avenueMedianColumnHasNoMarkingOverlay() {
        RoadStyle style = RoadStyleCatalog.avenue();
        Preview preview = previewWithStyle(style);
        ResolvedCrossSection resolved = preview.resolved();

        int markingOnMedian = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : preview.edge().placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX() - 20) > 8) {
                continue;
            }
            if (entry.getValue() == RoadSolidLayer.MARKING && resolved.occupiesMedianColumn(pos.getZ())) {
                markingOnMedian++;
            }
        }
        assertEquals(0, markingOnMedian, "avenue markings must not occupy median columns");
    }

    private static void assertHardWidthWithinOneBlock(RoadStyle style) {
        Preview preview = previewWithStyle(style);
        ResolvedCrossSection resolved = preview.resolved();
        int expectedHalf = (int) Math.ceil(resolved.totalHardSurfaceWidthBlocks() / 2.0);

        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : preview.edge().placementLayers.entrySet()) {
            RoadSolidLayer layer = entry.getValue();
            if (layer != RoadSolidLayer.ROAD
                    && layer != RoadSolidLayer.SHOULDER
                    && layer != RoadSolidLayer.SIDEWALK
                    && layer != RoadSolidLayer.BIKE_LANE
                    && layer != RoadSolidLayer.MEDIAN) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX() - 20) > 5) {
                continue;
            }
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        assertTrue(minZ <= maxZ, style.id + " must place hard-surface blocks");
        int measuredHalf = Math.max(Math.abs(minZ), Math.abs(maxZ));
        assertTrue(
            Math.abs(measuredHalf - expectedHalf) <= 1,
            style.id + " half-width measured=" + measuredHalf + " expected~" + expectedHalf
                + " totalHard=" + resolved.totalHardSurfaceWidthBlocks());
    }

    private static Preview previewWithStyle(RoadStyle style) {
        RoadSystemConfig config = new RoadSystemConfig("width-e2e");
        config.setIncludeSlopeBatter(false);
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(style.id);
        style.applyTo(road);
        road.setIncludeSlopeBatter(false);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun run = RoadBenchmarkHarness.generatePreview(
            network, generator, new FlatTerrainSampler(64));
        assertEquals(1, run.edgeResults().size());
        RoadGenerationResult edge = run.edgeResults().values().iterator().next();
        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(road.getCrossSection(), config);
        return new Preview(edge, resolved);
    }

    private record Preview(RoadGenerationResult edge, ResolvedCrossSection resolved) {
    }
}
