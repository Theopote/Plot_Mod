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
    /**
     * 上层路面下方保留的承重层厚度（格）。净空清障不得触及该层。
     * 与 {@link com.plot.plugin.road.pipeline.crosssection.RoadCrossSectionBuilder} 桥面板
     * {@code targetY - 1} 一致。
     */
    public static final int ELEVATED_DECK_SUPPORT_DEPTH = 1;

    /** 上层路面承重层默认方块（与桥面板 underlay 一致）。 */
    public static final String ELEVATED_DECK_SUPPORT_BLOCK = "minecraft:stone_bricks";

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
        // Engineering surface alone misses trees/tall solids above the load-bearing ground;
        // column top + leftover edge solids are required for a clean at-grade plaza.
        int surfaceY = terrain.sampleSurfaceY(planPoint);
        int columnTopY = terrain.sampleColumnTopY(planPoint);
        int topY = Math.max(leftoverTopY, Math.max(surfaceY, columnTopY));
        if (elevatedDeckY != null) {
            // elevatedDeckY 是上层路面；保留其下连续 DECK_SUPPORT_DEPTH 格承重层，
            // 净空只清到 supportBottom - 1。
            topY = Math.min(topY, elevatedDeckY - ELEVATED_DECK_SUPPORT_DEPTH - 1);
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
