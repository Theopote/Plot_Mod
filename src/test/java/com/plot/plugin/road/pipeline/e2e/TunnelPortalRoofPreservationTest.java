package com.plot.plugin.road.pipeline.e2e;

import com.plot.core.command.BlockRecord;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
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

        int crownY = roofTop + 1;
        int missingCrownSeal = 0;
        for (int x = 0; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(z) < 3) {
                    continue;
                }
                BlockPos crown = new BlockPos(x, crownY, z);
                if (!scenario.terrain().isSolidBlock(x, crownY, z)) {
                    continue;
                }
                BlockRecord record = edge.placementRecords.get(crown);
                if (record == null || "minecraft:air".equals(record.newBlockId)) {
                    missingCrownSeal++;
                }
            }
        }
        assertEquals(0, missingCrownSeal,
            "portal crown must seal lining to mountain, gaps=" + missingCrownSeal);
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
