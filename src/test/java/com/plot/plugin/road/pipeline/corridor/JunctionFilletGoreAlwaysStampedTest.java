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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圆角 gore 径向加密必须并入铺装责任：掩膜核区非空时也不能跳过，否则街角缺铺。
 */
class JunctionFilletGoreAlwaysStampedTest {

    @Test
    void footprintAndGoreDensifyCoverFilletAdditions() {
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadSystemConfig config = new RoadSystemConfig("fillet-gore-stamp");
        config.setDefaultCornerRadius(6.0f);
        junction.setCornerRadius(6.0);

        JunctionGeometryResult geometry = buildGeometry(
            network, junction, config,
            RoadJunctionGenerator.resolveCornerRadius(junction, network, config), 1.0);
        assertFalse(geometry.fillets().isEmpty(), "cross junction should produce fillets");

        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);
        assertFalse(footprint.paveColumns().isEmpty(), "core footprint must already own pave columns");

        List<Vec2d> goreCells = RoadJunctionGenerator.collectFilletGoreCells(geometry, null);
        assertFalse(goreCells.isEmpty(), "gore densify must yield sample cells even when core is non-empty");

        Set<ColumnKey> goreKeys = new HashSet<>();
        for (Vec2d cell : goreCells) {
            goreKeys.add(JunctionWorldFootprint.keyOf(cell, null));
        }
        // 掩膜经同源 densify 应吸收绝大部分 gore；铺装侧仍会无条件并入作双保险
        int covered = 0;
        for (ColumnKey key : goreKeys) {
            if (footprint.paveColumns().contains(key) || footprint.clearance().contains(key)) {
                covered++;
            }
        }
        assertTrue(covered * 4 >= goreKeys.size() * 3,
            "footprint should own >=75% of gore densify columns; covered="
                + covered + "/" + goreKeys.size());
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
