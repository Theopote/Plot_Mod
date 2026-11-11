package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTerrainClearanceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路口局部路面高程权威场：每个世界列唯一 {@code targetSurfaceY} + 足迹角色。
 * 铺装、清障、隧道壳共用；邻域孤立突起/凹陷在设计层钳制，不做生成后填石。
 */
public final class JunctionElevationField {
    private final int junctionY;
    private final JunctionFilletElevationLoft loft;
    private final JunctionWorldFootprint footprint;
    private final RoadTerrainClearanceUtils.BlockColumnResolver columns;
    private final Map<ColumnKey, Integer> targetYByColumn;
    private final Map<ColumnKey, JunctionWorldFootprint.ElevationRole> roleByColumn;

    private JunctionElevationField(
            int junctionY,
            JunctionFilletElevationLoft loft,
            JunctionWorldFootprint footprint,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            Map<ColumnKey, Integer> targetYByColumn,
            Map<ColumnKey, JunctionWorldFootprint.ElevationRole> roleByColumn) {
        this.junctionY = junctionY;
        this.loft = loft != null ? loft : JunctionFilletElevationLoft.inactive(junctionY);
        this.footprint = footprint != null ? footprint : JunctionWorldFootprint.empty();
        this.columns = columns;
        this.targetYByColumn = Collections.unmodifiableMap(new LinkedHashMap<>(targetYByColumn));
        this.roleByColumn = Collections.unmodifiableMap(new LinkedHashMap<>(roleByColumn));
    }

    public static JunctionElevationField inactive(int junctionY) {
        return new JunctionElevationField(
            junctionY,
            JunctionFilletElevationLoft.inactive(junctionY),
            JunctionWorldFootprint.empty(),
            null,
            Map.of(),
            Map.of());
    }

    public static JunctionElevationField build(
            JunctionWorldFootprint footprint,
            JunctionFilletElevationLoft loft,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        JunctionFilletElevationLoft safeLoft =
            loft != null ? loft : JunctionFilletElevationLoft.inactive(junctionY);
        JunctionWorldFootprint safeFootprint =
            footprint != null ? footprint : JunctionWorldFootprint.empty();
        Map<ColumnKey, Integer> targets = new LinkedHashMap<>();
        Map<ColumnKey, JunctionWorldFootprint.ElevationRole> roles = new LinkedHashMap<>();

        Set<ColumnKey> pave = safeFootprint.paveColumns();
        if (pave.isEmpty() && columns != null) {
            // 无足迹时仍可为后续查询保留 loft 行为
            return new JunctionElevationField(
                junctionY, safeLoft, safeFootprint, columns, Map.of(), Map.of());
        }
        for (ColumnKey key : pave) {
            if (key == null) {
                continue;
            }
            JunctionWorldFootprint.ElevationRole role = safeFootprint.elevationRole(key);
            roles.put(key, role);
            Vec2d plan = safeFootprint.planPoint(key);
            if (plan == null && columns != null) {
                plan = new Vec2d(key.worldX() + 0.5, key.worldZ() + 0.5);
            }
            int y = safeLoft.surfaceY(plan, safeFootprint);
            targets.put(key, y);
        }
        // 先消孤立尖峰，再对圆角/接缝强制相邻 |ΔY|≤1，避免纵坡圆角棋盘起伏与顶衬跟着下沉。
        for (int pass = 0; pass < 6; pass++) {
            if (clampIsolatedSpikes(targets) == 0) {
                break;
            }
        }
        Set<ColumnKey> soft = new java.util.LinkedHashSet<>();
        for (Map.Entry<ColumnKey, JunctionWorldFootprint.ElevationRole> entry : roles.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() == JunctionWorldFootprint.ElevationRole.FILLET
                    || entry.getValue() == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
                soft.add(entry.getKey());
            }
        }
        for (int pass = 0; pass < 24; pass++) {
            if (enforceAdjacentStepLimit(targets, soft) == 0) {
                break;
            }
        }
        // |ΔY|≤1 仍可能留下 64/65 棋盘；对圆角列再做邻域均值松弛，压掉视觉粗糙。
        Set<ColumnKey> filletOnly = new java.util.LinkedHashSet<>();
        for (Map.Entry<ColumnKey, JunctionWorldFootprint.ElevationRole> entry : roles.entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() == JunctionWorldFootprint.ElevationRole.FILLET) {
                filletOnly.add(entry.getKey());
            }
        }
        for (int pass = 0; pass < 8; pass++) {
            if (relaxFilletTowardNeighbors(targets, filletOnly) == 0) {
                break;
            }
        }
        for (int pass = 0; pass < 8; pass++) {
            if (enforceAdjacentStepLimit(targets, soft) == 0) {
                break;
            }
        }
        return new JunctionElevationField(
            junctionY, safeLoft, safeFootprint, columns, targets, roles);
    }

    public int junctionY() {
        return junctionY;
    }

    public JunctionFilletElevationLoft loft() {
        return loft;
    }

    public JunctionWorldFootprint footprint() {
        return footprint;
    }

    public Map<ColumnKey, Integer> targetYByColumn() {
        return targetYByColumn;
    }

    public JunctionWorldFootprint.ElevationRole role(ColumnKey key) {
        if (key == null) {
            return JunctionWorldFootprint.ElevationRole.OUTSIDE;
        }
        return roleByColumn.getOrDefault(key, footprint.elevationRole(key));
    }

    public int surfaceY(ColumnKey key) {
        if (key == null) {
            return junctionY;
        }
        Integer pinned = targetYByColumn.get(key);
        if (pinned != null) {
            return pinned;
        }
        Vec2d plan = footprint.planPoint(key);
        if (plan == null && columns != null) {
            plan = new Vec2d(key.worldX() + 0.5, key.worldZ() + 0.5);
        }
        return loft.surfaceY(plan, footprint);
    }

    public int surfaceY(Vec2d planPoint) {
        if (planPoint == null) {
            return junctionY;
        }
        if (columns != null) {
            ColumnKey key = new ColumnKey(columns.worldX(planPoint), columns.worldZ(planPoint));
            Integer pinned = targetYByColumn.get(key);
            if (pinned != null) {
                return pinned;
            }
        }
        return loft.surfaceY(planPoint, footprint);
    }

    /**
     * 四邻接中位数钳制：允许正常 1 格斜坡；相对邻域中位数孤立 ≥2 的突起/凹陷压回可连台阶。
     * @return 本轮修正列数
     */
    static int clampIsolatedSpikes(Map<ColumnKey, Integer> targets) {
        if (targets == null || targets.size() < 4) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (Map.Entry<ColumnKey, Integer> entry : targets.entrySet()) {
            ColumnKey key = entry.getKey();
            Integer y = entry.getValue();
            if (key == null || y == null) {
                continue;
            }
            List<Integer> neighborYs = new ArrayList<>(4);
            for (int[] dir : dirs) {
                Integer ny = targets.get(new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]));
                if (ny != null) {
                    neighborYs.add(ny);
                }
            }
            if (neighborYs.size() < 3) {
                continue;
            }
            Collections.sort(neighborYs);
            int median = neighborYs.get(neighborYs.size() / 2);
            // 仅钳制「孤立」突起/凹陷：比所有邻居都高或都低 ≥2；纵坡台阶边缘不压平。
            boolean isolatedHigh = true;
            boolean isolatedLow = true;
            for (int ny : neighborYs) {
                if (y < ny + 2) {
                    isolatedHigh = false;
                }
                if (y > ny - 2) {
                    isolatedLow = false;
                }
            }
            if (isolatedHigh || isolatedLow) {
                int next = median + (isolatedHigh ? 1 : isolatedLow ? -1 : 0);
                // 贴到可与邻域连接的一格台阶内
                if (Math.abs(next - median) > 1) {
                    next = median;
                }
                if (next != y) {
                    adjusted.put(key, next);
                }
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }

    /**
     * 软列（圆角/接缝）与任意邻列的高差不得超过 1 格：按违规邻居投票升降一格，保留 Minecraft 斜坡台阶。
     * @return 本轮修正列数
     */
    static int enforceAdjacentStepLimit(
            Map<ColumnKey, Integer> targets, Set<ColumnKey> softColumns) {
        if (targets == null || targets.isEmpty() || softColumns == null || softColumns.isEmpty()) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (ColumnKey key : softColumns) {
            Integer y = targets.get(key);
            if (key == null || y == null) {
                continue;
            }
            int pull = 0;
            int maxStep = 0;
            List<Integer> neighborYs = new ArrayList<>(4);
            for (int[] dir : dirs) {
                Integer ny = targets.get(new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]));
                if (ny == null) {
                    continue;
                }
                neighborYs.add(ny);
                int step = Math.abs(y - ny);
                maxStep = Math.max(maxStep, step);
                if (y > ny + 1) {
                    pull--;
                } else if (y < ny - 1) {
                    pull++;
                }
            }
            if (maxStep <= 1 || neighborYs.isEmpty()) {
                continue;
            }
            int next;
            if (pull != 0) {
                next = y + (pull > 0 ? 1 : -1);
            } else {
                // 双向都超限（如邻域同时有 64 与 67）：向中位数收一格，避免 pull 抵消后卡死。
                Collections.sort(neighborYs);
                int median = neighborYs.get(neighborYs.size() / 2);
                if (y == median) {
                    continue;
                }
                next = y + (median > y ? 1 : -1);
            }
            if (next != y) {
                adjusted.put(key, next);
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }

    /**
     * 圆角列向四邻均值收半步：消除 |ΔY|≤1 棋盘起伏，保留缓坡趋势。
     * @return 本轮修正列数
     */
    static int relaxFilletTowardNeighbors(
            Map<ColumnKey, Integer> targets, Set<ColumnKey> filletColumns) {
        if (targets == null || targets.isEmpty() || filletColumns == null || filletColumns.isEmpty()) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (ColumnKey key : filletColumns) {
            Integer y = targets.get(key);
            if (key == null || y == null) {
                continue;
            }
            int sum = 0;
            int count = 0;
            for (int[] dir : dirs) {
                Integer ny = targets.get(new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]));
                if (ny == null) {
                    continue;
                }
                sum += ny;
                count++;
            }
            if (count < 2) {
                continue;
            }
            double mean = (double) sum / (double) count;
            int next = (int) Math.round((y + mean) * 0.5);
            if (next != y) {
                adjusted.put(key, next);
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }
}
