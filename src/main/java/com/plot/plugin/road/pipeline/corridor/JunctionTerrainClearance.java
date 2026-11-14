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

import java.util.Collection;
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

    /**
     * 上层路面承重层缺省方块：与 {@link com.plot.plugin.road.pipeline.crosssection.BridgeStructureGenerator}
     * {@code DECK_SLAB_MATERIAL}（{@code material.plot.stone} → {@code minecraft:stone}）一致。
     * 不得使用隧道衬砌石砖，否则开放立交桥底会出现与底板不符的石砖带。
     */
    public static final String ELEVATED_DECK_SUPPORT_BLOCK = "minecraft:stone";

    private JunctionTerrainClearance() {
    }

    /**
     * @param edgeIds 非空时只扫描这些边（路口仅需相连进口），避免大型路网按全网放置记录扫一遍。
     */
    public static Map<ColumnKey, Integer> leftoverTopByColumn(
            Map<String, RoadGenerationResult> edgeResults,
            Collection<String> edgeIds) {
        Map<ColumnKey, Integer> tops = new HashMap<>();
        if (edgeResults == null || edgeResults.isEmpty()) {
            return tops;
        }
        if (edgeIds == null) {
            for (RoadGenerationResult result : edgeResults.values()) {
                accumulateLeftoverTops(tops, result);
            }
            return tops;
        }
        for (String edgeId : edgeIds) {
            if (edgeId == null) {
                continue;
            }
            accumulateLeftoverTops(tops, edgeResults.get(edgeId));
        }
        return tops;
    }

    private static void accumulateLeftoverTops(
            Map<ColumnKey, Integer> tops,
            RoadGenerationResult result) {
        if (result == null || result.placementRecords == null) {
            return;
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
        clearOverheadInPolygon(
            solids, polygon, cellY, tunnelThreshold, terrain, worldX, worldZ, planPoint,
            elevatedDeckY, leftoverTopY, null);
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
            int leftoverTopY,
            Integer absoluteClearTopY) {
        if (solids == null || polygon == null || polygon.size() < 3 || planPoint == null || terrain == null) {
            return;
        }
        if (!RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
            return;
        }
        clearOverheadColumn(
            solids, cellY, tunnelThreshold, terrain, worldX, worldZ, planPoint,
            elevatedDeckY, leftoverTopY, absoluteClearTopY);
    }

    /**
     * @param absoluteClearTopY 隧道设计洞腔顶（roadY+clearHeight）。仅隧道进口传入；
     *                          深覆土实体清障止于 {@code min(roof-1, surfaceY-1)}；
     *                          浅覆土（地表落在路面与洞顶之间）与壳侧一致：不开挖实体，只摘装饰/悬空残留。
     *                          开敞平交必须传 null：实体清障包含工程地表顶层（{@code surfaceY}），
     *                          并可清掉更高的残留边路与柱顶树冠；封闭隧道仍止于地表以下。
     *                          立交桥底上限由 {@code elevatedDeckY} 单独约束，勿与洞顶 cap 混用 {@code roof-1}。
     */
    public static void clearOverheadColumn(
            RoadSolidModel solids,
            int cellY,
            int tunnelThreshold,
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            Vec2d planPoint,
            Integer elevatedDeckY,
            int leftoverTopY,
            Integer absoluteClearTopY) {
        if (solids == null || planPoint == null || terrain == null) {
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
        // 树冠可达十余格；heightmap/隧道阈值都可能偏矮，装饰扫描至少留 24 格净空。
        int headroom = Math.max(24, tunnelThreshold);
        int decoCeil = Math.max(topY, cellY + headroom);

        boolean hasTunnelRoofCap = absoluteClearTopY != null;
        if (hasTunnelRoofCap) {
            // 与 TunnelJunctionShellPass.columnHasEnclosedCover / resolveTunnelSolidPunchTop 同一判定：
            // 浅覆土不开挖实体（避免剩单格悬空地表）；深覆土止于洞顶下；路堑仍可清洞腔。
            int solidPunchTop = resolveTunnelSolidPunchTop(cellY, surfaceY, absoluteClearTopY);
            topY = Math.min(topY, solidPunchTop);
            // 有覆土时硬夹：隧道上下文永不把空气写到自然地表及以上。
            if (surfaceY > cellY) {
                topY = Math.min(topY, surfaceY - 1);
            }
            if (solidPunchTop <= cellY) {
                // 浅覆：实体开挖关闭；装饰只扫洞腔内（地表以下），禁止摘掉地表草/雪造成穿孔。
                decoCeil = Math.min(Math.max(decoCeil, cellY + headroom), surfaceY - 1);
            } else {
                decoCeil = Math.min(decoCeil, Math.max(solidPunchTop, Math.min(surfaceY - 1, cellY + 2)));
            }
            decoCeil = Math.min(decoCeil, surfaceY - 1);
        }
        // 无洞顶 cap：开敞平交，自然地表夹到 surface-1；高于地表的 leftover/columnTop 仍可写入。

        if (elevatedDeckY != null) {
            // elevatedDeckY 是上层路面；保留其下连续 DECK_SUPPORT_DEPTH 格承重层，
            // 净空只清到 supportBottom - 1（deckCap 已是可写空气的最高 Y，勿再 -1）。
            int deckCap = elevatedDeckY - ELEVATED_DECK_SUPPORT_DEPTH - 1;
            topY = Math.min(topY, deckCap);
            decoCeil = Math.min(decoCeil, deckCap);
        } else if (!hasTunnelRoofCap) {
            // 开敞责任列：包含工程地表顶层方块本身（sampleSurfaceY = 最高自然实心 Y）。
            // 遗留边路 / 柱顶树冠可高于地表，取 max 一并清掉；立交承重由 elevatedDeckY 分支夹死。
            int naturalCap = surfaceY;
            int engineeringTop = Math.max(leftoverTopY, columnTopY);
            topY = Math.max(naturalCap, engineeringTop);
        }
        punchColumn(solids, planPoint, cellY, topY);
        clearFloatingAndDecorationsAbove(
            solids, planPoint, terrain, worldX, worldZ, cellY, topY, decoCeil, hasTunnelRoofCap);
    }

    /**
     * 浅覆土隧道列：地表仍高于路面且覆土不足门槛（与壳侧「不封闭施工」同义的清障侧判定）。
     */
    public static boolean isShallowTunnelCover(
            int cellY, int surfaceY, int tunnelThreshold, boolean hasTunnelRoofCap) {
        return hasTunnelRoofCap && surfaceY > cellY && (surfaceY - cellY) < tunnelThreshold;
    }

    /**
     * 隧道路口实体开挖顶：永不挖到自然地表，永不挖掉设计洞顶衬砌格。
     * <ul>
     *   <li>地表 ≤ 路面：路面以上可清到洞顶下（清残留衬砌），不会掏穿地表</li>
     *   <li>路面 &lt; 地表 ≤ 洞顶：浅覆土，返回 {@code cellY}（不开挖）</li>
     *   <li>地表 &gt; 洞顶：深覆土，返回 {@code min(roof-1, surface-1)}</li>
     * </ul>
     */
    public static int resolveTunnelSolidPunchTop(int cellY, int naturalSurfaceY, int designRoofY) {
        if (naturalSurfaceY <= cellY) {
            return designRoofY - 1;
        }
        if (naturalSurfaceY <= designRoofY) {
            return cellY;
        }
        return Math.min(designRoofY - 1, naturalSurfaceY - 1);
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

    /**
     * 带自然地表硬夹的开挖：任何调用方都不得把空气写到地表及以上。
     */
    public static void punchColumnBelowSurface(
            RoadSolidModel solids,
            Vec2d planPoint,
            int cellY,
            int requestedTopY,
            TerrainSampler terrain) {
        if (solids == null || planPoint == null || terrain == null) {
            return;
        }
        int surfaceY = terrain.sampleSurfaceY(planPoint);
        int topY = Math.min(requestedTopY, surfaceY - 1);
        punchColumn(solids, planPoint, cellY, topY);
    }

    /**
     * 实心清障顶以上：摘除可清装饰，以及下方已悬空的残留实心（边坡掏空后挂住的土块/短树干）。
     * 自下而上清，使上层失去支撑后连锁清理。
     * <p>
     * 隧道洞顶上下文（{@code protectNaturalOverburden=true}）：非装饰实心不得仅凭
     * 「下方是空气 / 下方刚被清除」连锁删除——天然洞穴、岩石悬挑与覆土不能当成工程残留。
     */
    static void clearFloatingAndDecorationsAbove(
            RoadSolidModel solids,
            Vec2d planPoint,
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            int cellY,
            int solidTopY,
            int decoCeil) {
        clearFloatingAndDecorationsAbove(
            solids, planPoint, terrain, worldX, worldZ, cellY, solidTopY, decoCeil, false);
    }

    static void clearFloatingAndDecorationsAbove(
            RoadSolidModel solids,
            Vec2d planPoint,
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            int cellY,
            int solidTopY,
            int decoCeil,
            boolean protectNaturalOverburden) {
        if (solids == null || planPoint == null || terrain == null || decoCeil <= solidTopY) {
            return;
        }
        int surfaceY = terrain.sampleSurfaceY(planPoint);
        // 隧道覆土保护：装饰扫描也不得写到自然地表及以上（草/雪层打成空气即「零星穿孔」）。
        int scanCeil = protectNaturalOverburden
            ? Math.min(decoCeil, surfaceY - 1)
            : decoCeil;
        if (scanCeil <= solidTopY) {
            return;
        }
        int fromY = Math.max(cellY + 1, solidTopY + 1);
        boolean[] cleared = new boolean[Math.max(0, scanCeil - fromY + 1)];
        for (int y = fromY; y <= scanCeil; y++) {
            if (protectNaturalOverburden && y >= surfaceY) {
                continue;
            }
            boolean decoration = terrain.isRoadClearableDecoration(worldX, y, worldZ);
            boolean fluid = terrain.isFluidBlock(worldX, y, worldZ);
            boolean solidOrObstruction = terrain.isSolidBlock(worldX, y, worldZ)
                || terrain.isConstructionVolumeObstruction(worldX, y, worldZ);
            if (!decoration && !solidOrObstruction && !fluid) {
                continue;
            }
            if (decoration) {
                writeClearanceAir(solids, planPoint, y);
                cleared[y - fromY] = true;
                continue;
            }
            if (fluid) {
                // 开敞净空内的水/岩浆清掉；隧道覆土扫描不得把山上湖水/含水层一并掏空
                // （洞腔内流体由 punchColumn / 走廊 hasObstruction 负责）。
                if (protectNaturalOverburden) {
                    continue;
                }
                writeClearanceAir(solids, planPoint, y);
                cleared[y - fromY] = true;
                continue;
            }
            if (protectNaturalOverburden) {
                // 隧道列：无设计净空授权时，禁止把自然实心当悬空残留向上掏穿。
                continue;
            }
            boolean belowCleared = y > fromY && cleared[y - 1 - fromY];
            // 支撑判定必须含矿石等 OTHER_SOLID：isSolidBlock 仅认工程土石，
            // 把矿石当「下方空气」会沿矿脉/洞穴把 CLEARANCE 空气连锁写到地表，并压掉衬砌/灯光。
            boolean belowUnsupported = !hasWorldVerticalSupport(terrain, worldX, y - 1, worldZ);
            if (!belowCleared && !belowUnsupported) {
                continue;
            }
            writeClearanceAir(solids, planPoint, y);
            cleared[y - fromY] = true;
        }
    }

    /**
     * 竖直支撑：工程土石，或任意非空气构筑/矿石障碍（与 {@link TerrainSampler#isConstructionVolumeObstruction} 对齐）。
     */
    static boolean hasWorldVerticalSupport(
            TerrainSampler terrain, int worldX, int y, int worldZ) {
        if (terrain == null) {
            return false;
        }
        return terrain.isSolidBlock(worldX, y, worldZ)
            || terrain.isConstructionVolumeObstruction(worldX, y, worldZ);
    }

    private static void writeClearanceAir(RoadSolidModel solids, Vec2d planPoint, int y) {
        solids.add(
            planPoint,
            y,
            RoadSolidLayer.TUNNEL,
            "minecraft:air",
            RoadSolidMaterialPriority.CLEARANCE);
    }
}
