package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.RetainingEdge;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
            EarthworkGenerator.EarthworkGenerationResult result) {
        if (site == null || world == null || result == null || transformer == null) {
            return;
        }
        Set<Long> placedColumns = new HashSet<>();
        for (RetainingEdge edge : site.getRetainingEdges()) {
            if (edge == null) {
                continue;
            }
            generateWall(edge, world, transformer, result, placedColumns);
        }
    }

    private static void generateWall(
            RetainingEdge edge,
            World world,
            ICoordinateService transformer,
            EarthworkGenerator.EarthworkGenerationResult result,
            Set<Long> placedColumns) {
        List<Vec2d> polyline = edge.getPolyline();
        if (polyline.size() < 2) {
            return;
        }
        int bottom = Math.min(edge.getBottomElevation(), edge.getTopElevation());
        int top = Math.max(edge.getBottomElevation(), edge.getTopElevation());
        if (top < bottom) {
            return;
        }
        String wallBlockId = resolveWallBlockId(edge.getWallMaterial());

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
                for (int y = bottom; y <= top; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    recordWallBlock(result, world, pos, wallBlockId);
                }
            }
        }
    }

    private static void recordWallBlock(
            EarthworkGenerator.EarthworkGenerationResult result,
            World world,
            BlockPos pos,
            String blockId) {
        if (result.placementRecords.containsKey(pos)) {
            return;
        }
        String previous = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        if (!EarthworkGenerator.shouldApplyBlockChange(previous, blockId)) {
            return;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, previous, blockId));
        result.changeTypes.put(pos, EarthworkGenerator.ChangeType.FILL);
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
