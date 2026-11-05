package com.plot.plugin.road.pipeline.e2e;

import com.plot.core.command.BlockRecord;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final-block smoke for all seven tunnel shapes on the R09 mountain corridor.
 */
class TunnelSevenShapeBuildE2ETest {

    @Test
    void everyShapeKeepsClearanceLiningAndRoadContinuity() {
        for (TunnelShape shape : TunnelShape.values()) {
            RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r09Tunnel();
            scenario.config().getTunnelStyle().setShape(shape);
            // Circular: keep clear height within diameter so geometry stays a true circle.
            if (shape == TunnelShape.CIRCULAR) {
                scenario.config().getTunnelStyle().setClearHeight(5);
            }

            RoadGenerationResult edge = preview(scenario);
            assertEquals(0, edge.unclearedObstructionCount,
                shape + " uncleared obstructions");
            assertEquals(0, edge.unsupportedPavementCount,
                shape + " unsupported pavement");
            assertTrue(edge.tunnelCount > 0 || edge.tunnelLength > 0 || !edge.tunnelBlocks.isEmpty(),
                shape + " must emit tunnel structure");

            // Sparse classification shortens the buried run; sample the actual tunnel body near x=0..8.
            int roadCells = countLayer(edge, RoadSolidLayer.ROAD, 0, 8, -2, 2);
            assertTrue(roadCells >= 10, shape + " road continuity, count=" + roadCells);

            int cavityAir = countAir(edge, 2, 6, -2, 2, 66, 69);
            assertTrue(cavityAir >= 10, shape + " cavity air, count=" + cavityAir);

            int lining = countNonAirTunnel(edge, 4, -4, 4, 64, 72);
            assertTrue(lining >= 8, shape + " lining shell, count=" + lining);
        }
    }

    private static RoadGenerationResult preview(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return RoadBenchmarkHarness.generatePreview(
            scenario.network(), generator, scenario.terrain())
            .edgeResults().values().iterator().next();
    }

    private static int countLayer(
            RoadGenerationResult edge,
            RoadSolidLayer layer,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        int count = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            if (entry.getValue() != layer) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                count++;
            }
        }
        return count;
    }

    private static int countAir(
            RoadGenerationResult edge,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minY,
            int maxY) {
        int count = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX
                    || pos.getZ() < minZ || pos.getZ() > maxZ
                    || pos.getY() < minY || pos.getY() > maxY) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                count++;
            }
        }
        return count;
    }

    private static int countNonAirTunnel(
            RoadGenerationResult edge,
            int x,
            int minZ,
            int maxZ,
            int minY,
            int maxY) {
        int count = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (edge.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
                    continue;
                }
                BlockRecord record = edge.placementRecords.get(pos);
                if (record != null
                        && record.newBlockId != null
                        && !"minecraft:air".equals(record.newBlockId)) {
                    count++;
                }
            }
        }
        return count;
    }
}
