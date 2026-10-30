package com.plot.plugin.road.pipeline.marking;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionMarkingGenerator;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.JunctionEndpointSnapTargets;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.style.RoadStyleCatalog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路段轨面 targetY+1 与路口轨面 junctionY+1 的整数衔接约定。
 */
class RoadRailElevationSeamTest {

    @Test
    void flatJunctionRailsShareSingleIntegerElevation() {
        Fixture f = railwayTJunction();
        int junctionY = 64;
        f.markingGenerator.generateMarkings(f.blocks, f.junction, f.network, f.edges, f.polygon, junctionY);

        var railYs = f.blocks.getSolids().primitives().stream()
            .filter(p -> p.materialId() != null && p.materialId().contains("rail"))
            .map(p -> p.elevation())
            .distinct()
            .toList();
        assertEquals(List.of(junctionY + 1), railYs, "flat plaza rails must share one integer Y");
    }

    @Test
    void underpassEndpointSnapMatchesJunctionRailSurface() {
        Fixture f = railwayTJunction();
        int networkUnderpassY = 70;
        Map<String, Integer> elevations = Map.of(f.junction.getId(), networkUnderpassY);
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(0, 10));
        List<SegmentHeightInfo> heights = List.of(
            new SegmentHeightInfo(segment, 68, 70, 68, 70, 0.0));

        Integer snapY = JunctionEndpointSnapTargets.snapTargetY(
            f.junction,
            f.edges.getFirst(),
            f.network,
            elevations,
            heights,
            true,
            (node, network) -> null);
        assertEquals(networkUnderpassY, snapY,
            "underpass edge endpoint must snap to network junction Y for rail seam");

        f.markingGenerator.generateMarkings(
            f.blocks, f.junction, f.network, f.edges, f.polygon, networkUnderpassY);
        assertTrue(
            f.blocks.getSolids().primitives().stream()
                .filter(p -> p.materialId() != null && p.materialId().contains("rail"))
                .allMatch(p -> p.elevation() == networkUnderpassY + 1),
            "junction rails must sit on snapped underpass Y + 1");
    }

    @Test
    void elevatedSnapKeepsDeckAboveUnderpassForRailSeam() {
        Fixture f = railwayTJunction();
        f.junction.setGradeSeparated(true);
        f.junction.setElevatedRoadId(f.road.getId());
        f.junction.setCrossingClearance(4.0);

        int underpassY = 64;
        Map<String, Integer> elevations = Map.of(f.junction.getId(), underpassY);
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(0, 10));
        List<SegmentHeightInfo> heights = List.of(
            new SegmentHeightInfo(segment, 68, 68, 68, 68, 0.0));

        Integer snapY = JunctionEndpointSnapTargets.snapTargetY(
            f.junction,
            f.edges.getFirst(),
            f.network,
            elevations,
            heights,
            true,
            (node, network) -> f.road.getId());
        assertEquals(68, snapY, "elevated rail edge keeps profile deck height");

        int deckY = underpassY + 4;
        f.markingGenerator.generateElevatedRailwayContinuation(
            f.blocks, f.junction, f.network, f.edges, deckY);
        assertTrue(
            f.blocks.getSolids().primitives().stream()
                .filter(p -> "minecraft:rail".equals(p.materialId()))
                .allMatch(p -> p.elevation() == deckY + 1),
            "elevated continuation rails must not drop to underpass Y");
    }

    private static Fixture railwayTJunction() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        RoadJunctionMarkingGenerator markingGenerator = new RoadJunctionMarkingGenerator(generator);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode south = network.createNode(new Vec2d(0, -10));
        Road road = network.createRoad();
        road.applyStyle(RoadStyleCatalog.railway());

        List<RoadEdge> edges = new ArrayList<>();
        edges.add(network.createEdge(
            junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), road.getId()));
        edges.add(network.createEdge(
            junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()));
        edges.add(network.createEdge(
            south.getId(), junction.getId(),
            List.of(new Vec2d(0, -10), new Vec2d(0, 0)), road.getId()));

        List<Vec2d> polygon = com.plot.plugin.road.RoadJunctionGeometry.buildJunctionFillPolygon(
            junction.getId(),
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            com.plot.plugin.road.RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            2.0,
            RoadJunctionCenterlineResolver.forNetwork(network));

        return new Fixture(
            markingGenerator, network, junction, road, edges, polygon, new RoadJunctionGenerator.JunctionBlocks());
    }

    private record Fixture(
            RoadJunctionMarkingGenerator markingGenerator,
            RoadNetwork network,
            RoadNode junction,
            Road road,
            List<RoadEdge> edges,
            List<Vec2d> polygon,
            RoadJunctionGenerator.JunctionBlocks blocks) {
    }
}
