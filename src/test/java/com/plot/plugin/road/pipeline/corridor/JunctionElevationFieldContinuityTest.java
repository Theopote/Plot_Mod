package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异纵坡交叉：局部高程场不得出现相对邻域中位数孤立 ≥2 的突起/下沉。
 */
class JunctionElevationFieldContinuityTest {

    @Test
    void opposingGradeFilletHasNoIsolatedSpikes() {
        RoadNetwork network = new RoadNetwork();
        Road ns = network.createRoad("ns");
        ns.setWidth(7);
        Road ew = network.createRoad("ew");
        ew.setWidth(7);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        RoadEdge nsEdge = network.createEdge(
            junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), ns.getId());
        RoadEdge ewEdge = network.createEdge(
            junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), ew.getId());

        Map<String, RoadGenerationResult> edgeResults = new HashMap<>();
        RoadGenerationResult nsResult = new RoadGenerationResult(0);
        RoadGenerationResult ewResult = new RoadGenerationResult(0);
        for (int z = 8; z <= 20; z++) {
            for (int x = -3; x <= 3; x++) {
                plant(nsResult, x, 64, z);
            }
        }
        for (int x = 8; x <= 20; x++) {
            for (int z = -3; z <= 3; z++) {
                plant(ewResult, x, 70, z);
            }
        }
        edgeResults.put(nsEdge.getId(), nsResult);
        edgeResults.put(ewEdge.getId(), ewResult);

        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(3, 8),
            new Vec2d(8, 3),
            8.0,
            Math.PI / 2.0,
            FilletArcSweep.resolveShort(new Vec2d(8, 8), new Vec2d(3, 8), new Vec2d(8, 3)));
        JunctionGeometryResult geometry = new JunctionGeometryResult(
            List.of(new Vec2d(-6, -6), new Vec2d(20, -6), new Vec2d(20, 20), new Vec2d(-6, 20)),
            List.of(new Vec2d(-4, -4), new Vec2d(16, -4), new Vec2d(16, 16), new Vec2d(-4, 16)),
            List.of(),
            8.0,
            8.0,
            List.of(fillet),
            List.of(new Vec2d(-2, -2), new Vec2d(10, -2), new Vec2d(10, 10), new Vec2d(-2, 10)),
            List.of());

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
        JunctionWorldFootprint footprint = JunctionWorldFootprint.from(geometry, null);
        JunctionFilletElevationLoft loft = JunctionFilletElevationLoft.build(
            List.of(fillet), edgeResults, List.of(nsEdge, ewEdge), network, columns, 67);
        JunctionElevationField field = JunctionElevationField.build(footprint, loft, columns, 67);

        assertTrue(field.targetYByColumn().size() >= 8, "field must cover pave columns");

        // 直接验证钳制器：人造孤立突起必须被压回。
        Map<ColumnKey, Integer> spike = new HashMap<>();
        spike.put(new ColumnKey(0, 0), 70);
        spike.put(new ColumnKey(1, 0), 64);
        spike.put(new ColumnKey(-1, 0), 64);
        spike.put(new ColumnKey(0, 1), 64);
        spike.put(new ColumnKey(0, -1), 64);
        assertTrue(JunctionElevationField.clampIsolatedSpikes(spike) > 0);
        assertTrue(Math.abs(spike.get(new ColumnKey(0, 0)) - 64) <= 1);

        Map<ColumnKey, Integer> step = new HashMap<>();
        step.put(new ColumnKey(0, 0), 70);
        step.put(new ColumnKey(1, 0), 64);
        step.put(new ColumnKey(0, 1), 64);
        java.util.Set<ColumnKey> soft = java.util.Set.of(new ColumnKey(0, 0));
        assertTrue(JunctionElevationField.enforceAdjacentStepLimit(step, soft) > 0);
        for (int pass = 0; pass < 12; pass++) {
            if (JunctionElevationField.enforceAdjacentStepLimit(step, soft) == 0) {
                break;
            }
        }
        assertTrue(Math.abs(step.get(new ColumnKey(0, 0)) - 64) <= 1);

        Map<ColumnKey, Integer> checker = new HashMap<>();
        checker.put(new ColumnKey(0, 0), 64);
        checker.put(new ColumnKey(1, 0), 65);
        checker.put(new ColumnKey(0, 1), 65);
        checker.put(new ColumnKey(1, 1), 64);
        java.util.Set<ColumnKey> filletKeys = java.util.Set.of(
            new ColumnKey(0, 0), new ColumnKey(1, 0), new ColumnKey(0, 1), new ColumnKey(1, 1));
        assertTrue(JunctionElevationField.relaxFilletTowardNeighbors(checker, filletKeys) > 0);

        Map<ColumnKey, Integer> checkerboard = new HashMap<>();
        checkerboard.put(new ColumnKey(0, 0), 64);
        checkerboard.put(new ColumnKey(1, 0), 65);
        checkerboard.put(new ColumnKey(0, 1), 65);
        checkerboard.put(new ColumnKey(1, 1), 64);
        assertTrue(JunctionElevationField.flattenFilletCheckerboard(checkerboard, filletKeys) > 0);
        int flat = checkerboard.get(new ColumnKey(0, 0));
        assertTrue(
            checkerboard.get(new ColumnKey(1, 0)) == flat
                && checkerboard.get(new ColumnKey(0, 1)) == flat
                && checkerboard.get(new ColumnKey(1, 1)) == flat,
            "2x2 fillet checkerboard must collapse to one elevation");

        List<Integer> curb = new java.util.ArrayList<>(List.of(67, 70, 64, 64, 71));
        JunctionFilletElevationLoft.smoothCurbElevations(curb);
        for (int i = 1; i < curb.size(); i++) {
            assertTrue(Math.abs(curb.get(i) - curb.get(i - 1)) <= 1,
                "curb samples must be step-continuous after smooth");
        }

        Map<ColumnKey, Integer> seam = new HashMap<>();
        seam.put(new ColumnKey(1, 0), 70);
        Map<ColumnKey, Integer> anchors = Map.of(new ColumnKey(0, 0), 64);
        java.util.Set<ColumnKey> softSeam = java.util.Set.of(new ColumnKey(1, 0));
        for (int pass = 0; pass < 12; pass++) {
            if (JunctionElevationField.alignSoftColumnsToApproachSeam(seam, softSeam, anchors) == 0) {
                break;
            }
        }
        assertTrue(Math.abs(seam.get(new ColumnKey(1, 0)) - 64) <= 1,
            "fillet/seam column must step toward adjacent graded approach anchor");

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (Map.Entry<ColumnKey, Integer> entry : field.targetYByColumn().entrySet()) {
            ColumnKey key = entry.getKey();
            int y = entry.getValue();
            JunctionWorldFootprint.ElevationRole role = field.role(key);
            java.util.ArrayList<Integer> neighbors = new java.util.ArrayList<>();
            for (int[] dir : dirs) {
                Integer ny = field.targetYByColumn().get(
                    new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]));
                if (ny != null) {
                    neighbors.add(ny);
                }
            }
            if (neighbors.size() < 3) {
                continue;
            }
            boolean isolatedHigh = neighbors.stream().allMatch(n -> y >= n + 2);
            boolean isolatedLow = neighbors.stream().allMatch(n -> y <= n - 2);
            assertTrue(!isolatedHigh && !isolatedLow,
                "isolated spike/dip at " + key + " y=" + y + " neighbors=" + neighbors);
            if (role == JunctionWorldFootprint.ElevationRole.FILLET
                    || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
                for (int[] dir : dirs) {
                    ColumnKey nk = new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]);
                    Integer ny = field.targetYByColumn().get(nk);
                    if (ny == null) {
                        continue;
                    }
                    JunctionWorldFootprint.ElevationRole nr = field.role(nk);
                    if (nr != JunctionWorldFootprint.ElevationRole.FILLET
                            && nr != JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
                        continue;
                    }
                    assertTrue(Math.abs(y - ny) <= 1,
                        "soft-soft step >1 at " + key + " y=" + y + " neighbor=" + nk + " ny=" + ny);
                }
                Integer eastY = field.targetYByColumn().get(new ColumnKey(key.worldX() + 1, key.worldZ()));
                Integer northY = field.targetYByColumn().get(new ColumnKey(key.worldX(), key.worldZ() + 1));
                Integer northEastY = field.targetYByColumn().get(
                    new ColumnKey(key.worldX() + 1, key.worldZ() + 1));
                if (eastY != null && northY != null && northEastY != null
                        && field.role(new ColumnKey(key.worldX() + 1, key.worldZ())) == role
                        && field.role(new ColumnKey(key.worldX(), key.worldZ() + 1)) == role
                        && field.role(new ColumnKey(key.worldX() + 1, key.worldZ() + 1)) == role) {
                    boolean isChecker = y == northEastY && eastY.equals(northY)
                        && y != eastY && Math.abs(y - eastY) == 1;
                    assertTrue(!isChecker,
                        "fillet 2x2 checkerboard at " + key
                            + " y=" + y + "/" + eastY + "/" + northY + "/" + northEastY);
                }
            }
        }
    }

    private static void plant(RoadGenerationResult result, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
    }
}
