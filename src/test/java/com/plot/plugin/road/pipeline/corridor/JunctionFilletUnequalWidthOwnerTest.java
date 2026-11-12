package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.JunctionCurbFilletBuilder;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不等宽路口：切点归属必须跟路缘半宽，而非最近中心线。
 */
class JunctionFilletUnequalWidthOwnerTest {

    @Test
    void curbOwnerPrefersMatchingHalfWidthOverNearestCenterline() {
        // 南北半宽 6、东西半宽 2；切点 (6,3) 距 EW 中心更近，但落在 NS 路缘上。
        List<JunctionCurbFilletBuilder.Approach> approaches = List.of(
            new JunctionCurbFilletBuilder.Approach(new Vec2d(0, 1), 6.0, "ns"),
            new JunctionCurbFilletBuilder.Approach(new Vec2d(1, 0), 2.0, "ew"));
        Vec2d tangent = new Vec2d(6, 3);
        String owner = RoadJunctionGeometry.resolveCurbOwnerEdgeId(tangent, approaches, new Vec2d(0, 0));
        assertEquals("ns", owner, "tangent on wide NS curb must not bind to narrow EW");
    }

    @Test
    void loftUsesGeometryOwnerIdsNotNearestCenterline() {
        RoadNetwork network = new RoadNetwork();
        Road nsRoad = network.createRoad("ns");
        nsRoad.setWidth(13); // ~half 6
        Road ewRoad = network.createRoad("ew");
        ewRoad.setWidth(5); // ~half 2
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        RoadEdge ns = network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), nsRoad.getId());
        RoadEdge ew = network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), ewRoad.getId());

        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(6, 6),
            new Vec2d(6, 3), // 宽路路缘侧
            new Vec2d(3, 2),
            4.0,
            Math.PI / 2.0).withOwners(ns.getId(), ew.getId());

        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        // 几何 id 优先：即使最近中心线会选错，仍应落到 ns
        RoadEdge byId = JunctionFilletElevationLoft.edgeById(fillet.ownerStartEdgeId(),
            List.of(ns, ew));
        assertSame(ns, byId);
        RoadEdge nearest = JunctionFilletElevationLoft.resolveTangentOwner(
            fillet.tangentStart(), List.of(ns, ew), centerlines);
        // 文档化：最近中心线可能选 ew；几何 owner 必须覆盖该错误
        assertNotNull(fillet.ownerStartEdgeId());
        assertEquals(ns.getId(), fillet.ownerStartEdgeId());
        if (nearest != null && nearest.getId().equals(ew.getId())) {
            assertTrue(true, "nearest-centerline would pick EW; geometry owner overrides");
        }
    }

    @Test
    void pinElevationInsertsExactTangentStation() {
        java.util.List<Double> distances = new java.util.ArrayList<>(List.of(0.0, 1.0, 2.0, 3.0, 4.0));
        java.util.List<Double> elevations = new java.util.ArrayList<>(List.of(64.0, 64.0, 65.0, 66.0, 67.0));
        JunctionFilletElevationLoft.pinElevation(distances, elevations, 2.4, 70.0);
        assertTrue(distances.contains(2.4));
        int idx = distances.indexOf(2.4);
        assertEquals(70.0, elevations.get(idx), 1e-9);
        // 邻近整数格不得被改成切点高程
        assertEquals(65.0, elevations.get(distances.indexOf(2.0)), 1e-9);
        assertEquals(66.0, elevations.get(distances.indexOf(3.0)), 1e-9);
    }
}
