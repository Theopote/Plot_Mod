package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.geometry.RoadCorridorGeometry;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadOverlayGeometryTest {

    private static final List<Vec2d> CENTERLINE = List.of(new Vec2d(0, 0), new Vec2d(100, 0));

    @Test
    void resolveConfigPavementHalfWidthBlocks_includesSidewalkAndShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeSlopeBatter(false);

        double halfWidth = RoadOverlayGeometry.resolveConfigPavementHalfWidthBlocks(config);

        // 行车道半宽 3.5 + 路肩 1 + 人行道 2
        assertEquals(6.5, halfWidth, 0.01);
    }

    @Test
    void resolveConfigCorridorHalfWidth_matchesPavementEnvelopeByDefault() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeBikeLane(true);
        config.setBikeLaneWidth(2);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeSlopeBatter(true);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setCutSlopeRatio(1.0f);

        double corridorHalfWidth = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);
        double pavementHalfWidth = RoadOverlayGeometry.resolveConfigPavementHalfWidthBlocks(config);

        assertEquals(8.5, corridorHalfWidth, 0.01);
        assertEquals(8.5, pavementHalfWidth, 0.01);
    }

    @Test
    void resolveConfigCorridorHalfWidth_extendsWithSlopeBatterWhenRequested() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeBikeLane(true);
        config.setBikeLaneWidth(2);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeSlopeBatter(true);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setCutSlopeRatio(1.0f);

        double pavementHalfWidth = RoadOverlayGeometry.resolveConfigPavementHalfWidthBlocks(config);
        double slopeHalfWidth = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE, true);

        assertEquals(8.5, pavementHalfWidth, 0.01);
        assertTrue(slopeHalfWidth > pavementHalfWidth);
    }

    @Test
    void resolveRoadSlopeBatterGeometry_isWiderThanPavementAndNotNull() {
        RoadSystemConfig config = slopeConfig();
        RoadNetwork network = straightRoad(config);
        Road road = network.getRoads().values().iterator().next();

        RoadCorridorGeometry pavement = RoadOverlayGeometry.resolveRoadCorridorGeometry(
            network, road, config, IdentityCoordinateService.INSTANCE, false);
        RoadCorridorGeometry slope = RoadOverlayGeometry.resolveRoadSlopeBatterGeometry(
            network, road, config, IdentityCoordinateService.INSTANCE, pavement);

        assertTrue(slope != null && slope.primaryFillContour().size() >= 3);
        assertTrue(RoadOverlayGeometry.containsPoint(slope, 50, 0));
        // Pavement stays narrow; slope ring reaches farther off-centerline.
        assertFalse(RoadOverlayGeometry.containsPoint(pavement, 50, 8));
        assertTrue(RoadOverlayGeometry.containsPoint(slope, 50, 8));
    }

    @Test
    void resolveRoadSlopeBatterGeometry_returnsNullWhenSlopeDisabled() {
        RoadSystemConfig config = slopeConfig();
        config.setIncludeSlopeBatter(false);
        RoadNetwork network = straightRoad(config);
        Road road = network.getRoads().values().iterator().next();
        road.setIncludeSlopeBatter(false);

        RoadCorridorGeometry pavement = RoadOverlayGeometry.resolveRoadCorridorGeometry(
            network, road, config, IdentityCoordinateService.INSTANCE, false);
        RoadCorridorGeometry slope = RoadOverlayGeometry.resolveRoadSlopeBatterGeometry(
            network, road, config, IdentityCoordinateService.INSTANCE, pavement);

        assertEquals(null, slope);
    }

    private static RoadSystemConfig slopeConfig() {
        RoadSystemConfig config = new RoadSystemConfig("slope_overlay");
        config.setRoadWidth(7);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeBikeLane(false);
        config.setIncludeSlopeBatter(true);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setCutSlopeRatio(1.0f);
        return config;
    }

    private static RoadNetwork straightRoad(RoadSystemConfig config) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setIncludeSlopeBatter(config.isIncludeSlopeBatter());
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }

    @Test
    void resolveConfigCorridorHalfWidth_updatesWhenRoadWidthChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setRoadWidth(5);
        double narrow = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);

        config.setRoadWidth(9);
        double wide = RoadOverlayGeometry.resolveConfigCorridorHalfWidth(
            config, CENTERLINE, IdentityCoordinateService.INSTANCE);

        assertEquals(2.5, narrow, 0.01);
        assertEquals(4.5, wide, 0.01);
        assertTrue(wide > narrow);
    }

    @Test
    void resolveRoadCorridor_usesPlanCenterlineWhenHorizontalAlignmentDefined() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeSlopeBatter(false);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setIncludeSlopeBatter(false);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 8), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        List<Vec2d> planCenterline = RoadOverlayGeometry.resolvePlanCenterline(network, road);
        List<Vec2d> storedCenterline = RoadOverlayGeometry.resolveRoadCenterline(network, road);
        List<Vec2d> corridor = RoadOverlayGeometry.resolveRoadCorridor(
            network, road, config, IdentityCoordinateService.INSTANCE);

        assertTrue(planCenterline.size() >= 2);
        // 100 m HA @ 1 m overlay spacing → denser than default 2 m materialize spacing
        assertTrue(planCenterline.size() >= 90);
        assertEquals(0.0, storedCenterline.getFirst().y, 1e-6);
        assertEquals(8.0, planCenterline.getFirst().y, 0.2);
        assertFalse(corridor.isEmpty());
        assertTrue(RoadOverlayGeometry.containsPoint(corridor, 50, 8));
        assertFalse(RoadOverlayGeometry.containsPoint(corridor, 50, 0));
    }

    @Test
    void containsPoint_closedLoopExcludesInnerHole() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 10),
            new Vec2d(0, 10),
            new Vec2d(0, 0));
        RoadCorridorGeometry geometry = com.plot.plugin.road.geometry.RoadCorridorGeometryBuilder.build(
            centerline, 3.0, true);

        assertTrue(RoadOverlayGeometry.containsPoint(geometry, 1, 1));
        assertFalse(RoadOverlayGeometry.containsPoint(geometry, 10, 5));
    }

    @Test
    void resolveRoadCorridorGeometry_followsVariableCrossSectionHalfWidth() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeSlopeBatter(false);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("var");
        road.setWidth(6);
        road.setIncludeSlopeBatter(false);
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(16);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(50.0, wide)
        ), VariableCrossSectionTransitionMode.STEP));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadCorridorGeometry geometry = RoadOverlayGeometry.resolveRoadCorridorGeometry(
            network, road, config, IdentityCoordinateService.INSTANCE);

        assertTrue(geometry.primaryFillContour().size() >= 3);
        // 起点仍是 6 格宽（半宽 3）；过 50 后加宽到 16（半宽 8）
        assertTrue(RoadOverlayGeometry.containsPoint(geometry, 10, 0));
        assertTrue(RoadOverlayGeometry.containsPoint(geometry, 10, 2.5));
        assertFalse(RoadOverlayGeometry.containsPoint(geometry, 10, 7));
        assertTrue(RoadOverlayGeometry.containsPoint(geometry, 80, 7));
        assertFalse(RoadOverlayGeometry.containsPoint(geometry, 80, 10));
    }

    @Test
    void resolveSpiralHighlightSegments_samplesClothoidsOnly() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ha");
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of(
            HorizontalAlignmentElement.tangent(40.0),
            HorizontalAlignmentElement.spiral(20.0, 15.0),
            HorizontalAlignmentElement.circularArc(25.0, 40.0, com.plot.plugin.road.alignment.TurnDirection.LEFT),
            HorizontalAlignmentElement.tangent(30.0)
        ));
        road.setHorizontalAlignment(alignment);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        List<List<Vec2d>> spirals = RoadOverlayGeometry.resolveSpiralHighlightSegments(network, road);
        assertEquals(1, spirals.size());
        assertTrue(spirals.getFirst().size() >= 2);

        Road plain = network.createRoad("plain");
        assertTrue(RoadOverlayGeometry.resolveSpiralHighlightSegments(network, plain).isEmpty());
    }

    @Test
    void resolveRoadCorridorGeometry_marksLoopRoadClosed() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeSlopeBatter(false);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        network.createEdge(
            n1.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(0, 10), new Vec2d(0, 0)),
            road.getId());

        RoadCorridorGeometry geometry = RoadOverlayGeometry.resolveRoadCorridorGeometry(
            network, road, config, IdentityCoordinateService.INSTANCE);

        assertTrue(geometry.closed());
        assertEquals(2, geometry.fillContours().size());
    }
}
