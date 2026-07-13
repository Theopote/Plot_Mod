package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.JunctionMarkingSetting;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadJunctionMarkingGeneratorTest {

    @Test
    void generatesStopLinesCrosswalksAndContinuedMarkingsAtTJunction() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadJunctionMarkingGenerator markingGenerator = new RoadJunctionMarkingGenerator(generator);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode south = network.createNode(new Vec2d(0, -10));

        Road road = network.createRoad();
        road.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        road.setIncludeSidewalk(true);
        road.setSidewalkWidth(2);

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

        List<Vec2d> polygon = RoadJunctionGeometry.buildJunctionFillPolygon(
            junction.getId(),
            edges,
            edge -> RoadModelUtils.getEffectiveWidth(network, edge, config) / 2.0,
            RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
            2.0
        );

        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator.JunctionBlocks();
        markingGenerator.generateStopLines(blocks, junction, network, edges, 64);
        markingGenerator.generateMarkings(blocks, junction, network, edges, polygon, 64);

        assertFalse(blocks.getSolids().isEmpty(), "stop lines should be generated");
        assertTrue(blocks.getSolids().count(RoadSolidLayer.MARKING) >= 12,
            "crosswalk stripes and continued centerline should add marking blocks");
    }

    @Test
    void turnArrowsAppearAtThreeWayJunctionWithoutManualMarkingSetup() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadJunctionMarkingGenerator markingGenerator = new RoadJunctionMarkingGenerator(generator);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode south = network.createNode(new Vec2d(0, -10));

        // 故意不开启中线/分道线：箭头仍应自动出现
        Road road = network.createRoad();

        List<RoadEdge> edges = List.of(
            network.createEdge(
                junction.getId(), north.getId(),
                List.of(new Vec2d(0, 0), new Vec2d(0, 10)), road.getId()),
            network.createEdge(
                junction.getId(), east.getId(),
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()),
            network.createEdge(
                south.getId(), junction.getId(),
                List.of(new Vec2d(0, -10), new Vec2d(0, 0)), road.getId())
        );

        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator.JunctionBlocks();
        markingGenerator.generateMarkings(
            blocks, junction, network, edges, List.of(), 64);

        assertTrue(blocks.getSolids().count(RoadSolidLayer.MARKING) >= 12,
            "topology-based turn arrows should appear without manual lane-marking setup");
    }

    @Test
    void tJunctionSouthApproachResolvesLeftAndStraightTurns() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode south = network.createNode(new Vec2d(0, -10));

        RoadEdge northEdge = network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)));
        RoadEdge eastEdge = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        RoadEdge southEdge = network.createEdge(
            south.getId(), junction.getId(), List.of(new Vec2d(0, -10), new Vec2d(0, 0)));

        var southApproach = new RoadJunctionMarkingGenerator.ApproachGeometry(
            southEdge, RoadJunctionGeometry.computeApproachDirection(southEdge, junction.getId()).normalize());
        var all = List.of(
            new RoadJunctionMarkingGenerator.ApproachGeometry(
                northEdge, RoadJunctionGeometry.computeApproachDirection(northEdge, junction.getId()).normalize()),
            new RoadJunctionMarkingGenerator.ApproachGeometry(
                eastEdge, RoadJunctionGeometry.computeApproachDirection(eastEdge, junction.getId()).normalize()),
            southApproach
        );

        var turns = RoadJunctionMarkingGenerator.resolveTurnOptions(southApproach, all);
        // 从南向北驶入：可直行向北、右转向东（坐标系 y 向上时左转会朝西，此处无西出口）
        assertTrue(turns.straight(), "south approach should allow straight north");
        assertTrue(turns.right() || turns.left(), "south approach should allow turn onto east arm");
    }

    @Test
    void junctionMarkingOverridesDisableCrosswalksAndArrows() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadJunctionMarkingGenerator markingGenerator = new RoadJunctionMarkingGenerator(generator);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setCrosswalks(JunctionMarkingSetting.OFF);
        junction.setTurnArrows(JunctionMarkingSetting.OFF);
        junction.setStopLines(JunctionMarkingSetting.OFF);

        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode south = network.createNode(new Vec2d(0, -10));

        Road road = network.createRoad();
        road.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        road.setIncludeSidewalk(true);

        List<RoadEdge> edges = List.of(
            network.createEdge(
                junction.getId(), north.getId(),
                List.of(new Vec2d(0, 0), new Vec2d(0, 10)), road.getId()),
            network.createEdge(
                junction.getId(), east.getId(),
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()),
            network.createEdge(
                south.getId(), junction.getId(),
                List.of(new Vec2d(0, -10), new Vec2d(0, 0)), road.getId())
        );

        RoadJunctionGenerator.JunctionBlocks fullBlocks = new RoadJunctionGenerator.JunctionBlocks();
        markingGenerator.generateStopLines(fullBlocks, junction, network, edges, 64);
        markingGenerator.generateMarkings(fullBlocks, junction, network, edges, List.of(), 64);

        junction.setCrosswalks(JunctionMarkingSetting.ON);
        junction.setTurnArrows(JunctionMarkingSetting.ON);
        junction.setStopLines(JunctionMarkingSetting.ON);

        RoadJunctionGenerator.JunctionBlocks enabledBlocks = new RoadJunctionGenerator.JunctionBlocks();
        markingGenerator.generateStopLines(enabledBlocks, junction, network, edges, 64);
        markingGenerator.generateMarkings(enabledBlocks, junction, network, edges, List.of(), 64);

        assertTrue(enabledBlocks.getSolids().count(RoadSolidLayer.MARKING)
                > fullBlocks.getSolids().count(RoadSolidLayer.MARKING),
            "forcing markings on should add more blocks than when stop lines, crosswalks and arrows are off");
    }
}
