package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

import java.util.HashSet;
import java.util.Set;

/**
 * 隧道洞身/洞门/棚洞的山体保护柱：开放式边坡与边坡清障不得切入这些柱。
 * <p>
 * 仅看站点 constructionType 不够——OPEN 路基的边坡射线会伸进邻近洞顶山体；
 * 这里用隧道族净空柱形成统一的空间拒绝域。洞门外侧仍允许开放放坡清地形盖。
 */
public final class TunnelMountainProtection {
    private final Set<ColumnKey> protectedColumns;

    private TunnelMountainProtection(Set<ColumnKey> protectedColumns) {
        this.protectedColumns = Set.copyOf(protectedColumns);
    }

    public static TunnelMountainProtection none() {
        return new TunnelMountainProtection(Set.of());
    }

    /**
     * 洞身/棚洞：路面半宽+1 扩张；洞门：仅净空柱本身。
     */
    public static TunnelMountainProtection from(RoadCorridorPlan plan, ResolvedCrossSection crossSection) {
        if (plan == null) {
            return none();
        }
        int halfWidth = Math.max(
            1,
            Math.abs(RoadDimensionUtils.maxLateralOffset(
                RoadCorridorWidth.pavementEnvelopeWidthBlocks(crossSection))));
        int bodyRadius = halfWidth + 1;
        Set<ColumnKey> protectedColumns = new HashSet<>();
        for (var entry : plan.clearanceEntries()) {
            RoadConstructionType type = entry.getValue().constructionType();
            if (type == null) {
                continue;
            }
            ColumnKey seed = entry.getKey();
            if (type == RoadConstructionType.TUNNEL
                    || type == RoadConstructionType.TUNNEL_PORTAL
                    || type.isRockShelter()) {
                // 洞门与洞身同用半宽包络：锐弯洞口外侧 OPEN 边坡射线同样会切入洞顶山体
                for (int dx = -bodyRadius; dx <= bodyRadius; dx++) {
                    for (int dz = -bodyRadius; dz <= bodyRadius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) > bodyRadius) {
                            continue;
                        }
                        protectedColumns.add(new ColumnKey(seed.worldX() + dx, seed.worldZ() + dz));
                    }
                }
            }
        }
        return protectedColumns.isEmpty() ? none() : new TunnelMountainProtection(protectedColumns);
    }

    public boolean isEmpty() {
        return protectedColumns.isEmpty();
    }

    public boolean protects(ColumnKey key) {
        return key != null && protectedColumns.contains(key);
    }

    public boolean protects(int worldX, int worldZ) {
        return protectedColumns.contains(new ColumnKey(worldX, worldZ));
    }

    public int protectedColumnCount() {
        return protectedColumns.size();
    }

    /**
     * 删除落在保护柱上的开放边坡单元，避免随后清障/切方掏穿山体。
     */
    public void scrubOpenBatter(RoadCorridorPlan plan) {
        if (plan == null || protectedColumns.isEmpty()) {
            return;
        }
        for (ColumnKey key : protectedColumns) {
            plan.removeSlopeCell(key);
        }
    }
}
