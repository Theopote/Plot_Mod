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
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies MANUAL_PROFILE PVI edits recompile BuildHeightProfile and change final pavement Y.
 * AUTO_SMOOTH does not use DesignElevationSource.forEdge — PVI edits are expected not to apply.
 */
class VerticalAlignmentBuildHeightIntegrationTest {

    @Test
    void manualProfilePviRaiseIncreasesMidspanPavementY() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("pvi-manual");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(15.0, 64.0),
            new PointOfVerticalIntersection(30.0, 64.0))));

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("pvi-build-height");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        // 15 m run to +5 blocks needs ~33% grade; keep under clamp but above default 10%.
        config.setMaxSlope(40.0f);
        road.setMaxSlope(40.0f);

        int baselineY = midspanRoadSurfaceY(network, config);
        assertTrue(baselineY > 0, "baseline generation must place midspan ROAD surface");

        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(15.0, 69.0),
            new PointOfVerticalIntersection(30.0, 64.0))));

        int raisedY = midspanRoadSurfaceY(network, config);
        int delta = raisedY - baselineY;
        assertTrue(delta >= 4 && delta <= 5,
            "raising interior PVI by 5 must raise midspan pavement Y by 4–5 blocks when MANUAL_PROFILE (got "
                + baselineY + " -> " + raisedY + ", delta=" + delta + ")");
    }

    @Test
    void autoSmoothIgnoresPviEditsForBuildHeight() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("pvi-auto");
        road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(15.0, 80.0),
            new PointOfVerticalIntersection(30.0, 64.0))));

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("pvi-auto-smooth");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);

        int withTallPvi = midspanRoadSurfaceY(network, config);

        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(15.0, 64.0),
            new PointOfVerticalIntersection(30.0, 64.0))));

        int withFlatPvi = midspanRoadSurfaceY(network, config);
        assertEquals(withTallPvi, withFlatPvi,
            "AUTO_SMOOTH must not apply DesignElevationSource / PVI to build height");
    }

    private static int midspanRoadSurfaceY(RoadNetwork network, RoadSystemConfig config) {
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            network,
            generator,
            new FlatTerrainSampler(64));
        assertFalse(preview.edgeResults().isEmpty(), "preview must produce edge results");
        RoadGenerationResult edge = preview.edgeResults().values().iterator().next();
        assertNotNull(edge);
        Integer yAtMid = null;
        for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            RoadSolidLayer layer = edge.placementLayers.get(pos);
            if (layer != RoadSolidLayer.ROAD) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() >= 14 && pos.getX() <= 16 && Math.abs(pos.getZ()) <= 2) {
                yAtMid = pos.getY();
                break;
            }
        }
        assertNotNull(yAtMid, "expected ROAD placement near world X=15");
        return yAtMid;
    }
}
