package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 未物化普通 RoadCrossing 也能生成圆角预览几何。 */
class JunctionPreviewGeometryCrossingTest {

    @Test
    void atGradeCrossingWithoutSharedNodeBuildsFilletPreview() {
        RoadSystemConfig config = new RoadSystemConfig("crossing-preview");
        config.setDefaultCornerRadius(3.0f);
        config.setIncludeShoulder(true);
        config.setIncludeSidewalk(true);

        RoadNetwork network = new RoadNetwork();
        Road wide = network.createRoad("wide");
        wide.setWidth(9);
        Road narrow = network.createRoad("narrow");
        narrow.setWidth(5);

        RoadNode a0 = network.createNode(new Vec2d(-40, 0));
        RoadNode a1 = network.createNode(new Vec2d(40, 0));
        RoadNode b0 = network.createNode(new Vec2d(0, -40));
        RoadNode b1 = network.createNode(new Vec2d(0, 40));
        network.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), wide.getId());
        network.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), narrow.getId());

        RoadCrossing crossing = RoadCrossing.atGrade(
            wide.getId(), 40, narrow.getId(), 40, new Vec2d(0, 0)).withCornerRadius(3.0);
        network.registerCrossing(crossing);

        assertTrue(network.getNodes().values().stream().noneMatch(n -> n.getDegree() >= 3),
            "fixture must not create a shared junction node");

        JunctionGeometryResult geometry = JunctionPreviewGeometry.resolve(
            network, config, null, crossing.id());
        assertTrue(geometry.hasFillPolygon(), "crossing-only preview must produce a fill polygon");
        assertTrue(geometry.fillets().size() >= 1, "fillets expected at convex corners");
        assertTrue(geometry.sharpPolygon().size() >= 4);
        assertFalse(geometry.fillPolygon().equals(geometry.sharpPolygon()),
            "filleted outline should differ from sharp when radius > 0");
    }
}
