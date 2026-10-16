package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clears terrain, decorations, and leftover edge solids above a junction road surface.
 */
public final class JunctionTerrainClearance {
    private JunctionTerrainClearance() {
    }

    /**
     * Highest solid leftover Y in each world column from already-built edge results.
     * Air placements are ignored.
     */
    public static Map<ColumnKey, Integer> leftoverTopByColumn(Map<String, RoadGenerationResult> edgeResults) {
        Map<ColumnKey, Integer> tops = new HashMap<>();
        if (edgeResults == null) {
            return tops;
        }
        for (RoadGenerationResult result : edgeResults.values()) {
            if (result == null || result.placementRecords == null) {
                continue;
            }
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (pos == null || record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                ColumnKey key = new ColumnKey(pos.getX(), pos.getZ());
                tops.merge(key, pos.getY(), Math::max);
            }
        }
        return tops;
    }

    public static void clearOverheadInPolygon(
            RoadSolidModel solids,
            List<Vec2d> polygon,
            int junctionY,
            int tunnelThreshold,
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            Vec2d planPoint) {
        clearOverheadInPolygon(
            solids, polygon, junctionY, tunnelThreshold, terrain, worldX, worldZ, planPoint, null, junctionY);
    }

    public static void clearOverheadInPolygon(
            RoadSolidModel solids,
            List<Vec2d> polygon,
            int cellY,
            int tunnelThreshold,
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            Vec2d planPoint,
            Integer elevatedDeckY,
            int leftoverTopY) {
        if (solids == null || polygon == null || polygon.size() < 3 || planPoint == null || terrain == null) {
            return;
        }
        if (!RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
            return;
        }
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        int surfaceY = terrain.sampleSurfaceY(planPoint);
        int topY = Math.max(surfaceY, leftoverTopY);
        if (elevatedDeckY != null) {
            topY = Math.min(topY, elevatedDeckY - 1);
        }
        punchColumn(solids, planPoint, cellY, topY);
    }

    static void punchColumn(RoadSolidModel solids, Vec2d planPoint, int cellY, int topY) {
        if (solids == null || planPoint == null || topY <= cellY) {
            return;
        }
        for (int y = cellY + 1; y <= topY; y++) {
            solids.add(
                planPoint,
                y,
                RoadSolidLayer.TUNNEL,
                "minecraft:air",
                RoadSolidMaterialPriority.CLEARANCE);
        }
    }
}
