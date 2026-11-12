package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFilletElevationLoftTest {

    @Test
    void projectedCurbStationUsesRayNotRadialDistance() {
        Vec2d sharp = new Vec2d(0, 0);
        Vec2d dirA = new Vec2d(1, 0);
        // 点在 45° 方向、欧氏距离约 5；沿 A 轴投影应为 ~3.5 而非 5。
        Vec2d point = new Vec2d(3.5, 3.5);
        double s = JunctionFilletElevationLoft.projectedCurbStation(point, sharp, dirA, 8.0);
        assertTrue(Math.abs(s - 3.5) < 1e-6, "projected station along curb A; s=" + s);
        assertTrue(s < point.distance(sharp) - 0.5,
            "projection must be shorter than polar radius so grade does not advance early");
    }

    @Test
    void goreOverlapKeepsExactApproachVoxelElevation() {
        var network = orthogonalNetwork();
        var edge = edgeToward(network, new Vec2d(0, 40));
        var result = new RoadGenerationResult(0);
        var pos = new BlockPos(2, 71, 8);
        result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
        var center = new Vec2d(10, 10);
        var start = new Vec2d(2, 10);
        var end = new Vec2d(10, 2);
        var fillet = new JunctionGeometryResult.FilletCorner(center, start, end, 8, Math.PI / 2,
            FilletArcSweep.resolveShort(center, start, end));
        var columns = new RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.floor(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.floor(p.y); }
        };
        var loft = JunctionFilletElevationLoft.build(List.of(fillet), Map.of(edge.getId(), result),
            List.copyOf(network.getEdges().values()), network, columns, 64);
        var point = new Vec2d(2.1, 8);
        assertTrue(loft.isFilletGore(point));
        int y = loft.surfaceY(point);
        assertTrue(y >= 64 && y < 71,
            "gore lofts along curb rails; leftover voxel in the column must not pin the patch; y=" + y);
    }

    @Test
    void slopedCurbRailsLoftIgnoresVoxelNoiseAlongTheGore() {
        var network = orthogonalNetwork();
        var ns = edgeToward(network, new Vec2d(0, 40));
        var ew = edgeToward(network, new Vec2d(40, 0));
        var nsResult = new RoadGenerationResult(0);
        var ewResult = new RoadGenerationResult(0);
        for (int z = 2; z <= 14; z++) {
            nsResult.recordPlacementOverride(new BlockPos(2, 64, z), "minecraft:air", "minecraft:stone");
            nsResult.placementLayers.put(new BlockPos(2, 64, z), RoadSolidLayer.ROAD);
        }
        for (int x = 2; x <= 14; x++) {
            int y = 64 + Math.min(8, x - 2);
            ewResult.recordPlacementOverride(new BlockPos(x, y, 2), "minecraft:air", "minecraft:stone");
            ewResult.placementLayers.put(new BlockPos(x, y, 2), RoadSolidLayer.ROAD);
        }
        // 圆角内部体素噪声：旧实现会沿路缘逐格拾取，坡度一大就起伏。
        nsResult.recordPlacementOverride(new BlockPos(3, 71, 4), "minecraft:air", "minecraft:stone");
        nsResult.placementLayers.put(new BlockPos(3, 71, 4), RoadSolidLayer.ROAD);
        ewResult.recordPlacementOverride(new BlockPos(4, 59, 3), "minecraft:air", "minecraft:stone");
        ewResult.placementLayers.put(new BlockPos(4, 59, 3), RoadSolidLayer.ROAD);

        var center = new Vec2d(6, 6);
        var start = new Vec2d(2, 6);
        var end = new Vec2d(6, 2);
        var fillet = new JunctionGeometryResult.FilletCorner(
            center, start, end, 4.0, Math.PI / 2,
            FilletArcSweep.resolveShort(center, start, end));
        var columns = new RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.floor(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.floor(p.y); }
        };
        var loft = JunctionFilletElevationLoft.build(
            List.of(fillet),
            Map.of(ns.getId(), nsResult, ew.getId(), ewResult),
            List.copyOf(network.getEdges().values()),
            network,
            columns,
            64);

        int prev = loft.surfaceY(new Vec2d(2.3, 2.3));
        for (double t = 0.15; t <= 0.9; t += 0.1) {
            Vec2d p = new Vec2d(2.3 + t * 3.2, 2.3 + (1.0 - t) * 0.2);
            if (!loft.isFilletGore(p)) {
                continue;
            }
            int y = loft.surfaceY(p);
            assertTrue(Math.abs(y - prev) <= 2, "lofted fillet must not jump; y=" + y + " prev=" + prev);
            assertTrue(y >= 63 && y <= 73, "loft stays between curb rails; y=" + y);
            assertTrue(y != 71 && y != 59, "interior voxel noise must not become fillet Y; y=" + y);
            prev = y;
        }
    }

    @Test
    void gentleGradeFilletDoesNotRiseAheadOfOwnedRoad() {
        // 东西向从路口每 8 格抬 1 格；南北向保持 64。圆角靠近东路缘时不得提前抬到 65。
        var network = orthogonalNetwork();
        var ns = edgeToward(network, new Vec2d(0, 40));
        var ew = edgeToward(network, new Vec2d(40, 0));
        var nsResult = new RoadGenerationResult(0);
        var ewResult = new RoadGenerationResult(0);
        for (int z = 0; z <= 16; z++) {
            for (int x = -3; x <= 3; x++) {
                nsResult.recordPlacementOverride(new BlockPos(x, 64, z), "minecraft:air", "minecraft:stone");
                nsResult.placementLayers.put(new BlockPos(x, 64, z), RoadSolidLayer.ROAD);
            }
        }
        for (int x = 0; x <= 20; x++) {
            int y = 64 + x / 8;
            for (int z = -3; z <= 3; z++) {
                ewResult.recordPlacementOverride(new BlockPos(x, y, z), "minecraft:air", "minecraft:stone");
                ewResult.placementLayers.put(new BlockPos(x, y, z), RoadSolidLayer.ROAD);
            }
        }
        var center = new Vec2d(12, 12);
        var start = new Vec2d(4, 12);
        var end = new Vec2d(12, 4);
        var fillet = new JunctionGeometryResult.FilletCorner(
            center, start, end, 8.0, Math.PI / 2,
            FilletArcSweep.resolveShort(center, start, end));
        var columns = new RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.floor(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.floor(p.y); }
        };
        var loft = JunctionFilletElevationLoft.build(
            List.of(fillet),
            Map.of(ns.getId(), nsResult, ew.getId(), ewResult),
            List.copyOf(network.getEdges().values()),
            network,
            columns,
            64);
        Vec2d midEastCurb = new Vec2d(8.2, 4.4);
        assertTrue(loft.isFilletGore(midEastCurb), "sample must sit in gore");
        int y = loft.surfaceY(midEastCurb);
        int roadYAtSample = 64 + 8 / 8; // x=8 刚到下一台阶，道路仍按 floor 站距停在 64 直到走完
        assertTrue(y <= 64,
            "gentle rising curb must not step to 65 before the owned road; y=" + y
                + " roadStep=" + roadYAtSample);
        assertEquals(64, loft.surfaceY(new Vec2d(0.4, 0.4)),
            "plaza core must stay at junctionY on a gentle one-sided grade");
    }

    @Test
    void angularBlendSweepsFromStartCurbToEndCurb() {
        Vec2d sharp = new Vec2d(2, 2);
        Vec2d dirA = new Vec2d(0, 1); // north curb
        Vec2d dirB = new Vec2d(1, 0); // east curb
        assertEquals(0.0, JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(dirA)), 1e-6);
        assertEquals(1.0, JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(dirB)), 1e-6);
        double mid = JunctionFilletElevationLoft.angularBlend(
            sharp, dirA, dirB, sharp.add(new Vec2d(1, 1).normalize()));
        assertTrue(mid > 0.35 && mid < 0.65, "mid angle should be near 0.5; mid=" + mid);
    }

    @Test
    void reconstructSharpCornerMatchesOrthogonalFillet() {
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, 4.0, Math.PI / 2.0, sweep);
        Vec2d sharp = com.plot.plugin.road.RoadJunctionGenerator.reconstructFilletSharpCorner(
            fillet, sweep);
        assertTrue(sharp != null);
        assertEquals(2.0, sharp.x, 0.15);
        assertEquals(2.0, sharp.y, 0.15);
    }

    @Test
    void nonGoreSurfaceYFollowsPinnedApproachPavementNotFixedJunctionY() {
        RoadNetwork network = orthogonalNetwork();
        RoadEdge ns = edgeToward(network, new Vec2d(0, 40));
        int junctionY = 64;
        int approachY = 70;

        RoadGenerationResult nsResult = new RoadGenerationResult(0);
        BlockPos paved = new BlockPos(0, approachY, 12);
        nsResult.recordPlacementOverride(paved, "minecraft:air", "minecraft:stone");
        nsResult.placementLayers.put(paved, RoadSolidLayer.ROAD);

        Map<String, RoadGenerationResult> edgeResults = new HashMap<>();
        edgeResults.put(ns.getId(), nsResult);
        for (RoadEdge edge : network.getEdges().values()) {
            edgeResults.putIfAbsent(edge.getId(), new RoadGenerationResult(0));
        }

        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y);
                }
            };

        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.build(
            List.of(),
            edgeResults,
            List.copyOf(network.getEdges().values()),
            network,
            columns,
            junctionY);

        Vec2d onApproach = new Vec2d(0.4, 12.4);
        assertEquals(approachY, loft.surfaceY(onApproach),
            "non-gore approach column must pin to existing pavement Y");
        assertNotEquals(junctionY, loft.surfaceY(onApproach),
            "must not fall back to fixed junctionY on graded approach pavement");
    }

    @Test
    void junctionCoreIgnoresLeftoverPavementPins() {
        RoadNetwork network = orthogonalNetwork();
        int junctionY = 64;
        int leftoverY = 72;

        Map<String, RoadGenerationResult> edgeResults = new HashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(0);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = new BlockPos(x, leftoverY, z);
                    result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
                    result.placementLayers.put(pos, RoadSolidLayer.ROAD);
                }
            }
            edgeResults.put(edge.getId(), result);
        }

        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y);
                }
            };

        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.build(
            List.of(),
            edgeResults,
            List.copyOf(network.getEdges().values()),
            network,
            columns,
            junctionY);

        assertEquals(junctionY, loft.surfaceY(new Vec2d(0.4, 0.4)),
            "junction core must not adopt leftover edge ROAD as design pavement Y");
    }

    @Test
    void resolveTangentOwnerBindsEachCurbToNearestApproach() {
        RoadNetwork network = orthogonalNetwork();
        RoadEdge ns = edgeToward(network, new Vec2d(0, 40));
        RoadEdge ew = edgeToward(network, new Vec2d(40, 0));
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);

        RoadEdge ownerNorth = JunctionFilletElevationLoft.resolveTangentOwner(
            new Vec2d(2, 6), List.copyOf(network.getEdges().values()), centerlines);
        RoadEdge ownerEast = JunctionFilletElevationLoft.resolveTangentOwner(
            new Vec2d(6, 2), List.copyOf(network.getEdges().values()), centerlines);

        assertSame(ns, ownerNorth, "north tangent belongs to NS approach");
        assertSame(ew, ownerEast, "east tangent belongs to EW approach");
    }

    @Test
    void mergeApproachRoadYPrefersNearestCenterlineOwnerNotMinY() {
        RoadNetwork network = orthogonalNetwork();
        RoadEdge ns = edgeToward(network, new Vec2d(0, 40));
        RoadEdge ew = edgeToward(network, new Vec2d(40, 0));
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y);
                }
            };

        // 同一世界列：NS 路缘 Y=68，EW 路面 Y=65；列更靠近 NS 中心线时应取 68。
        ColumnKey shared = new ColumnKey(1, 12);
        Map<String, Map<ColumnKey, Integer>> byEdge = Map.of(
            ns.getId(), Map.of(shared, 68),
            ew.getId(), Map.of(shared, 65));

        Map<ColumnKey, Integer> merged = JunctionFilletElevationLoft.mergeApproachRoadY(
            byEdge, List.of(ns, ew), centerlines, columns);
        assertEquals(68, merged.get(shared),
            "shared column must follow nearest approach owner, not Math.min");
    }

    @Test
    void mergeApproachRoadYKeepsLowerBandOnGradeSeparatedStack() {
        RoadNetwork network = orthogonalNetwork();
        RoadEdge ns = edgeToward(network, new Vec2d(0, 40));
        RoadEdge ew = edgeToward(network, new Vec2d(40, 0));
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y);
                }
            };

        // 同列叠层：下层 64、上层桥面 80；即使更靠近上层中心线，也必须留在低层带。
        ColumnKey shared = new ColumnKey(2, 2);
        Map<String, Map<ColumnKey, Integer>> byEdge = Map.of(
            ns.getId(), Map.of(shared, 80),
            ew.getId(), Map.of(shared, 64));

        Map<ColumnKey, Integer> merged = JunctionFilletElevationLoft.mergeApproachRoadY(
            byEdge, List.of(ns, ew), centerlines, columns);
        assertEquals(64, merged.get(shared),
            "grade-separated stack must not pin underpass loft to elevated deck");
    }

    @Test
    void fallbackElevationComparesSampleInWorldSpaceNotCanvas() {
        // 画布缩放 2×：canvas (0,20) → world (0,40)。若误用画布坐标比距，会找不到 Y=73 钉点。
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("scaled");
        road.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadEdge edge = network.createEdge(
            junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());

        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos paved = new BlockPos(0, 73, 40);
        result.recordPlacementOverride(paved, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(paved, RoadSolidLayer.ROAD);

        RoadTerrainClearanceUtils.BlockColumnResolver columns =
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.x * 2.0);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.floor(planPoint.y * 2.0);
                }
            };

        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.build(
            List.of(),
            Map.of(edge.getId(), result),
            List.of(edge),
            network,
            columns,
            64);

        assertEquals(73, loft.surfaceY(new Vec2d(0.2, 20.2)),
            "scaled projection must still pin approach pavement via world-space distance");
    }

    private static RoadNetwork orthogonalNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road nsRoad = network.createRoad("ns");
        nsRoad.setWidth(7);
        Road ewRoad = network.createRoad("ew");
        ewRoad.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), nsRoad.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), ewRoad.getId());
        return network;
    }

    private static RoadEdge edgeToward(RoadNetwork network, Vec2d leaf) {
        return network.getEdges().values().stream()
            .filter(edge -> {
                RoadNode start = network.getNode(edge.getStartNodeId());
                RoadNode end = network.getNode(edge.getEndNodeId());
                return (start != null && start.getPosition().distance(leaf) < 1.0)
                    || (end != null && end.getPosition().distance(leaf) < 1.0);
            })
            .findFirst()
            .orElseThrow();
    }
}
