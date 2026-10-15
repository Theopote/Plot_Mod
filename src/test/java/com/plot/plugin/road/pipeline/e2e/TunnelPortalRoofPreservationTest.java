package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelPortalRoofPreservationTest {

    private static final int ROAD_Y = 64;
    private static final int TERRAIN_TOP = 72;

    @Test
    void r09PortalRoofDoesNotClearSolidMountainAboveCavity() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r09Tunnel();
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofTop = portalRoofTop(scenario, style);
        int terrainTop = 80;

        List<BlockPos> preserveSamples = new ArrayList<>();
        for (int x = 0; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (Math.abs(z) < 5) {
                    continue;
                }
                for (int y = roofTop + 1; y <= terrainTop; y++) {
                    if (scenario.terrain().isSolidBlock(x, y, z)) {
                        preserveSamples.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        assertTrue(preserveSamples.size() > 10, "fixture must include solid roof samples");

        RoadGenerationResult edge = preview(scenario);

        int unexpectedAir = 0;
        for (BlockPos pos : preserveSamples) {
            if (isAirPlacement(edge, pos)) {
                unexpectedAir++;
            }
        }
        assertEquals(0, unexpectedAir,
            "blocks above portal roof must not become air, violations=" + unexpectedAir);

        int gapLayers = 0;
        for (int x = 0; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(z) < 3) {
                    continue;
                }
                for (int y = roofTop + 1; y <= 72; y++) {
                    if (!scenario.terrain().isSolidBlock(x, y, z)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockRecord record = edge.placementRecords.get(pos);
                    if (record != null && "minecraft:air".equals(record.newBlockId)) {
                        gapLayers++;
                    }
                }
            }
        }
        assertEquals(0, gapLayers,
            "roof shell must have no air layers up to original terrain, gaps=" + gapLayers);
    }

    @Test
    void r09PortalCrownHasNoAirGapAboveRoofShell() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r09Tunnel();
        int roofTop = portalRoofTop(scenario, ResolvedTunnelStyle.defaults());
        RoadGenerationResult edge = preview(scenario);

        int airAboveRoof = 0;
        for (int x = 0; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = roofTop + 1; y <= roofTop + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isAirPlacement(edge, pos)) {
                        airAboveRoof++;
                    }
                }
            }
        }
        assertEquals(0, airAboveRoof,
            "portal crown must not leave air above roof shell, gaps=" + airAboveRoof);
    }

    @Test
    void r09PortalVerticalExcavationStaysBelowRoof() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r09Tunnel();
        int roofTop = portalRoofTop(scenario, ResolvedTunnelStyle.defaults());
        RoadGenerationResult edge = preview(scenario);
        int airAboveRoofOutsideEnvelope = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() <= roofTop) {
                continue;
            }
            if (pos.getX() < 0 || pos.getX() > 8) {
                continue;
            }
            if (Math.abs(pos.getZ()) < 5) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                airAboveRoofOutsideEnvelope++;
            }
        }
        assertTrue(airAboveRoofOutsideEnvelope <= 4,
            "portal must not carve air above roof outside corridor envelope, count="
                + airAboveRoofOutsideEnvelope);
    }

    @Test
    void horizontalPortalRoofNotClearedAboveShell() {
        assertOrientedPortalRoofPreserved(
            tunnelScenario(List.of(new Vec2d(0, 0), new Vec2d(30, 0))),
            new Vec2d(1, 0));
    }

    @Test
    void verticalPortalRoofNotClearedAboveShell() {
        assertOrientedPortalRoofPreserved(
            tunnelScenario(List.of(new Vec2d(0, 0), new Vec2d(0, 30))),
            new Vec2d(0, 1));
    }

    @Test
    void diagonalPortalRoofNotClearedAboveShell() {
        assertOrientedPortalRoofPreserved(
            tunnelScenario(List.of(new Vec2d(0, 0), new Vec2d(24, 24))),
            new Vec2d(1, 1).normalize());
    }

    @Test
    void sCurvePortalRoofNotClearedAboveShell() {
        // Sample near the first portal station along local tangent.
        assertOrientedPortalRoofPreserved(
            tunnelScenario(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 4),
                new Vec2d(20, -4),
                new Vec2d(30, 0))),
            new Vec2d(10, 4).normalize());
    }

    @Test
    void widerCorridorPortalRoofNotClearedAboveShell() {
        RoadSystemConfig config = baseTunnelConfig();
        config.setLaneCount(4);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(2);
        assertOrientedPortalRoofPreserved(
            tunnelScenario(List.of(new Vec2d(0, 0), new Vec2d(30, 0)), config),
            new Vec2d(1, 0));
    }

    /**
     * Samples solid cells on the portal roof-shell fringe (outside cavity, inside outer lining),
     * oriented to the local left-normal of the approach tangent.
     */
    private static void assertOrientedPortalRoofPreserved(RoadGoldenScenario scenario, Vec2d tangent) {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofTop = portalRoofTop(scenario, style);
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(
            ResolvedCrossSection.fromConfig(scenario.config()));
        int cavityHalf = envelopeWidth / 2 + style.sideClearance();
        int outerHalf = cavityHalf + Math.min(
            com.plot.plugin.road.tunnel.TunnelStyle.MAX_LINING_THICKNESS,
            style.liningThickness() + 1);
        Vec2d forward = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : new Vec2d(1, 0);
        Vec2d leftNormal = forward.perpendicular().normalize();

        List<BlockPos> preserveSamples = new ArrayList<>();
        for (int along = 0; along <= 6; along++) {
            Vec2d center = forward.multiply(along);
            for (int lateral = -outerHalf; lateral <= outerHalf; lateral++) {
                int absLateral = Math.abs(lateral);
                if (absLateral < cavityHalf || absLateral > outerHalf) {
                    continue;
                }
                Vec2d plan = center.add(leftNormal.multiply(lateral));
                int wx = (int) Math.round(plan.x);
                int wz = (int) Math.round(plan.y);
                for (int y = roofTop + 1; y <= Math.min(TERRAIN_TOP, roofTop + 3); y++) {
                    if (scenario.terrain().isSolidBlock(wx, y, wz)) {
                        preserveSamples.add(new BlockPos(wx, y, wz));
                    }
                }
            }
        }
        assertTrue(preserveSamples.size() > 5, "fixture must include oriented roof preserve samples");

        RoadGenerationResult edge = preview(scenario);
        int unexpectedAir = 0;
        for (BlockPos pos : preserveSamples) {
            if (isAirPlacement(edge, pos)) {
                unexpectedAir++;
            }
        }
        assertEquals(0, unexpectedAir,
            "oriented portal roof preserve cells must not become air, gaps=" + unexpectedAir);
    }

    private static RoadGoldenScenario tunnelScenario(List<Vec2d> centerline) {
        return tunnelScenario(centerline, baseTunnelConfig());
    }

    private static RoadGoldenScenario tunnelScenario(List<Vec2d> centerline, RoadSystemConfig config) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("portal-orient");
        RoadNode a = network.createNode(centerline.getFirst());
        RoadNode b = network.createNode(centerline.getLast());
        a.setManualElevation((double) ROAD_Y);
        b.setManualElevation((double) ROAD_Y);
        network.createEdge(a.getId(), b.getId(), centerline, road.getId());
        TerrainSampler mountain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return TERRAIN_TOP;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= TERRAIN_TOP;
            }
        };
        return new RoadGoldenScenario("PORTAL-ORIENT", "oriented portal", network, mountain, config);
    }

    private static RoadSystemConfig baseTunnelConfig() {
        RoadSystemConfig config = new RoadSystemConfig("portal-orient");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setRoadWidth(5);
        config.setMaxSlope(12.0f);
        return config;
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

    private static int portalRoofTop(RoadGoldenScenario scenario, ResolvedTunnelStyle style) {
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(scenario.config());
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        return TunnelPortalExcavationMask.forPortal(style, envelopeWidth, ROAD_Y).roofTopY();
    }

    private static boolean isAirPlacement(RoadGenerationResult edge, BlockPos pos) {
        BlockRecord record = edge.placementRecords.get(pos);
        return record != null && "minecraft:air".equals(record.newBlockId);
    }
}
