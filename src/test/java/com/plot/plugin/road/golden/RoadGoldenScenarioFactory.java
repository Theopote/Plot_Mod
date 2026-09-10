package com.plot.plugin.road.golden;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路 Golden 场景工厂（R01–R12）。
 */
public final class RoadGoldenScenarioFactory {
    private RoadGoldenScenarioFactory() {
    }

    public static List<RoadGoldenScenario> all() {
        return List.of(
            r01StraightFlat(),
            r02Curved(),
            r03TJunction(),
            r04CrossJunction(),
            r05FiveWay(),
            r06Sloped(),
            r07TerrainFit(),
            r08Bridge(),
            r09Tunnel(),
            r10WaterCrossing(),
            r11GradeSeparated(),
            r12ClosedShape());
    }

    public static RoadGoldenScenario r01StraightFlat() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r01");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return scenario("R01", "straight flat road", network, flat(64), baseConfig());
    }

    public static RoadGoldenScenario r02Curved() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r02");
        List<Vec2d> arc = new ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double t = Math.PI * i / 16.0;
            arc.add(new Vec2d(15 + 12 * Math.cos(t), 12 * Math.sin(t)));
        }
        RoadNode start = network.createNode(arc.getFirst());
        RoadNode end = network.createNode(arc.getLast());
        network.createEdge(start.getId(), end.getId(), arc, road.getId());
        return scenario("R02", "curved road", network, flat(64), baseConfig());
    }

    public static RoadGoldenScenario r03TJunction() {
        RoadNetwork network = junctionNetwork(3);
        return scenario("R03", "T junction", network, flat(64), baseConfig());
    }

    public static RoadGoldenScenario r04CrossJunction() {
        RoadNetwork network = junctionNetwork(4);
        return scenario("R04", "cross junction", network, flat(64), baseConfig());
    }

    public static RoadGoldenScenario r05FiveWay() {
        RoadNetwork network = fiveWayNetwork();
        return scenario("R05", "five-way junction", network, flat(64), baseConfig());
    }

    public static RoadGoldenScenario r06Sloped() {
        RoadNetwork network = r01StraightFlat().network();
        Road road = network.getRoads().values().iterator().next();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(30, 72))));
        RoadSystemConfig config = baseConfig();
        config.setMaxSlope(8.0f);
        return scenario("R06", "sloped road", network, flat(64), config);
    }

    public static RoadGoldenScenario r07TerrainFit() {
        TerrainSampler rolling = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d p) {
                return 64 + (int) Math.round(2 * Math.sin(p.x / 5.0));
            }
            @Override public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
        return scenario("R07", "terrain fit", r01StraightFlat().network(), rolling, baseConfig());
    }

    public static RoadGoldenScenario r08Bridge() {
        TerrainSampler valley = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d p) {
                return p.x >= 8 && p.x <= 22 ? 50 : 64;
            }
            @Override public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
        RoadSystemConfig config = baseConfig();
        config.setBridgeThreshold(2);
        config.setIncludeShoulder(true);
        return scenario("R08", "bridge", r01StraightFlat().network(), valley, config);
    }

    public static RoadGoldenScenario r09Tunnel() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r09");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        a.setManualElevation(64.0);
        b.setManualElevation(64.0);
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        TerrainSampler mountain = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d p) { return 72; }
            @Override public boolean isSolidBlock(int x, int y, int z) { return y <= 72; }
        };
        RoadSystemConfig config = baseConfig();
        config.setTunnelThreshold(4);
        config.setBridgeThreshold(2);
        config.setMinimumConstructionRunLength(2.0);
        return scenario("R09", "tunnel", network, mountain, config);
    }

    public static RoadGoldenScenario r10WaterCrossing() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r10");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        a.setManualElevation(64.0);
        b.setManualElevation(64.0);
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        TerrainSampler water = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d p) { return 50; }
            @Override public int sampleColumnTopY(Vec2d p) { return 63; }
            @Override public boolean isSolidBlock(int x, int y, int z) { return y <= 50; }
        };
        RoadSystemConfig config = baseConfig();
        config.setBridgeThreshold(3);
        config.setIncludeShoulder(true);
        return scenario("R10", "water crossing", network, water, config);
    }

    public static RoadGoldenScenario r11GradeSeparated() {
        RoadNetwork network = new RoadNetwork();
        RoadNode center = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 20));
        RoadNode south = network.createNode(new Vec2d(0, -20));
        RoadNode east = network.createNode(new Vec2d(20, 0));
        RoadNode west = network.createNode(new Vec2d(-20, 0));
        Road nsRoad = network.createRoad("ns");
        Road ewRoad = network.createRoad("ew");
        network.createEdge(south.getId(), center.getId(), List.of(south.getPosition(), center.getPosition()), nsRoad.getId());
        network.createEdge(center.getId(), north.getId(), List.of(center.getPosition(), north.getPosition()), nsRoad.getId());
        network.createEdge(west.getId(), center.getId(), List.of(west.getPosition(), center.getPosition()), ewRoad.getId());
        network.createEdge(center.getId(), east.getId(), List.of(center.getPosition(), east.getPosition()), ewRoad.getId());
        if (!network.setNodeGradeSeparation(center.getId(), true, nsRoad.getId(), 3.0)) {
            throw new IllegalStateException("grade separation setup failed");
        }
        RoadSystemConfig config = baseConfig();
        config.setDefaultCrossingClearance(3.0);
        return scenario("R11", "grade-separated crossing", network, flat(70), config);
    }

    public static RoadGoldenScenario r12ClosedShape() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r12");
        Vec2d[] corners = {
            new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 15), new Vec2d(0, 15)
        };
        RoadNode[] nodes = new RoadNode[4];
        for (int i = 0; i < 4; i++) {
            nodes[i] = network.createNode(corners[i]);
        }
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            network.createEdge(
                nodes[i].getId(), nodes[next].getId(),
                List.of(corners[i], corners[next]),
                road.getId());
        }
        RoadTopologyRoadSplitter.repairAfterAdopt(network);
        road = network.getRoad(road.getId());
        if (road != null) {
            road.setTopologyMode(RoadTopologyMode.LOOP);
        }
        return scenario("R12", "closed road shape", network, flat(64), baseConfig());
    }

    private static RoadNetwork junctionNetwork(int arms) {
        RoadNetwork network = new RoadNetwork();
        RoadNode center = network.createNode(new Vec2d(0, 0));
        Vec2d[] dirs = {
            new Vec2d(0, 20), new Vec2d(20, 0), new Vec2d(0, -20), new Vec2d(-20, 0)
        };
        for (int i = 0; i < arms; i++) {
            Vec2d endPoint = dirs[i];
            RoadNode end = network.createNode(endPoint);
            Road road = network.createRoad("arm-" + i);
            network.createEdge(
                center.getId(), end.getId(),
                List.of(center.getPosition(), endPoint),
                road.getId());
        }
        return network;
    }

    private static RoadNetwork fiveWayNetwork() {
        RoadNetwork network = new RoadNetwork();
        RoadNode center = network.createNode(new Vec2d(0, 0));
        List<Vec2d> dirs = List.of(
            new Vec2d(20, 0), new Vec2d(0, 20), new Vec2d(-20, 0),
            new Vec2d(0, -20), new Vec2d(14, 14));
        for (int i = 0; i < dirs.size(); i++) {
            Vec2d endPoint = dirs.get(i);
            RoadNode end = network.createNode(endPoint);
            Road road = network.createRoad("spoke-" + i);
            network.createEdge(
                center.getId(), end.getId(),
                List.of(center.getPosition(), endPoint),
                road.getId());
        }
        return network;
    }

    private static RoadGoldenScenario scenario(
            String id,
            String description,
            RoadNetwork network,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        return new RoadGoldenScenario(id, description, network, terrain, config);
    }

    private static TerrainSampler flat(int elevation) {
        return new FlatTerrainSampler(elevation);
    }

    private static RoadSystemConfig baseConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road-golden");
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setRoadWidth(5);
        config.setMaxSlope(12.0f);
        return config;
    }
}
