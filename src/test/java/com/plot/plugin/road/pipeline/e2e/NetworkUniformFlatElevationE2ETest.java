package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路网级 UNIFORM_FLAT：多路生成同一 Y、关闭后恢复、新增路继承、路口覆盖仅快照、改 Y。
 */
class NetworkUniformFlatElevationE2ETest {

    @Test
    void threeRoadsUniformFlatAggregateRoadAt72() {
        RoadNetwork network = multiRoadNetwork();
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);

        RoadNetworkGenerator.PreviewResult preview = generate(network);
        assertAllNodeElevations(preview, 72);
        assertPavementYs(preview.aggregate(), 72);
        assertEquals(0, preview.aggregate().missingRoadSurfaceCount);
    }

    @Test
    void disablingUniformFlatRestoresPerRoadAuthorityForGeneration() {
        RoadNetwork network = multiRoadNetwork();
        Road edited = network.getRoad("edited");
        assertEquals(ProfileSource.EDITED, edited.getEffectiveProfileSource());

        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);
        assertEquals(ResolvedProfileAuthority.Kind.FLAT,
            ResolvedProfileAuthority.resolve(network, edited).kind());
        assertAllNodeElevations(generate(network), 72);

        network.setElevationMode(NetworkElevationMode.PER_ROAD);
        assertEquals(ResolvedProfileAuthority.Kind.EDITED,
            ResolvedProfileAuthority.resolve(network, edited).kind());
        assertNotNull(edited.getVerticalAlignment());
        assertEquals(ProfileSource.EDITED, edited.getEffectiveProfileSource());

        RoadNetworkGenerator.PreviewResult preview = generate(network);
        // 关闭后 EDITED 路段应按保存曲线生成，不再全网锁死 72
        Set<Integer> nodeYs = new HashSet<>(preview.nodeElevations().values());
        assertFalse(nodeYs.equals(Set.of(72)),
            "after disable, node elevations must leave uniform-only set: " + nodeYs);
    }

    @Test
    void newRoadAfterEnableStillUsesUniformY() {
        RoadNetwork network = multiRoadNetwork();
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);

        Road fourth = network.createRoad("fourth");
        fourth.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        RoadNode a = network.createNode(new Vec2d(0, 40));
        RoadNode b = network.createNode(new Vec2d(40, 40));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), fourth.getId());

        RoadNetworkGenerator.PreviewResult preview = generate(network);
        assertAllNodeElevations(preview, 72);
        assertPavementYs(preview.aggregate(), 72);
    }

    @Test
    void junctionManualElevationOverriddenOnGenerationButPreservedLive() {
        RoadNetwork network = multiRoadNetwork();
        RoadNode mid = findNode(network, 40, 0);
        mid.setManualElevation(80.0);
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);

        RoadNetworkGenerator.PreviewResult preview = generate(network);
        assertEquals(80.0, mid.getManualElevation(), 1e-6);
        assertEquals(72, preview.nodeElevations().get(mid.getId()));
        assertPavementYs(preview.aggregate(), 72);
    }

    @Test
    void changingUniformYRebuildsAllAtNewElevation() {
        RoadNetwork network = multiRoadNetwork();
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);
        assertAllNodeElevations(generate(network), 72);

        network.setUniformElevationY(75);
        RoadNetworkGenerator.PreviewResult preview = generate(network);
        assertAllNodeElevations(preview, 75);
        assertPavementYs(preview.aggregate(), 75);
    }

    private static void assertAllNodeElevations(RoadNetworkGenerator.PreviewResult preview, int y) {
        assertFalse(preview.nodeElevations().isEmpty());
        for (Integer elev : preview.nodeElevations().values()) {
            assertEquals(y, elev.intValue());
        }
    }

    private static void assertPavementYs(RoadGenerationResult aggregate, int expectedY) {
        Set<Integer> roadYs = new HashSet<>();
        for (var entry : aggregate.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            RoadSolidLayer layer = entry.getValue();
            if (pos == null || layer != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record == null) {
                continue;
            }
            roadYs.add(pos.getY());
        }
        assertFalse(roadYs.isEmpty(), "expected ROAD pavement placements");
        assertEquals(Set.of(expectedY), roadYs, "all ROAD pavement Y must equal uniform elevation");
    }

    private static RoadNetworkGenerator.PreviewResult generate(RoadNetwork network) {
        RoadSystemConfig config = new RoadSystemConfig("uniform-flat-e2e");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);
        config.setGenerateBridgePillars(false);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return new RoadNetworkGenerator(generator).generatePreview(network, new FlatTerrainSampler(64));
    }

    private static RoadNode findNode(RoadNetwork network, double x, double y) {
        return network.getNodes().values().stream()
            .filter(n -> Math.hypot(n.getPosition().x - x, n.getPosition().y - y) < 1e-6)
            .findFirst()
            .orElseThrow();
    }

    private static RoadNetwork multiRoadNetwork() {
        RoadNetwork network = new RoadNetwork();

        Road flat = network.createRoad("flat");
        flat.setVerticalMode(RoadVerticalMode.FLAT);
        flat.setFlatVerticalIntent(new com.plot.plugin.road.vertical.FlatVerticalIntent(66.0));

        Road auto = network.createRoad("auto");
        auto.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);

        Road edited = network.createRoad("edited");
        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setMaxSlope(40.0f);
        edited.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 80.0),
            new PointOfVerticalIntersection(40.0, 90.0))));

        RoadNode west = network.createNode(new Vec2d(0, 0));
        RoadNode mid = network.createNode(new Vec2d(40, 0));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode north = network.createNode(new Vec2d(40, 40));

        network.createEdge(west.getId(), mid.getId(),
            List.of(west.getPosition(), mid.getPosition()), flat.getId());
        network.createEdge(mid.getId(), east.getId(),
            List.of(mid.getPosition(), east.getPosition()), auto.getId());
        network.createEdge(mid.getId(), north.getId(),
            List.of(mid.getPosition(), north.getPosition()), edited.getId());

        RoadTestFixtures.confirmEditedProfileGeometry(network, edited);
        return network;
    }
}
