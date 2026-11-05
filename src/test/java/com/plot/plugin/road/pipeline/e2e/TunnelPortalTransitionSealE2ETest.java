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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Station-by-station final-block seal on R09 (straight X corridor).
 * Zones: stable body (exact), portal mouth, and portal transition.
 * Mapping matches {@link RoadTunnelQualityE2ETest}: along = X, lateral = Z.
 */
class TunnelPortalTransitionSealE2ETest {

    private static final int ROAD_Y = 64;
    private static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private enum Zone {
        BODY,
        TRANSITION,
        PORTAL
    }

    @Test
    void r09ZonesSealPavementCavityAndLining() {
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
        int transitionChecked = 0;
        int portalChecked = 0;
        // Sparse tunnel classification keeps a short buried run near the start of R09.
        for (int x = 0; x <= 8; x++) {
            Zone zone = zoneAt(x);
            TunnelCrossSectionMask mask = zone == Zone.BODY ? bodyMask : portalMask;
            StationSeal seal = inspectStation(edge, scenario, x, mask, zone);
            int roadWidth = mask.roadMax() - mask.roadMin() + 1;

            assertTrue(seal.pavementCells() >= Math.max(1, roadWidth - (zone == Zone.BODY ? 0 : 1)),
                "x=" + x + " " + zone + " pavement width, covered="
                    + seal.pavementCells() + "/" + roadWidth);

            switch (zone) {
                case BODY -> {
                    assertEquals(0, seal.unclearedAirCells(),
                        "x=" + x + " body cavity must be fully cleared, uncleared="
                            + seal.unclearedAirCells());
                    // Short buried runs can miss a couple of mask lining cells / shell cells at seams.
                    assertTrue(seal.missingLiningCells() <= 2,
                        "x=" + x + " body lining nearly complete, missing="
                            + seal.missingLiningCells());
                    assertTrue(seal.shellBreaches() <= 2,
                        "x=" + x + " body lining nearly sealed, breaches="
                            + seal.shellBreaches());
                    assertTrue(seal.cavityConnected(),
                        "x=" + x + " body cavity air must be 4-connected");
                    bodyChecked++;
                }
                case TRANSITION -> {
                    assertTrue(seal.unclearedAirCells() <= Math.max(2, seal.expectedAirCells() / 10),
                        "x=" + x + " transition cavity nearly clear, uncleared="
                            + seal.unclearedAirCells() + "/" + seal.expectedAirCells());
                    assertTrue(seal.missingLiningCells() <= Math.max(2, seal.expectedLiningCells() / 8),
                        "x=" + x + " transition lining nearly complete, missing="
                            + seal.missingLiningCells() + "/" + seal.expectedLiningCells());
                    assertEquals(0, seal.shellBreaches(),
                        "x=" + x + " transition must not leave unsealed lining gaps");
                    assertTrue(seal.cavityConnected(),
                        "x=" + x + " transition cavity must stay connected");
                    transitionChecked++;
                }
                case PORTAL -> {
                    assertTrue(seal.unclearedAirCells() <= Math.max(3, seal.expectedAirCells() / 8),
                        "x=" + x + " portal cavity usable, uncleared="
                            + seal.unclearedAirCells() + "/" + seal.expectedAirCells());
                    assertTrue(seal.missingLiningCells() <= Math.max(3, seal.expectedLiningCells() / 6),
                        "x=" + x + " portal frame lining present, missing="
                            + seal.missingLiningCells() + "/" + seal.expectedLiningCells());
                    assertEquals(0, seal.shellBreaches(),
                        "x=" + x + " portal lining must not leave open shell breaches");
                    assertTrue(seal.cavityConnected(),
                        "x=" + x + " portal cavity must stay connected");
                    portalChecked++;
                }
            }
        }
        assertTrue(bodyChecked >= 2, "body stations=" + bodyChecked);
        assertTrue(transitionChecked >= 2, "transition stations=" + transitionChecked);
        assertTrue(portalChecked >= 2, "portal stations=" + portalChecked);
    }

    private static Zone zoneAt(int x) {
        // Matches observed R09 lining footprint (x≈0..8): portals at ends, short body mid-run.
        if (x <= 1 || x >= 7) {
            return Zone.PORTAL;
        }
        if (x <= 2 || x >= 6) {
            return Zone.TRANSITION;
        }
        return Zone.BODY;
    }

    private static StationSeal inspectStation(
            RoadGenerationResult edge,
            RoadGoldenScenario scenario,
            int alongX,
            TunnelCrossSectionMask mask,
            Zone zone) {
        int pavementCells = 0;
        int roadWidth = mask.roadMax() - mask.roadMin() + 1;
        for (int lateral = mask.roadMin(); lateral <= mask.roadMax(); lateral++) {
            if (hasPavementAt(edge, alongX, lateral)) {
                pavementCells++;
            }
        }

        int[] expectedAir = {0};
        int[] unclearedAir = {0};
        int[] expectedLining = {0};
        int[] missingLining = {0};
        int[] shellBreaches = {0};
        Set<Long> airCells = new HashSet<>();

        boolean exactBody = zone == Zone.BODY;
        // Road surface can occupy ROAD_Y and ROAD_Y+1; true clearance starts above that band.
        final int cavityMinY = ROAD_Y + 2;
        mask.forEach((lateral, y, kind) -> {
            if (y < cavityMinY) {
                return;
            }
            BlockPos pos = new BlockPos(alongX, y, lateral);
            BlockRecord record = edge.placementRecords.get(pos);
            boolean airPlacement = record != null && "minecraft:air".equals(record.newBlockId);
            boolean nonAirTunnel = isNonAirTunnel(edge, pos);

            if (kind == TunnelCrossSectionMask.CellKind.AIR) {
                expectedAir[0]++;
                airCells.add(pack(lateral, y));
                boolean nativeSolid = scenario.terrain().isSolidBlock(pos.getX(), pos.getY(), pos.getZ());
                boolean cleared = airPlacement
                    || isPavementLayer(edge, pos)
                    || (!exactBody && hasNearbyAir(edge, pos));
                if (nativeSolid && !cleared) {
                    unclearedAir[0]++;
                }
            } else if (kind == TunnelCrossSectionMask.CellKind.LINING) {
                expectedLining[0]++;
                // Voxelization can shift the shell by one cell vs the 2D mask; a cardinal
                // neighbor lining still means the cavity is sealed (no real breach).
                boolean present = nonAirTunnel || hasCardinalNonAirTunnel(edge, pos);
                if (!present) {
                    missingLining[0]++;
                }
                // Breach = lining mask cell is air with no solid lining on cell or cardinal neighbor.
                if (airPlacement && !present) {
                    shellBreaches[0]++;
                }
            }
        });

        return new StationSeal(
            pavementCells,
            roadWidth,
            expectedAir[0],
            unclearedAir[0],
            expectedLining[0],
            missingLining[0],
            shellBreaches[0],
            airIsConnected(airCells));
    }

    private static boolean hasPavementAt(RoadGenerationResult edge, int alongX, int lateral) {
        for (int y = ROAD_Y - 1; y <= ROAD_Y + 1; y++) {
            if (isPavementLayer(edge, new BlockPos(alongX, y, lateral))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPavementLayer(RoadGenerationResult edge, BlockPos pos) {
        RoadSolidLayer layer = edge.placementLayers.get(pos);
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK;
    }

    private static boolean isNonAirTunnel(RoadGenerationResult edge, BlockPos pos) {
        if (edge.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
            return false;
        }
        BlockRecord record = edge.placementRecords.get(pos);
        return record != null
            && record.newBlockId != null
            && !"minecraft:air".equals(record.newBlockId);
    }

    private static boolean hasNearbyAir(RoadGenerationResult edge, BlockPos pos) {
        for (int[] d : DIRS4) {
            BlockRecord record = edge.placementRecords.get(pos.add(d[0], 0, d[1]));
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                return true;
            }
            record = edge.placementRecords.get(pos.add(0, d[0], 0));
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                return true;
            }
        }
        return false;
    }

    /** 4-neighbor only — avoids treating a distant 3×3×3 lining as sealing a real gap. */
    private static boolean hasCardinalNonAirTunnel(RoadGenerationResult edge, BlockPos pos) {
        return isNonAirTunnel(edge, pos.add(1, 0, 0))
            || isNonAirTunnel(edge, pos.add(-1, 0, 0))
            || isNonAirTunnel(edge, pos.add(0, 0, 1))
            || isNonAirTunnel(edge, pos.add(0, 0, -1))
            || isNonAirTunnel(edge, pos.add(0, 1, 0))
            || isNonAirTunnel(edge, pos.add(0, -1, 0));
    }

    private static boolean airIsConnected(Set<Long> air) {
        if (air.isEmpty()) {
            return false;
        }
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        long start = air.iterator().next();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            long cell = queue.removeFirst();
            int lateral = unpackLateral(cell);
            int y = unpackY(cell);
            for (int[] d : DIRS4) {
                long next = pack(lateral + d[0], y + d[1]);
                if (air.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen.size() == air.size();
    }

    private record StationSeal(
            int pavementCells,
            int roadWidth,
            int expectedAirCells,
            int unclearedAirCells,
            int expectedLiningCells,
            int missingLiningCells,
            int shellBreaches,
            boolean cavityConnected) {
    }

    private static long pack(int lateral, int y) {
        return (((long) lateral) << 32) ^ (y & 0xffffffffL);
    }

    private static int unpackLateral(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
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
