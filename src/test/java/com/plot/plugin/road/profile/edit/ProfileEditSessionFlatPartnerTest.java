package com.plot.plugin.road.profile.edit;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileEditSessionFlatPartnerTest {

    @Test
    void draggingOneFlatPartnerIntersectionSyncsAllOnAdaptiveRoad() {
        RoadNetwork network = adaptiveCrossingFlatTwice();
        Road adaptive = network.getRoad("adaptive");
        Road flat = network.getRoad("flat");
        flat.setVerticalMode(RoadVerticalMode.FLAT);
        flat.setFlatVerticalIntent(new com.plot.plugin.road.vertical.FlatVerticalIntent(64.0));

        List<RoadProfileIntersection> intersections = List.of(
            intersection("n1", adaptive.getId(), flat.getId(), 25.0, 64.0, 64.0),
            intersection("n2", adaptive.getId(), flat.getId(), 75.0, 64.0, 64.0));
        ProfileEditSession session = new ProfileEditSession();
        session.beginIntersectionEdit(intersections);
        session.updateFlatPartnerIntersectionDrag(
            network,
            adaptive,
            new RoadSystemConfig("test"),
            0,
            RoadProfileIntersectionDragEditor.DragTarget.CURRENT,
            68.0,
            intersections);

        List<RoadProfileIntersection> draft = session.effectiveIntersections(intersections);
        assertEquals(68.0, draft.get(0).currentRoadElevation(), 1e-6);
        assertEquals(68.0, draft.get(0).otherRoadElevation(), 1e-6);
        assertEquals(68.0, draft.get(1).currentRoadElevation(), 1e-6);
        assertEquals(68.0, draft.get(1).otherRoadElevation(), 1e-6);
    }

    private static RoadNetwork adaptiveCrossingFlatTwice() {
        RoadNetwork network = new RoadNetwork();
        Road adaptive = network.createRoad("adaptive");
        Road flat = network.createRoad("flat");
        RoadNode w = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(25, 0));
        RoadNode n2 = network.createNode(new Vec2d(75, 0));
        RoadNode e = network.createNode(new Vec2d(100, 0));
        network.createEdge(w.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(25, 0)), adaptive.getId());
        network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(25, 0), new Vec2d(75, 0)), adaptive.getId());
        network.createEdge(n2.getId(), e.getId(),
            List.of(new Vec2d(75, 0), new Vec2d(100, 0)), adaptive.getId());
        network.createEdge(n1.getId(), network.createNode(new Vec2d(25, 20)).getId(),
            List.of(new Vec2d(25, 0), new Vec2d(25, 20)), flat.getId());
        network.createEdge(n2.getId(), network.createNode(new Vec2d(75, 20)).getId(),
            List.of(new Vec2d(75, 0), new Vec2d(75, 20)), flat.getId());
        adaptive.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        return network;
    }

    private static RoadProfileIntersection intersection(
            String nodeId,
            String currentRoadId,
            String otherRoadId,
            double station,
            double currentY,
            double otherY) {
        return new RoadProfileIntersection(
            nodeId,
            currentRoadId,
            otherRoadId,
            "other",
            station,
            station,
            currentY,
            otherY,
            ResolvedCrossSection.fromConfig(new RoadSystemConfig("test")),
            false,
            false,
            4.0,
            false);
    }
}
