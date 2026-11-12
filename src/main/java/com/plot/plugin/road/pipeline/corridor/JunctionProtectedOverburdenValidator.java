package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import net.minecraft.util.math.BlockPos;

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

        if (context != null && terrain != null) {
            for (ColumnKey column : context.protectedCavityColumns()) {
                if (column == null) {
                    continue;
                }
                JunctionConstructionContext.ColumnRule rule = context.rule(column);
                int designY = rule != null ? rule.designRoadY() : field.surfaceY(column);
                Integer roofY = rule != null ? rule.cavityRoofY() : null;
                int surfaceY = rule != null
                    ? rule.naturalSurfaceY()
                    : terrain.sampleSurfaceY(new Vec2d(column.worldX() + 0.5, column.worldZ() + 0.5));
                if (roofY == null) {
                    continue;
                }
                // 洞顶以上到自然地表：不得有非授权 AIR（最终合成优先，否则看路口 solids）。
                for (int y = roofY; y <= surfaceY; y++) {
                    if (isUnauthorizedAir(aggregate, blocks, column, y)) {
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
                if (!hasPavementAt(aggregate, blocks, column, expectedY)) {
                    // 仅统计有路面记录却错层的列；完全无路面可能是未铺装责任列。
                    if (hasAnyPavementInColumn(aggregate, blocks, column)) {
                        elevationMismatch++;
                    }
                }
            }
        }

        if (aggregate != null && aggregate.expectedTunnelLining != null) {
            for (BlockPos pos : aggregate.expectedTunnelLining) {
                if (pos == null) {
                    continue;
                }
                BlockRecord record = aggregate.placementRecords.get(pos);
                if (record == null || "minecraft:air".equals(record.newBlockId)) {
                    missingLining++;
                }
            }
        }

        return new Metrics(overburdenAir, elevationMismatch, missingLining);
    }

    private static boolean isUnauthorizedAir(
            RoadGenerationResult aggregate,
            RoadJunctionGenerator.JunctionBlocks blocks,
            ColumnKey column,
            int y) {
        BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
        if (aggregate != null && aggregate.placementRecords != null) {
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                return true;
            }
            if (record != null) {
                return false;
            }
        }
        if (blocks == null || blocks.getSolids() == null) {
            return false;
        }
        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || !"minecraft:air".equals(p.materialId())) {
                continue;
            }
            if (p.elevation() != y) {
                continue;
            }
            Vec2d plan = p.planPoint();
            if (plan == null) {
                continue;
            }
            int wx = (int) Math.floor(plan.x);
            int wz = (int) Math.floor(plan.y);
            if (wx == column.worldX() && wz == column.worldZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPavementAt(
            RoadGenerationResult aggregate,
            RoadJunctionGenerator.JunctionBlocks blocks,
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
        if (blocks == null || blocks.getSolids() == null) {
            return false;
        }
        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || "minecraft:air".equals(p.materialId())) {
                continue;
            }
            if (p.elevation() != y) {
                continue;
            }
            Vec2d plan = p.planPoint();
            if (plan == null) {
                continue;
            }
            if ((int) Math.floor(plan.x) == column.worldX()
                    && (int) Math.floor(plan.y) == column.worldZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyPavementInColumn(
            RoadGenerationResult aggregate,
            RoadJunctionGenerator.JunctionBlocks blocks,
            ColumnKey column) {
        if (aggregate != null && aggregate.placementLayers != null) {
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
        return hasPavementAt(aggregate, blocks, column,
            blocks != null && blocks.elevationField() != null
                ? blocks.elevationField().surfaceY(column)
                : 0);
    }
}
