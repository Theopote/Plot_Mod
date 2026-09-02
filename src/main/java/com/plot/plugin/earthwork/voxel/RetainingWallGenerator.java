package com.plot.plugin.earthwork.voxel;
import com.plot.plugin.earthwork.design.DesignSurfaceResolver;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.geometry.ZoneBoundaryRetainingEdgeAdapter;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.RetainingEdge;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 沿挡土界折线放置直立墙方块。
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
        List<RetainingEdge> edges = new ArrayList<>(site.getRetainingEdges());
        edges.addAll(ZoneBoundaryRetainingEdgeAdapter.deriveVirtualEdges(site, grid, evaluators));
        Set<Long> placedColumns = new HashSet<>();
        for (RetainingEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            generateWall(edge, site, world, transformer, result, placedColumns, grid);
        }
    }

    private static void generateWall(
            RetainingEdge edge,
            EarthworkSite site,
            World world,
            ICoordinateService transformer,
            EarthworkGenerationResult result,
            Set<Long> placedColumns,
            DesignTerrainGrid grid) {
        List<Vec2d> polyline = edge.getPolyline();
        if (polyline.size() < 2) {
            return;
        }
        int fallbackBottom = Math.min(edge.getBottomElevation(), edge.getTopElevation());
        int fallbackTop = Math.max(edge.getBottomElevation(), edge.getTopElevation());
        String wallBlockId = resolveWallBlockId(edge, site);

        for (int segmentIndex = 0; segmentIndex < polyline.size() - 1; segmentIndex++) {
            Vec2d start = polyline.get(segmentIndex);
            Vec2d end = polyline.get(segmentIndex + 1);
            if (start == null || end == null) {
                continue;
            }
            double segmentLength = start.distance(end);
            int steps = Math.max(1, (int) Math.ceil(segmentLength));
            for (int step = 0; step <= steps; step++) {
                double ratio = step / (double) steps;
                Vec2d canvasPoint = start.lerp(end, ratio);
                BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(canvasPoint, transformer);
                long columnKey = columnKey(column.getX(), column.getZ());
                if (!placedColumns.add(columnKey)) {
                    continue;
                }
                int bottom = fallbackBottom;
                int top = fallbackTop;
                if (grid != null) {
                    DesignTerrainCell cell = grid.get(column.getX(), column.getZ());
                    if (cell != null) {
                        bottom = Math.min(cell.existingGroundY(), cell.targetY());
                        top = Math.max(cell.existingGroundY(), cell.targetY());
                    }
                }
                if (top <= bottom) {
                    continue;
                }
                for (int y = bottom; y <= top; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    recordWallBlock(result, world, pos, wallBlockId);
                }
            }
        }
    }

    private static void recordWallBlock(
            EarthworkGenerationResult result,
            World world,
            BlockPos pos,
            String blockId) {
        if (result.placementRecords.containsKey(pos)) {
            return;
        }
        String previous = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        if (!EarthworkVoxelizer.shouldApplyBlockChange(previous, blockId)) {
            return;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, previous, blockId));
        result.changeTypes.put(pos, EarthworkGenerationResult.ChangeType.FILL);
    }

    private static String resolveWallBlockId(RetainingEdge edge, EarthworkSite site) {
        if (edge != null && edge.isUseLinkedZoneFillMaterial() && site != null) {
            String linkedZoneId = edge.getLinkedZoneId();
            if (!linkedZoneId.isBlank()) {
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
}
