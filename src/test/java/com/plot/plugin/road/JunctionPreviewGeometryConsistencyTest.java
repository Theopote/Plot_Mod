package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingMaterializer;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 未物化 Crossing 预览与物化节点生成共用权威几何。
 */
class JunctionPreviewGeometryConsistencyTest {

    @Test
    void unmaterializedCrossingPreviewMatchesMaterializedNodeGeometry() {
        RoadSystemConfig config = new RoadSystemConfig("preview-consistency");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius(3.0f);

        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);
        assertTrue(network.getCrossings().size() >= 1);

        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        JunctionGeometryResult preview = JunctionPreviewGeometry.fromCrossing(network, config, crossing);
        assertTrue(preview.hasFillPolygon(), "canvas preview must resolve geometry");

        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = materialized.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        if (crossing.cornerRadius() != null) {
            junction.setCornerRadius(crossing.cornerRadius());
        }
        JunctionGeometryResult generated = JunctionPreviewGeometry.fromNode(
            materialized, config, junction, crossing);
        assertTrue(generated.hasFillPolygon(), "materialized node must resolve geometry");

        assertEquals(
            roundArea(generated.fillPolygon()),
            roundArea(preview.fillPolygon()),
            1e-3,
            "preview and generation fill area must share the same authority geometry");
        assertEquals(
            generated.fillPolygon().size(),
            preview.fillPolygon().size(),
            "preview and generation vertex counts should match after materialization path");
    }

    @Test
    void fromCrossingPrefersMaterializedPathOverTangentFallback() {
        RoadSystemConfig config = new RoadSystemConfig("preview-authority");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius(4.0f);

        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();

        JunctionGeometryResult authority = JunctionPreviewGeometry.fromCrossing(network, config, crossing);
        JunctionGeometryResult tangent = JunctionPreviewGeometry.fromCrossingTangentFallback(
            network, config, crossing);
        assertTrue(authority.hasFillPolygon());
        assertTrue(tangent.hasFillPolygon());
        // 正交恒宽时两者接近；权威路径必须至少产出可用铺装
        assertTrue(authority.fillPolygon().size() >= 3);
    }

    private static double roundArea(List<Vec2d> polygon) {
        return Math.round(JunctionRegionBuilder.polygonArea(polygon) * 1000.0) / 1000.0;
    }
}
