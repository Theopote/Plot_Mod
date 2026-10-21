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
import java.util.TreeMap;

/**
 * 沿挡土界折线放置直立墙方块。
 * <p>
 * 共享 X/Z 列的多段墙按<strong>逐层 Y</strong>认领，而非 min/max 包围盒：
 * <ul>
 *   <li>不连续区间保留中间空隙，不会填入设计中不存在的方块</li>
 *   <li>同一 Y 被多段占用时：先到达者保留材质与 {@link PlacementMeta}（确定性）</li>
 *   <li>后到达段只写入尚未认领的 Y</li>
 * </ul>
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
     * 同列按 Y 层认领：只写入尚未占用的高度；不连续区间不填空隙。
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
        ColumnPlan plan = columnPlans.computeIfAbsent(columnKey, ignored -> new ColumnPlan());
        PlacementMeta safeMeta = meta != null ? meta : PlacementMeta.UNASSIGNED;
        String blockId = wallBlockId != null ? wallBlockId : DEFAULT_WALL_BLOCK;
        for (int y = bottom; y <= top; y++) {
            if (plan.occupied.containsKey(y)) {
                continue;
            }
            plan.occupied.put(y, new OccupiedY(blockId, safeMeta));
            recordWallBlock(result, world, new BlockPos(worldX, y, worldZ), blockId, safeMeta);
        }
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

    /** 单测可见：列内已占用的 Y → 材质/归属。 */
    static final class ColumnPlan {
        final Map<Integer, OccupiedY> occupied = new TreeMap<>();
    }

    record OccupiedY(String blockId, PlacementMeta meta) {
    }
}
