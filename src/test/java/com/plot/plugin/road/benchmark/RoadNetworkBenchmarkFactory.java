package com.plot.plugin.road.benchmark;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成路网工厂：用于性能基准与大规模回归。
 */
public final class RoadNetworkBenchmarkFactory {
    private RoadNetworkBenchmarkFactory() {
    }

    /**
     * 线性链：N 条边、N+1 节点、1 条 Road。
     */
    public static RoadNetwork chainNetwork(int edgeCount) {
        if (edgeCount < 1) {
            throw new IllegalArgumentException("edgeCount must be >= 1");
        }
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("bench-chain");
        RoadNode previous = network.createNode(new Vec2d(0, 0));
        for (int i = 0; i < edgeCount; i++) {
            double x = (i + 1) * 10.0;
            RoadNode next = network.createNode(new Vec2d(x, 0));
            network.createEdge(
                previous.getId(),
                next.getId(),
                List.of(new Vec2d(previous.getPosition().x, 0), new Vec2d(x, 0)),
                road.getId());
            previous = next;
        }
        return network;
    }

    /**
     * 网格路网：含大量路口（degree >= 3），用于 junction 基准。
     */
    public static RoadNetwork gridNetwork(int rows, int cols) {
        if (rows < 2 || cols < 2) {
            throw new IllegalArgumentException("rows and cols must be >= 2");
        }
        RoadNetwork network = new RoadNetwork();
        Road[][] roads = new Road[rows][cols - 1];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                roads[r][c] = network.createRoad("grid-r" + r + "-c" + c);
            }
        }
        RoadNode[][] nodes = new RoadNode[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                nodes[r][c] = network.createNode(new Vec2d(c * 12.0, r * 12.0));
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                Vec2d start = nodes[r][c].getPosition();
                Vec2d end = nodes[r][c + 1].getPosition();
                network.createEdge(
                    nodes[r][c].getId(),
                    nodes[r][c + 1].getId(),
                    List.of(start, end),
                    roads[r][c].getId());
            }
        }
        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols; c++) {
                Road road = network.createRoad("grid-v-r" + r + "-c" + c);
                Vec2d start = nodes[r][c].getPosition();
                Vec2d end = nodes[r + 1][c].getPosition();
                network.createEdge(
                    nodes[r][c].getId(),
                    nodes[r + 1][c].getId(),
                    List.of(start, end),
                    road.getId());
            }
        }
        return network;
    }

    /**
     * 五岔路口：中心节点连接 5 条放射边。
     */
    public static RoadNetwork fiveWayJunction() {
        RoadNetwork network = new RoadNetwork();
        RoadNode center = network.createNode(new Vec2d(0, 0));
        double radius = 20.0;
        List<Vec2d> directions = List.of(
            new Vec2d(1, 0),
            new Vec2d(0, 1),
            new Vec2d(-1, 0),
            new Vec2d(0, -1),
            new Vec2d(0.707, 0.707));
        for (int i = 0; i < directions.size(); i++) {
            Vec2d dir = directions.get(i);
            Vec2d endPoint = new Vec2d(dir.x * radius, dir.y * radius);
            RoadNode end = network.createNode(endPoint);
            Road road = network.createRoad("spoke-" + i);
            network.createEdge(
                center.getId(),
                end.getId(),
                List.of(center.getPosition(), endPoint),
                road.getId());
        }
        return network;
    }

    /**
     * 宽路口：长臂 T 形，放大 bounding box。
     */
    public static RoadNetwork wideJunction() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode west = network.createNode(new Vec2d(-80, 0));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        Road r1 = network.createRoad("wide-main");
        Road r2 = network.createRoad("wide-arm");
        network.createEdge(
            west.getId(), junction.getId(),
            List.of(west.getPosition(), junction.getPosition()), r1.getId());
        network.createEdge(
            junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), r1.getId());
        network.createEdge(
            junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), r2.getId());
        return network;
    }

    public static List<RoadNode> junctionNodes(RoadNetwork network, int minDegree) {
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() >= minDegree) {
                junctions.add(node);
            }
        }
        return junctions;
    }
}
