package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 画布倒圆角必须与道路走廊叠加层共用投影半宽尺度：视图范围变化时绿弧跟着外边路缘走。
 */
class JunctionPreviewGeometryCanvasScaleTest {

    @Test
    void canvasScaledFilletsTrackProjectedOuterCurbsNotNode() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-canvas-scale");
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
        JunctionGeometryResult blockGeom = JunctionPreviewGeometry.resolve(
            network, config, null, crossing.id(), null);
        assertFalse(blockGeom.fillets().isEmpty(), "block-space fillets required");

        // 1 canvas unit = 0.5 block → 半宽/半径在画布上放大 2×
        ICoordinateService zoomed = SnapshotCoordinateService.uniformScale(0.5);
        JunctionGeometryResult canvasGeom = JunctionPreviewGeometry.resolve(
            network, config, null, crossing.id(), zoomed);
        assertEquals(blockGeom.fillets().size(), canvasGeom.fillets().size());

        Vec2d center = crossing.position();
        double blockHalf = 3.0;
        double canvasHalf = JunctionPreviewGeometry.scaleHalfWidthToCanvas(
            blockHalf,
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            zoomed);
        assertEquals(6.0, canvasHalf, 1e-6, "road overlay half-width scale factor");

        for (int i = 0; i < blockGeom.fillets().size(); i++) {
            var block = blockGeom.fillets().get(i);
            var canvas = canvasGeom.fillets().get(i);
            double blockDist = block.center().distance(center);
            double canvasDist = canvas.center().distance(center);
            assertEquals(blockDist * 2.0, canvasDist, 0.15,
                "fillet center must scale with view projection like road half-width");
            // 圆心在铺装外侧：距交点应明显大于半宽（街角外扩，而非贴着节点）
            assertTrue(canvasDist > canvasHalf + 0.5,
                "canvas fillet center must sit outside projected curb, dist=" + canvasDist
                    + " half=" + canvasHalf);
            assertTrue(Math.abs(canvas.sweepRadians()) <= Math.PI + 1e-6);
        }
    }

    @Test
    void identityProjectionMatchesBlockSpaceGeometry() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-identity");
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
        RoadCrossing crossing = network.getCrossings().values().iterator().next();

        JunctionGeometryResult a = JunctionPreviewGeometry.resolve(
            network, config, null, crossing.id(), null);
        JunctionGeometryResult b = JunctionPreviewGeometry.resolve(
            network, config, null, crossing.id(), SnapshotCoordinateService.uniformScale(1.0));

        assertEquals(a.fillets().size(), b.fillets().size());
        for (int i = 0; i < a.fillets().size(); i++) {
            assertEquals(a.fillets().get(i).center().x, b.fillets().get(i).center().x, 1e-4);
            assertEquals(a.fillets().get(i).center().y, b.fillets().get(i).center().y, 1e-4);
            assertEquals(a.fillets().get(i).radius(), b.fillets().get(i).radius(), 1e-4);
        }
    }

    @Test
    void overlaySnapshotPassesCoordinatesIntoFilletArcs() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-overlay-coords");
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

        var unscaled = com.plot.plugin.road.overlay.RoadJunctionOverlayController.snapshot(
            network, builder, config, null, crossing.id(), null, true);
        var scaled = com.plot.plugin.road.overlay.RoadJunctionOverlayController.snapshot(
            network, builder, config, SnapshotCoordinateService.uniformScale(0.5),
            crossing.id(), null, true);

        var u = unscaled.stream().filter(e -> e.selected()).findFirst().orElseThrow();
        var s = scaled.stream().filter(e -> e.selected()).findFirst().orElseThrow();
        assertFalse(u.filletArcs().isEmpty());
        assertFalse(s.filletArcs().isEmpty());

        Vec2d center = crossing.position();
        double uDist = u.filletArcs().getFirst().stream()
            .mapToDouble(p -> p.distance(center)).average().orElse(0);
        double sDist = s.filletArcs().getFirst().stream()
            .mapToDouble(p -> p.distance(center)).average().orElse(0);
        assertTrue(sDist > uDist * 1.5,
            "scaled overlay arcs must sit farther out; u=" + uDist + " s=" + sDist);
    }
}
