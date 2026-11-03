package com.plot.plugin.road.pipeline.e2e;

import com.plot.core.command.BlockRecord;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Station-by-station final-block seal on R09 (straight X corridor).
 * Uses the same world mapping as {@link RoadTunnelQualityE2ETest}: along = X, lateral = Z.
 */
class TunnelPortalTransitionSealE2ETest {

    private static final int ROAD_Y = 64;

    @Test
    void r09StationsKeepPavementCavityAndLiningSealed() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r09Tunnel();
        RoadGenerationResult edge = preview(scenario);
        ResolvedTunnelStyle style = ResolvedTunnelStyle.from(scenario.config().getTunnelStyle());
        int envelope = RoadCorridorWidth.gradingEnvelopeWidthBlocks(
            ResolvedCrossSection.fromConfig(scenario.config()));
        TunnelCrossSectionMask bodyMask = TunnelCrossSectionMask.build(
            style.shape(),
            envelope,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            ROAD_Y,
            false);
        TunnelCrossSectionMask portalMask = TunnelCrossSectionMask.build(
            style.shape(),
            envelope,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            ROAD_Y,
            true);

        int bodyChecked = 0;
        int portalChecked = 0;
        for (int x = 0; x <= 30; x++) {
            boolean portalBand = x <= 3 || x >= 27;
            boolean bodyBand = x >= 8 && x <= 22;
            if (!portalBand && !bodyBand) {
                continue;
            }
            TunnelCrossSectionMask mask = portalBand ? portalMask : bodyMask;
            StationSeal seal = inspectStation(edge, scenario, x, mask);
            assertTrue(seal.pavementOk(),
                "x=" + x + " must keep pavement under the road envelope");
            assertTrue(seal.unclearedAirCells() <= seal.expectedAirCells() / 2,
                "x=" + x + " cavity mostly cleared, uncleared="
                    + seal.unclearedAirCells() + "/" + seal.expectedAirCells());
            assertTrue(seal.missingLiningCells() <= seal.expectedLiningCells() / 2,
                "x=" + x + " lining mostly placed, missing="
                    + seal.missingLiningCells() + "/" + seal.expectedLiningCells());
            assertTrue(seal.shellAirLeaks() == 0,
                "x=" + x + " lining shell must not be carved to air, leaks=" + seal.shellAirLeaks());
            if (bodyBand) {
                bodyChecked++;
            } else {
                portalChecked++;
            }
        }
        assertTrue(bodyChecked >= 5, "body stations checked=" + bodyChecked);
        assertTrue(portalChecked >= 4, "portal stations checked=" + portalChecked);
    }

    private static StationSeal inspectStation(
            RoadGenerationResult edge,
            RoadGoldenScenario scenario,
            int alongX,
            TunnelCrossSectionMask mask) {
        boolean pavementOk = false;
        for (int lateral = mask.roadMin(); lateral <= mask.roadMax(); lateral++) {
            for (int y = ROAD_Y - 1; y <= ROAD_Y + 1; y++) {
                BlockPos pos = new BlockPos(alongX, y, lateral);
                RoadSolidLayer layer = edge.placementLayers.get(pos);
                if (layer == RoadSolidLayer.ROAD
                        || layer == RoadSolidLayer.SHOULDER
                        || layer == RoadSolidLayer.SIDEWALK) {
                    pavementOk = true;
                    break;
                }
            }
            if (pavementOk) {
                break;
            }
        }
        // Fallback: any ROAD cell on this station strip (generator may offset Y slightly).
        if (!pavementOk) {
            for (var entry : edge.placementLayers.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getX() != alongX) {
                    continue;
                }
                if (pos.getZ() < mask.roadMin() || pos.getZ() > mask.roadMax()) {
                    continue;
                }
                if (entry.getValue() == RoadSolidLayer.ROAD) {
                    pavementOk = true;
                    break;
                }
            }
        }

        int[] expectedAir = {0};
        int[] unclearedAir = {0};
        int[] expectedLining = {0};
        int[] missingLining = {0};
        int[] shellAirLeaks = {0};

        mask.forEach((lateral, y, kind) -> {
            if (y < ROAD_Y) {
                return;
            }
            BlockPos pos = new BlockPos(alongX, y, lateral);
            BlockRecord record = edge.placementRecords.get(pos);
            boolean airPlacement = record != null && "minecraft:air".equals(record.newBlockId);
            boolean nonAirTunnel = edge.placementLayers.get(pos) == RoadSolidLayer.TUNNEL
                && record != null
                && record.newBlockId != null
                && !"minecraft:air".equals(record.newBlockId);

            if (kind == TunnelCrossSectionMask.CellKind.AIR) {
                expectedAir[0]++;
                boolean nativeSolid = scenario.terrain().isSolidBlock(pos.getX(), pos.getY(), pos.getZ());
                if (nativeSolid && !airPlacement) {
                    unclearedAir[0]++;
                }
            } else if (kind == TunnelCrossSectionMask.CellKind.LINING) {
                expectedLining[0]++;
                boolean nearbyLining = hasNearbyNonAirTunnel(edge, pos);
                if (!nonAirTunnel && !nearbyLining) {
                    missingLining[0]++;
                }
                // True shell breach: mask lining cell became air and no lining sits nearby.
                if (airPlacement && !nearbyLining && !nonAirTunnel) {
                    shellAirLeaks[0]++;
                }
            }
        });

        return new StationSeal(
            pavementOk, expectedAir[0], unclearedAir[0], expectedLining[0], missingLining[0], shellAirLeaks[0]);
    }

    private record StationSeal(
            boolean pavementOk,
            int expectedAirCells,
            int unclearedAirCells,
            int expectedLiningCells,
            int missingLiningCells,
            int shellAirLeaks) {
    }

    private static boolean hasNearbyNonAirTunnel(RoadGenerationResult edge, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos near = pos.add(dx, dy, dz);
                    if (edge.placementLayers.get(near) != RoadSolidLayer.TUNNEL) {
                        continue;
                    }
                    BlockRecord record = edge.placementRecords.get(near);
                    if (record != null
                            && record.newBlockId != null
                            && !"minecraft:air".equals(record.newBlockId)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
}
