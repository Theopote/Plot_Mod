package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路口中心同高、两进口分向上坡/下坡：倒圆放样邻格高差应连续（无单格悬崖）。
 */
class JunctionFilletOpposingGradeContinuityTest {

    @Test
    void opposingGradeCurbsKeepNeighborFilletStepsWithinTwoBlocks() {
        Vec2d center = new Vec2d(6, 6);
        Vec2d tStart = new Vec2d(2, 6);
        Vec2d tEnd = new Vec2d(6, 2);
        double sweep = FilletArcSweep.resolveShort(center, tStart, tEnd);
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            center, tStart, tEnd, 4.0, Math.PI / 2.0, sweep);
        Vec2d sharp = RoadJunctionGenerator.reconstructFilletSharpCorner(fillet, sweep);

        RoadNetwork network = orthogonalApproaches();
        RoadEdge ns = edgeToward(network, new Vec2d(0, 40));
        RoadEdge ew = edgeToward(network, new Vec2d(40, 0));

        // 北向缓升、东向缓降（同中心标高），按进口隔离写入
        RoadGenerationResult nsResult = new RoadGenerationResult(0);
        RoadGenerationResult ewResult = new RoadGenerationResult(0);
        for (int s = 0; s <= 12; s++) {
            putRoad(nsResult, 2, 2 + s, 64 + s / 4);
            putRoad(ewResult, 2 + s, 2, 64 - s / 4);
        }
        Map<String, RoadGenerationResult> edgeResults = new HashMap<>();
        edgeResults.put(ns.getId(), nsResult);
        edgeResults.put(ew.getId(), ewResult);

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
            List.of(fillet),
            edgeResults,
            List.copyOf(network.getEdges().values()),
            network,
            columns,
            64);

        List<int[]> goreCells = new ArrayList<>();
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                Vec2d p = new Vec2d(x + 0.5, z + 0.5);
                if (!loft.isFilletGore(p)) {
                    continue;
                }
                goreCells.add(new int[]{x, z, loft.surfaceY(p)});
            }
        }
        assertTrue(goreCells.size() >= 3, "need fillet gore samples; n=" + goreCells.size());

        int maxNeighborJump = 0;
        for (int[] a : goreCells) {
            for (int[] b : goreCells) {
                if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) != 1) {
                    continue;
                }
                maxNeighborJump = Math.max(maxNeighborJump, Math.abs(a[2] - b[2]));
            }
        }
        assertTrue(maxNeighborJump <= 2,
            "opposing-grade fillet neighbors must stay continuous; jump=" + maxNeighborJump
                + " samples=" + goreCells.stream()
                    .sorted(Comparator.comparingInt((int[] c) -> c[0]).thenComparingInt(c -> c[1]))
                    .map(c -> "(" + c[0] + "," + c[1] + ")=" + c[2])
                    .toList());

        // 角向两端应分别偏向北缘升坡与东缘降坡，并与路缘采样衔接
        int yNearNorth = loft.surfaceY(new Vec2d(2.3, 4.0));
        int yNearEast = loft.surfaceY(new Vec2d(4.0, 2.3));
        assertTrue(yNearNorth >= 64 && yNearEast <= 64,
            "north-side loft ≥ junctionY, east-side ≤ junctionY; n=" + yNearNorth + " e=" + yNearEast);
        assertTrue(Math.abs(yNearNorth - 64) <= 2, "north curb seam; y=" + yNearNorth);
        assertTrue(Math.abs(yNearEast - 64) <= 2, "east curb seam; y=" + yNearEast);
        assertTrue(sharp != null);
    }

    private static RoadNetwork orthogonalApproaches() {
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

    private static void putRoad(RoadGenerationResult result, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
    }
}
