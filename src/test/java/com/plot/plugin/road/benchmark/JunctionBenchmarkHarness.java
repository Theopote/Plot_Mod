package com.plot.plugin.road.benchmark;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.FlatTerrainSampler;

import java.util.List;
import java.util.Objects;

/**
 * 路口栅格化基准 harness（J01–J05）。
 */
public final class JunctionBenchmarkHarness {

    public record JunctionCase(String id, RoadNetwork network, int minDegree) {
    }

    public record JunctionMetrics(
            String id,
            int junctionCount,
            int totalVertices,
            double totalBboxArea,
            long rasterMillis,
            int generatedCells) {

        public String summary() {
            return String.format(
                "%s junctions=%d vertices=%d bboxArea=%.0f rasterMs=%d cells=%d",
                id, junctionCount, totalVertices, totalBboxArea, rasterMillis, generatedCells);
        }
    }

    private JunctionBenchmarkHarness() {
    }

    public static List<JunctionCase> standardCases() {
        return List.of(
            new JunctionCase("J01", tJunction(), 3),
            new JunctionCase("J02", crossJunction(), 4),
            new JunctionCase("J03", RoadNetworkBenchmarkFactory.fiveWayJunction(), 5),
            new JunctionCase("J04", eightWayJunction(), 8),
            new JunctionCase("J05", RoadNetworkBenchmarkFactory.wideJunction(), 3));
    }

    public static JunctionMetrics run(JunctionCase junctionCase) {
        RoadGenerator generator = new RoadGenerator(
            RoadBenchmarkHarness.benchmarkConfig(),
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        var terrain = new FlatTerrainSampler(64);
        var nodeElevations = generator.resolveNetworkNodeElevations(junctionCase.network(), terrain);

        int vertices = 0;
        double bboxArea = 0;
        int cells = 0;
        long start = System.nanoTime();
        List<RoadNode> junctions = RoadNetworkBenchmarkFactory.junctionNodes(
            junctionCase.network(), junctionCase.minDegree());
        for (RoadNode node : junctions) {
            var connected = node.getConnectedEdgeIds().stream()
                .map(junctionCase.network()::getEdge)
                .filter(Objects::nonNull)
                .toList();
            var polygon = RoadJunctionGeometry.buildJunctionFillPolygon(
                node.getId(),
                connected,
                edge -> 3.0,
                RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS,
                0.0,
                RoadJunctionCenterlineResolver.forNetwork(junctionCase.network()));
            if (!polygon.isEmpty()) {
                vertices += polygon.size();
                bboxArea += polygonBoundingArea(polygon);
            }
            var blocks = junctionGenerator.generateJunction(
                node, junctionCase.network(), terrain, nodeElevations);
            cells += blocks.getSolids().primitives().size();
        }
        long rasterMillis = (System.nanoTime() - start) / 1_000_000L;
        if (junctions.isEmpty()) {
            throw new IllegalStateException(junctionCase.id() + " must have junction nodes");
        }
        return new JunctionMetrics(
            junctionCase.id(),
            junctions.size(),
            vertices,
            bboxArea,
            rasterMillis,
            cells);
    }

    private static RoadNetwork tJunction() {
        RoadNetwork network = new RoadNetwork();
        var j = network.createNode(new Vec2d(0, 0));
        var n = network.createNode(new Vec2d(0, 20));
        var w = network.createNode(new Vec2d(-20, 0));
        var e = network.createNode(new Vec2d(20, 0));
        var r1 = network.createRoad("main");
        var r2 = network.createRoad("arm");
        network.createEdge(w.getId(), j.getId(), List.of(w.getPosition(), j.getPosition()), r1.getId());
        network.createEdge(j.getId(), e.getId(), List.of(j.getPosition(), e.getPosition()), r1.getId());
        network.createEdge(j.getId(), n.getId(), List.of(j.getPosition(), n.getPosition()), r2.getId());
        return network;
    }

    private static RoadNetwork crossJunction() {
        RoadNetwork network = new RoadNetwork();
        var j = network.createNode(new Vec2d(0, 0));
        var n = network.createNode(new Vec2d(0, 20));
        var s = network.createNode(new Vec2d(0, -20));
        var w = network.createNode(new Vec2d(-20, 0));
        var e = network.createNode(new Vec2d(20, 0));
        var r1 = network.createRoad("ns");
        var r2 = network.createRoad("ew");
        network.createEdge(s.getId(), j.getId(), List.of(s.getPosition(), j.getPosition()), r1.getId());
        network.createEdge(j.getId(), n.getId(), List.of(j.getPosition(), n.getPosition()), r1.getId());
        network.createEdge(w.getId(), j.getId(), List.of(w.getPosition(), j.getPosition()), r2.getId());
        network.createEdge(j.getId(), e.getId(), List.of(j.getPosition(), e.getPosition()), r2.getId());
        return network;
    }

    private static RoadNetwork eightWayJunction() {
        RoadNetwork network = new RoadNetwork();
        var center = network.createNode(new Vec2d(0, 0));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            var end = network.createNode(new Vec2d(
                Math.cos(angle) * 25, Math.sin(angle) * 25));
            var road = network.createRoad("arm-" + i);
            network.createEdge(
                center.getId(), end.getId(),
                List.of(center.getPosition(), end.getPosition()),
                road.getId());
        }
        return network;
    }

    private static double polygonBoundingArea(List<Vec2d> polygon) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (var p : polygon) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        return Math.max(0, maxX - minX) * Math.max(0, maxY - minY);
    }
}
