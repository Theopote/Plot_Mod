package com.plot.plugin.earthwork.voxel;

import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.geometry.ZoneBoundaryRetainingEdgeAdapter;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.RetainingEdge;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.PlacementMeta;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 沿挡土界折线放置直立墙方块。
 * <p>
 * 共享 X/Z 列的多段墙：合并高度区间（取并集），材质保留先到达该列的墙（确定性）；
 * 仅补写尚未覆盖的 Y 层，避免整列跳过导致丢高度。
 */
public final class RetainingWallGenerator {
    public static final String DEFAULT_WALL_BLOCK = "minecraft:stone_bricks";

    private RetainingWallGenerator() {
    }

    public static void generate(
            EarthworkSite site,
            World world,
            ICoordinateService transformer,
            EarthworkGenerationResult result) {
        generate(site, world, transformer, result, null, Map.of());
    }

    public static void generate(
            EarthworkSite site,
            World world,
            ICoordinateService transformer,
            EarthworkGenerationResult result,
            DesignTerrainGrid grid,
            Map<String, DesignSurfaceResolver.ZoneTargetEvaluator> evaluators) {
        if (site == null || world == null || result == null || transformer == null) {
            return;
        }
        EarthworkCanvasScale canvasScale = EarthworkCanvasScale.capture(transformer, site.getSiteBoundary());
        List<RetainingEdge> edges = new ArrayList<>(site.getRetainingEdges());
        edges.addAll(ZoneBoundaryRetainingEdgeAdapter.deriveVirtualEdges(site, grid, evaluators, canvasScale));
        Map<Long, ColumnPlan> columnPlans = new HashMap<>();
        for (RetainingEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            generateWall(edge, site, world, transformer, result, columnPlans, grid, canvasScale);
        }
    }

    private static void generateWall(
            RetainingEdge edge,
            EarthworkSite site,
            World world,
            ICoordinateService transformer,
            EarthworkGenerationResult result,
            Map<Long, ColumnPlan> columnPlans,
            DesignTerrainGrid grid,
            EarthworkCanvasScale canvasScale) {
        List<Vec2d> polyline = edge.getPolyline();
        if (polyline.size() < 2) {
            return;
        }
        int fallbackBottom = Math.min(edge.getBottomElevation(), edge.getTopElevation());
        int fallbackTop = Math.max(edge.getBottomElevation(), edge.getTopElevation());
        String wallBlockId = resolveWallBlockId(edge, site);
        PlacementMeta meta = PlacementMeta.ofZone(edge.getLinkedZoneId());

        for (int segmentIndex = 0; segmentIndex < polyline.size() - 1; segmentIndex++) {
            Vec2d start = polyline.get(segmentIndex);
            Vec2d end = polyline.get(segmentIndex + 1);
            if (start == null || end == null) {
                continue;
            }
            int steps = canvasScale.stepsAlongSegment(start, end);
            for (int step = 0; step <= steps; step++) {
                double ratio = step / (double) steps;
                Vec2d canvasPoint = start.lerp(end, ratio);
                BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(canvasPoint, transformer);
                long columnKey = columnKey(column.getX(), column.getZ());
                int bottom = fallbackBottom;
                int top = fallbackTop;
                if (grid != null) {
                    DesignTerrainCell cell = grid.get(column.getX(), column.getZ());
                    if (cell != null) {
                        bottom = Math.min(cell.existingGroundY(), cell.targetY());
                        top = Math.max(cell.existingGroundY(), cell.targetY());
                    }
                }
                if (top < bottom) {
                    continue;
                }
                placeOrMergeColumn(
                    result, world, columnPlans, columnKey, column.getX(), column.getZ(),
                    bottom, top, wallBlockId, meta);
            }
        }
    }

    /**
     * 同列合并：高度取并集；材质保留先到达者；仅写入尚未覆盖的 Y。
     */
    static void placeOrMergeColumn(
            EarthworkGenerationResult result,
            World world,
            Map<Long, ColumnPlan> columnPlans,
            long columnKey,
            int worldX,
            int worldZ,
            int bottom,
            int top,
            String wallBlockId,
            PlacementMeta meta) {
        ColumnPlan existing = columnPlans.get(columnKey);
        if (existing == null) {
            ColumnPlan plan = new ColumnPlan(bottom, top, wallBlockId, meta);
            columnPlans.put(columnKey, plan);
            for (int y = bottom; y <= top; y++) {
                recordWallBlock(result, world, new BlockPos(worldX, y, worldZ), wallBlockId, meta);
            }
            return;
        }
        int newBottom = Math.min(existing.bottom, bottom);
        int newTop = Math.max(existing.top, top);
        // 材质：先到达者胜（确定性，不依赖后续墙材质）
        String blockId = existing.blockId;
        PlacementMeta keepMeta = existing.meta;
        for (int y = newBottom; y <= newTop; y++) {
            if (y >= existing.bottom && y <= existing.top) {
                continue;
            }
            recordWallBlock(result, world, new BlockPos(worldX, y, worldZ), blockId, keepMeta);
        }
        existing.bottom = newBottom;
        existing.top = newTop;
    }

    private static void recordWallBlock(
            EarthworkGenerationResult result,
            World world,
            BlockPos pos,
            String blockId,
            PlacementMeta meta) {
        String previous = "minecraft:air";
        if (world != null) {
            previous = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        }
        EarthworkPlacementMerge.recordOrReplace(
            result, pos, previous, blockId, EarthworkGenerationResult.ChangeType.STRUCTURE, meta);
    }

    private static String resolveWallBlockId(RetainingEdge edge, EarthworkSite site) {
        if (edge != null && edge.isUseLinkedZoneFillMaterial() && site != null) {
            String linkedZoneId = edge.getLinkedZoneId();
            if (linkedZoneId != null && !linkedZoneId.isBlank()) {
                var zone = site.getZone(linkedZoneId);
                if (zone != null) {
                    String fillMaterial = EarthworkGeometryUtils.resolveFillBlockId(zone.getFillMaterial());
                    if (fillMaterial != null && !fillMaterial.isBlank()) {
                        return fillMaterial;
                    }
                }
            }
        }
        return resolveWallBlockId(edge != null ? edge.getWallMaterial() : "");
    }

    private static String resolveWallBlockId(String material) {
        if (material == null || material.isBlank()) {
            return DEFAULT_WALL_BLOCK;
        }
        String trimmed = material.trim().toLowerCase(Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private static long columnKey(int worldX, int worldZ) {
        return ((long) worldX << 32) | (worldZ & 0xFFFFFFFFL);
    }

    /** 单测可见：列合并状态。 */
    static final class ColumnPlan {
        int bottom;
        int top;
        final String blockId;
        final PlacementMeta meta;

        ColumnPlan(int bottom, int top, String blockId, PlacementMeta meta) {
            this.bottom = bottom;
            this.top = top;
            this.blockId = blockId;
            this.meta = meta != null ? meta : PlacementMeta.UNASSIGNED;
        }
    }
}
