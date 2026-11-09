package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionWorldFootprintTest {

    @Test
    void corridorTrimColumnsEqualPaveColumnsAndSubsetOfClearance() {
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadSystemConfig config = new RoadSystemConfig("test");
        double cornerBlocks = RoadJunctionGenerator.resolveCornerRadius(junction, network, config);
        JunctionGeometryResult geometry = buildGeometry(network, junction, config, cornerBlocks, 1.0);

        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);

        assertFalse(footprint.paveColumns().isEmpty(), "filleted cross junction should own pave columns");
        assertEquals(footprint.paveColumns(), footprint.corridorTrimColumns());
        assertTrue(footprint.clearance().containsAll(footprint.paveColumns()),
            "clearance must cover every pave column the corridor yields");
        assertTrue(!footprint.roadCore().isEmpty() || !footprint.filletAdditions().isEmpty(),
            "region geometry should populate core and/or fillet columns");
        assertFalse(footprint.planPointsFor(footprint.paveColumns()).isEmpty());
    }

    @Test
    void planFilterRemovesOnlyFootprintOwnedColumns() {
        ColumnKey owned = new ColumnKey(10, 20);
        ColumnKey free = new ColumnKey(40, 50);
        List<Vec2d> square = List.of(
            new Vec2d(9.5, 19.5),
            new Vec2d(11.5, 19.5),
            new Vec2d(11.5, 21.5),
            new Vec2d(9.5, 21.5));
        JunctionGeometryResult geometry = new JunctionGeometryResult(
            square, square, List.of(), 0.0, 0.0, List.of());
        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);
        assertTrue(footprint.corridorTrimColumns().contains(owned),
            "square raster must own the interior world column");

        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.surfaceMask().put(owned, new SurfaceCell(
            new Vec2d(owned.worldX(), owned.worldZ()), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.surfaceMask().put(free, new SurfaceCell(
            new Vec2d(free.worldX(), free.worldZ()), 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        JunctionCorridorBuildContext context = new JunctionCorridorBuildContext(
            StationFacilityJunctionTrim.FacilityEndpointTrim.NONE,
            List.of(List.of(new Vec2d(0, 0), new Vec2d(100, 0), new Vec2d(100, 100), new Vec2d(0, 100))),
            false,
            footprint);

        JunctionCorridorPlanFilter.apply(plan, context);

        assertNull(plan.surfaceMask().get(owned), "owned column must be trimmed by ColumnKey mask");
        assertNotNull(plan.surfaceMask().get(free), "non-owned column must remain despite large PIP polygon");
    }

    @Test
    void forEdgeAttachesNonEmptyWorldFootprintAtAtGradeJunction() {
        RoadNetwork network = crossNetwork();
        RoadEdge approach = network.getEdges().values().iterator().next();
        RoadSystemConfig config = new RoadSystemConfig("test");
        JunctionCorridorBuildContext context = JunctionCorridorTrim.forEdge(
            network,
            approach,
            com.plot.plugin.road.model.section.ResolvedCrossSection.fromConfig(config),
            config,
            1.0,
            null);
        assertTrue(context.hasColumnMask(), "at-grade junction trim should attach world ColumnKey mask");
        assertFalse(context.worldFootprint().corridorTrimColumns().isEmpty());
    }

    private static JunctionGeometryResult buildGeometry(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config,
            double cornerRadiusBlocks,
            double canvasScale) {
        List<RoadEdge> connected = new ArrayList<>();
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                connected.add(edge);
            }
        }
        double scale = canvasScale > 1e-9 ? canvasScale : 1.0;
        ToDoubleFunction<RoadEdge> halfWidthCanvas = edge -> RoadDimensionUtils.halfExtentFromCenter(
            RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()) * scale;
        double junctionRadius = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            halfWidthCanvas,
            node.getEffectivePlazaRadius(RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS) * scale);
        return RoadJunctionGeometry.buildJunctionGeometry(
            node.getId(),
            connected,
            halfWidthCanvas,
            junctionRadius,
            Math.max(0.0, cornerRadiusBlocks) * scale,
            RoadJunctionCenterlineResolver.forNetwork(network),
            network,
            config,
            scale);
    }

    private static RoadNetwork crossNetwork() {
        RoadNetwork network = new RoadNetwork();
        var roadA = network.createRoad("roadA");
        var roadB = network.createRoad("roadB");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode west = network.createNode(new Vec2d(-80, 0));
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadA.getId());
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), roadA.getId());
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-80, 0)), roadB.getId());
        return network;
    }
}
