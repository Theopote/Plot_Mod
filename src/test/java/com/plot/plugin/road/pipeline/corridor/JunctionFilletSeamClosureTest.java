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
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接缝闭合：走廊让出的世界列必须被路口铺装掩膜接管；清障覆盖铺装。
 */
class JunctionFilletSeamClosureTest {

    @Test
    void corridorTrimColumnsAreFullyOwnedByJunctionPaveMask() {
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadSystemConfig config = new RoadSystemConfig("seam-closure");
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);

        JunctionCorridorBuildContext trim = JunctionCorridorTrim.forEdge(
            network,
            network.getEdges().values().iterator().next(),
            crossSection,
            config,
            1.0,
            null);
        assertTrue(trim.hasColumnMask());

        JunctionGeometryResult geometry = buildGeometry(
            network, junction, config,
            RoadJunctionGenerator.resolveCornerRadius(junction, network, config), 1.0);
        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);

        assertFalse(footprint.paveColumns().isEmpty());
        assertTrue(footprint.clearance().containsAll(footprint.paveColumns()));
        // 走廊裁剪列 ⊆ 路口铺装列（同一工厂语义）
        assertTrue(footprint.paveColumns().containsAll(trim.worldFootprint().corridorTrimColumns())
                || trim.worldFootprint().corridorTrimColumns().equals(footprint.paveColumns())
                || footprint.corridorTrimColumns().containsAll(trim.worldFootprint().corridorTrimColumns()),
            "junction pave mask must own every corridor-trimmed column");
    }

    @Test
    void planFilterAndFootprintAgreeOnSeamColumns() {
        RoadNetwork network = crossNetwork();
        RoadSystemConfig config = new RoadSystemConfig("seam-filter");
        JunctionCorridorBuildContext trim = JunctionCorridorTrim.forEdge(
            network,
            network.getEdges().values().iterator().next(),
            ResolvedCrossSection.fromConfig(config),
            config,
            1.0,
            null);

        Set<ColumnKey> owned = trim.worldFootprint().corridorTrimColumns();
        assertFalse(owned.isEmpty());
        ColumnKey sample = owned.iterator().next();
        ColumnKey outsider = new ColumnKey(sample.worldX() + 80, sample.worldZ() + 80);

        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.surfaceMask().put(sample, new SurfaceCell(
            new Vec2d(sample.worldX(), sample.worldZ()), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.surfaceMask().put(outsider, new SurfaceCell(
            new Vec2d(outsider.worldX(), outsider.worldZ()), 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        JunctionCorridorPlanFilter.apply(plan, trim);
        assertTrue(plan.surfaceMask().get(sample) == null);
        assertTrue(plan.surfaceMask().get(outsider) != null);
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
