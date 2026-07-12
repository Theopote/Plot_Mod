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

        assertFalse(blocks.markingBlocks.isEmpty(), "stop lines should be generated");
        assertTrue(blocks.markingBlocks.size() >= 12,
            "crosswalk stripes and continued centerline should add marking blocks");
    }

    @Test
    void turnArrowsAppearAtThreeWayJunction() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(config, null);
        RoadJunctionMarkingGenerator markingGenerator = new RoadJunctionMarkingGenerator(generator);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode south = network.createNode(new Vec2d(0, -10));

        Road road = network.createRoad();
        road.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);

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

        assertTrue(blocks.markingBlocks.size() >= 12,
            "turn arrows add four blocks per approach at a T-junction");
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

        assertTrue(enabledBlocks.markingBlocks.size() > fullBlocks.markingBlocks.size(),
            "forcing markings on should add more blocks than when stop lines, crosswalks and arrows are off");
    }
}
