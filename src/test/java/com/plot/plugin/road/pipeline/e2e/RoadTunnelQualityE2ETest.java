package com.plot.plugin.road.pipeline.e2e;

import com.plot.core.command.BlockRecord;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTunnelQualityE2ETest {

    private static final int ROAD_Y = 64;
    private static final int ENVELOPE_WIDTH = 5;

    @Test
    void r09TunnelProducesStructureWithoutOpenCorridorFill() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r09Tunnel());
        assertEquals(0, edge.structureInteriorSlopeCellCount);
        assertEquals(0, edge.fillVolume);
        assertTrue(edge.tunnelCount > 0 || edge.tunnelLength > 0 || !edge.tunnelBlocks.isEmpty(),
            "R09 must emit tunnel structure blocks");
    }

    @Test
    void r09TunnelHasNoOrphanSlopePillars() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r09Tunnel());
        assertEquals(0, edge.orphanSlopePillarCount);
        assertEquals(0, edge.structureInteriorSlopeCellCount);
    }

    @Test
    void r09TunnelBodyClearsNativeRockInsideCavity() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r09Tunnel();
        RoadGenerationResult edge = preview(scenario);
        assertEquals(0, edge.unclearedObstructionCount,
            "tunnel clearance pass must resolve mountain obstructions");

        int bodyAir = countAirPlacements(edge, 8, 22, -3, 3, 65, 69);
        assertTrue(bodyAir >= 250,
            "tunnel body must carve a continuous air cavity, airPlacements=" + bodyAir);

        int[] maskStats = countMaskCavityAirGaps(edge, scenario, 12, 18);
        assertTrue(maskStats[1] <= maskStats[0] / 2,
            "most mask-defined cavity cells should be cleared, expectedAir=" + maskStats[0]
                + " unclearedAir=" + maskStats[1]);
    }

    @Test
    void r09TunnelMidSectionHasContinuousLiningShell() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r09Tunnel());
        int liningAtMid = countLiningBlocks(edge, 15, -4, 4, 64, 72);
        assertTrue(liningAtMid >= 12,
            "tunnel mid-section must place lining blocks around the cavity, count=" + liningAtMid);
    }

    @Test
    void r09TunnelPortalsDoNotOverExcavateFarFromCenterline() {
        RoadGenerationResult edge = preview(RoadGoldenScenarioFactory.r09Tunnel());
        int lateralOverCut = countTunnelAirOutsideEnvelope(edge, 0, 2, 28, 30, 9, 72);
        assertTrue(lateralOverCut <= 12,
            "portal mouths must not carve wide air pockets far from the road centerline, count="
                + lateralOverCut);
    }

    private static int[] countMaskCavityAirGaps(
            RoadGenerationResult edge,
            RoadGoldenScenario scenario,
            int minX,
            int maxX) {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            style.shape(),
            ENVELOPE_WIDTH,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            ROAD_Y,
            false);
        int[] stats = {0, 0};
        for (int x = minX; x <= maxX; x++) {
            int finalX = x;
            mask.forEach((lateral, y, kind) -> {
                if (kind != TunnelCrossSectionMask.CellKind.AIR) {
                    return;
                }
                stats[0]++;
                BlockPos pos = new BlockPos(finalX, y, lateral);
                if (!scenario.terrain().isSolidBlock(finalX, y, lateral)) {
                    return;
                }
                BlockRecord record = edge.placementRecords.get(pos);
                if (record == null || !"minecraft:air".equals(record.newBlockId)) {
                    stats[1]++;
                }
            });
        }
        return stats;
    }

    private static int countAirPlacements(
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
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                continue;
            }
            if (pos.getY() < minY || pos.getY() > maxY) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                count++;
            }
        }
        return count;
    }

    private static int countLiningBlocks(
            RoadGenerationResult edge,
            int x,
            int minZ,
            int maxZ,
            int minY,
            int maxY) {
        int lining = 0;
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
                    lining++;
                }
            }
        }
        return lining;
    }

    private static int countTunnelAirOutsideEnvelope(
            RoadGenerationResult edge,
            int portalMinX,
            int portalMaxX,
            int bodyMinX,
            int bodyMaxX,
            int minAbsZ,
            int maxY) {
        int count = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            int x = pos.getX();
            boolean portalBand = x >= portalMinX && x <= portalMaxX || x >= bodyMinX && x <= bodyMaxX;
            if (!portalBand) {
                continue;
            }
            if (Math.abs(pos.getZ()) < minAbsZ) {
                continue;
            }
            if (pos.getY() > maxY) {
                continue;
            }
            if (edge.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                count++;
            }
        }
        return count;
    }

    private static RoadGenerationResult preview(RoadGoldenScenario scenario) {
        RoadGenerator generator = new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            scenario.network(), generator, scenario.terrain());
        return preview.edgeResults().values().iterator().next();
    }
}
