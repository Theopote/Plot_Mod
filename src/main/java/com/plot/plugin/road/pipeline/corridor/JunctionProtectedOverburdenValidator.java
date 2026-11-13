package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 硬验收：受保护洞腔列的天然覆土不得被非授权空气打穿；
 * 实际路面标高须与权威高程场一致；合法衬砌不得丢失。
 */
public final class JunctionProtectedOverburdenValidator {
    private JunctionProtectedOverburdenValidator() {
    }

    public record Metrics(
            int protectedOverburdenAirCount,
            int unexpectedSurfaceElevationCount,
            int missingAuthorizedLiningCount) {
    }

    /**
     * 合并后修复：封闭洞顶以上的非法空气落点一律撤销，保留天然覆土。
     * 走廊/边坡清障常把空气写进路口保护列覆土层，若不剔除会既打穿地表又误拦建造。
     *
     * @return 撤销的空气格数
     */
    public static int stripUnauthorizedOverburdenAir(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadGenerationResult aggregate) {
        if (blocks == null || aggregate == null || aggregate.placementRecords == null) {
            return 0;
        }
        JunctionConstructionContext context = blocks.constructionContext();
        if (context == null) {
            return 0;
        }
        int stripped = 0;
        for (ColumnKey column : context.protectedCavityColumns()) {
            if (column == null) {
                continue;
            }
            JunctionConstructionContext.ColumnRule rule = context.rule(column);
            if (rule == null
                    || rule.role() != JunctionConstructionContext.ColumnRole.ENCLOSED_TUNNEL) {
                continue;
            }
            Integer roofY = rule.cavityRoofY();
            if (roofY == null) {
                continue;
            }
            int surfaceY = rule.naturalSurfaceY();
            for (int y = roofY + 1; y <= surfaceY; y++) {
                BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                BlockRecord record = aggregate.placementRecords.get(pos);
                if (record == null || !"minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                aggregate.clearPlacement(pos);
                stripped++;
            }
        }
        return stripped;
    }

    public static Metrics evaluate(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadGenerationResult aggregate,
            TerrainSampler terrain) {
        if (blocks == null) {
            return new Metrics(0, 0, 0);
        }
        JunctionConstructionContext context = blocks.constructionContext();
        JunctionElevationField field = blocks.elevationField();
        int overburdenAir = 0;
        int elevationMismatch = 0;
        int missingLining = 0;

        // 一次索引路口 solids，避免对每个 (column,y) 全表扫描。
        ColumnSolidIndex solidIndex = ColumnSolidIndex.from(blocks);

        if (context != null && terrain != null) {
            for (ColumnKey column : context.protectedCavityColumns()) {
                if (column == null) {
                    continue;
                }
                JunctionConstructionContext.ColumnRule rule = context.rule(column);
                // 仅封闭洞腔列要求天然覆土完整；洞口/过渡列允许浅覆或露天，不计穿孔。
                if (rule != null
                        && rule.role() != JunctionConstructionContext.ColumnRole.ENCLOSED_TUNNEL) {
                    continue;
                }
                Integer roofY = rule != null ? rule.cavityRoofY() : null;
                int surfaceY = rule != null
                    ? rule.naturalSurfaceY()
                    : terrain.sampleSurfaceY(new Vec2d(column.worldX() + 0.5, column.worldZ() + 0.5));
                if (roofY == null) {
                    continue;
                }
                // 洞顶衬砌格在 cavityRoofY；覆土穿孔只计衬砌顶以上到自然地表。
                for (int y = roofY + 1; y <= surfaceY; y++) {
                    if (isUnauthorizedAir(aggregate, solidIndex, column, y)) {
                        overburdenAir++;
                    }
                }
            }
        }

        if (field != null && field.targetYByColumn() != null) {
            for (var entry : field.targetYByColumn().entrySet()) {
                ColumnKey column = entry.getKey();
                Integer expectedY = entry.getValue();
                if (column == null || expectedY == null) {
                    continue;
                }
                if (!hasPavementAt(aggregate, solidIndex, column, expectedY)) {
                    if (hasAnyPavementInColumn(aggregate, solidIndex, column)) {
                        elevationMismatch++;
                    }
                }
            }
        }

        // 衬砌缺失只统计本路口范围内的期望格，禁止把全网 expectedTunnelLining
        // 重复算进每个路口（否则每个路口 missingLining 相同并误阻断建造）。
        missingLining = countMissingLiningInJunctionScope(blocks, aggregate);

        return new Metrics(overburdenAir, elevationMismatch, missingLining);
    }

    /**
     * 范围 = 保护洞腔列 ∪ 校验清单 expectedProtectedBlocks 所在列。
     * <p>
     * 不计 {@link RoadGenerationResult#expectedTunnelCavity}：连通清障把走廊衬砌合法打开为洞腔后，
     * 原 expectedTunnelLining 仍可能残留，不得当成「授权衬砌丢失」拦建造。
     */
    static int countMissingLiningInJunctionScope(
            RoadJunctionGenerator.JunctionBlocks blocks,
            RoadGenerationResult aggregate) {
        if (blocks == null || aggregate == null || aggregate.expectedTunnelLining == null
                || aggregate.expectedTunnelLining.isEmpty()) {
            return 0;
        }
        Set<Long> scopedColumns = new HashSet<>();
        JunctionConstructionContext context = blocks.constructionContext();
        if (context != null) {
            for (ColumnKey column : context.protectedCavityColumns()) {
                if (column != null) {
                    scopedColumns.add(columnKey(column.worldX(), column.worldZ()));
                }
            }
        }
        JunctionValidationManifest manifest = blocks.validationManifest();
        if (manifest != null && manifest.expectedProtectedBlocks() != null) {
            for (BlockPos pos : manifest.expectedProtectedBlocks()) {
                if (pos != null) {
                    scopedColumns.add(columnKey(pos.getX(), pos.getZ()));
                }
            }
        }
        if (scopedColumns.isEmpty()) {
            return 0;
        }
        Set<BlockPos> authorizedCavity = aggregate.expectedTunnelCavity != null
            ? aggregate.expectedTunnelCavity
            : Set.of();
        int missing = 0;
        for (BlockPos pos : aggregate.expectedTunnelLining) {
            if (pos == null || !scopedColumns.contains(columnKey(pos.getX(), pos.getZ()))) {
                continue;
            }
            if (authorizedCavity.contains(pos)) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record == null) {
                missing++;
                continue;
            }
            if (!"minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            // 隧道层空气 = 洞腔/连通清障，不是「授权衬砌丢失」。
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (layer == RoadSolidLayer.TUNNEL) {
                continue;
            }
            missing++;
        }
        return missing;
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static boolean isUnauthorizedAir(
            RoadGenerationResult aggregate,
            ColumnSolidIndex solidIndex,
            ColumnKey column,
            int y) {
        BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
        if (aggregate != null && aggregate.placementRecords != null) {
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                return true;
            }
            // 合成结果未落格 = 保留天然覆土，不算生成器打穿；勿回退路口 solids 误报。
            return false;
        }
        return solidIndex != null && solidIndex.hasAir(column, y);
    }

    private static boolean hasPavementAt(
            RoadGenerationResult aggregate,
            ColumnSolidIndex solidIndex,
            ColumnKey column,
            int y) {
        BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
        if (aggregate != null && aggregate.placementRecords != null) {
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && !"minecraft:air".equals(record.newBlockId)) {
                var layer = aggregate.placementLayers.get(pos);
                if (layer == null || JunctionSeamReconciliationPass.isStackedPavementLayer(layer)) {
                    return true;
                }
            }
        }
        return solidIndex != null && solidIndex.hasPavement(column, y);
    }

    private static boolean hasAnyPavementInColumn(
            RoadGenerationResult aggregate,
            ColumnSolidIndex solidIndex,
            ColumnKey column) {
        if (aggregate != null && aggregate.placementLayers != null) {
            // 仅扫该世界列：用 xz 哈希桶，避免整表线性扫描。
            for (var entry : aggregate.placementLayers.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos == null || pos.getX() != column.worldX() || pos.getZ() != column.worldZ()) {
                    continue;
                }
                if (JunctionSeamReconciliationPass.isStackedPavementLayer(entry.getValue())) {
                    BlockRecord record = aggregate.placementRecords.get(pos);
                    if (record != null && !"minecraft:air".equals(record.newBlockId)) {
                        return true;
                    }
                }
            }
        }
        return solidIndex != null && solidIndex.hasAnyPavement(column);
    }

    /** 路口 solids 按世界列索引，供硬验收 O(列×高度) 查询。 */
    private record ColumnSolidIndex(
            Map<Long, Set<Integer>> airYs,
            Map<Long, Set<Integer>> pavementYs) {

        static ColumnSolidIndex from(RoadJunctionGenerator.JunctionBlocks blocks) {
            Map<Long, Set<Integer>> air = new HashMap<>();
            Map<Long, Set<Integer>> pavement = new HashMap<>();
            if (blocks == null || blocks.getSolids() == null) {
                return new ColumnSolidIndex(air, pavement);
            }
            for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
                if (p == null || p.planPoint() == null) {
                    continue;
                }
                int wx = (int) Math.floor(p.planPoint().x);
                int wz = (int) Math.floor(p.planPoint().y);
                long key = columnKey(wx, wz);
                if ("minecraft:air".equals(p.materialId())) {
                    air.computeIfAbsent(key, ignored -> new HashSet<>()).add(p.elevation());
                    continue;
                }
                RoadSolidLayer layer = p.layer();
                if (layer == null || JunctionSeamReconciliationPass.isStackedPavementLayer(layer)) {
                    pavement.computeIfAbsent(key, ignored -> new HashSet<>()).add(p.elevation());
                }
            }
            return new ColumnSolidIndex(air, pavement);
        }

        boolean hasAir(ColumnKey column, int y) {
            Set<Integer> ys = airYs.get(columnKey(column.worldX(), column.worldZ()));
            return ys != null && ys.contains(y);
        }

        boolean hasPavement(ColumnKey column, int y) {
            Set<Integer> ys = pavementYs.get(columnKey(column.worldX(), column.worldZ()));
            return ys != null && ys.contains(y);
        }

        boolean hasAnyPavement(ColumnKey column) {
            Set<Integer> ys = pavementYs.get(columnKey(column.worldX(), column.worldZ()));
            return ys != null && !ys.isEmpty();
        }

        private static long columnKey(int x, int z) {
            return JunctionProtectedOverburdenValidator.columnKey(x, z);
        }
    }
}
