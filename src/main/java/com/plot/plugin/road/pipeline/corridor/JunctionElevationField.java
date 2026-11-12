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
        // 进口路面锚点：同列 exact 与足迹外邻接进口列。松弛不得把路缘↔圆角交界从坡道上拉开。
        Map<ColumnKey, Integer> approachAnchors = new LinkedHashMap<>();
        Map<ColumnKey, Integer> approachRoadY = safeLoft.approachRoadY();
        if (approachRoadY != null) {
            for (Map.Entry<ColumnKey, Integer> entry : approachRoadY.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    approachAnchors.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Set<ColumnKey> pinnedAnchors = new java.util.LinkedHashSet<>();
        for (ColumnKey key : pave) {
            JunctionWorldFootprint.ElevationRole role = roles.get(key);
            if (role == JunctionWorldFootprint.ElevationRole.ROAD_CORE
                    || role == JunctionWorldFootprint.ElevationRole.FILLET) {
                continue;
            }
            Integer ay = approachAnchors.get(key);
            if (ay != null) {
                targets.put(key, ay);
                pinnedAnchors.add(key);
            }
        }

        for (int pass = 0; pass < 6; pass++) {
            if (clampIsolatedSpikes(targets, pinnedAnchors) == 0) {
                break;
            }
        }
        Set<ColumnKey> soft = new java.util.LinkedHashSet<>();
        for (Map.Entry<ColumnKey, JunctionWorldFootprint.ElevationRole> entry : roles.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (pinnedAnchors.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() == JunctionWorldFootprint.ElevationRole.FILLET
                    || entry.getValue() == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
                soft.add(entry.getKey());
            }
        }
        Set<ColumnKey> filletOnly = new java.util.LinkedHashSet<>();
        Set<ColumnKey> seamOnly = new java.util.LinkedHashSet<>();
        for (ColumnKey key : soft) {
            JunctionWorldFootprint.ElevationRole role = roles.get(key);
            if (role == JunctionWorldFootprint.ElevationRole.FILLET) {
                filletOnly.add(key);
            } else if (role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
                seamOnly.add(key);
            }
        }
        for (int pass = 0; pass < 24; pass++) {
            if (enforceAdjacentStepLimit(targets, soft, approachAnchors) == 0) {
                break;
            }
        }
        // 接缝列贴齐进口坡道；圆角列跟路缘放样，禁止被高进口锚点往上拽出突起。
        for (int pass = 0; pass < 12; pass++) {
            if (alignSoftColumnsToApproachSeam(targets, seamOnly, approachAnchors) == 0) {
                break;
            }
        }
        for (int pass = 0; pass < 8; pass++) {
            if (relaxFilletTowardNeighbors(targets, filletOnly) == 0) {
                break;
            }
        }
        for (int pass = 0; pass < 6; pass++) {
            if (flattenFilletCheckerboard(targets, filletOnly) == 0) {
                break;
            }
        }
        // 松弛后再次钉回进口 exact，避免交界被核区均值拉偏（下沉/突起 + 顶衬跟着走）。
        for (ColumnKey key : pinnedAnchors) {
            Integer ay = approachAnchors.get(key);
            if (ay != null) {
                targets.put(key, ay);
            }
        }
        for (int pass = 0; pass < 12; pass++) {
            if (alignSoftColumnsToApproachSeam(targets, seamOnly, approachAnchors) == 0) {
                break;
            }
        }
        // 平坦同层：进口锚点均在 junctionY 时强制整片同高，禁止削峰/台阶把圆角压低或抬高。
        if (isUniformFlatApproaches(approachAnchors, junctionY)) {
            for (ColumnKey key : new java.util.ArrayList<>(targets.keySet())) {
                if (key != null) {
                    targets.put(key, junctionY);
                }
            }
        } else if (!isUniformFlatTargets(targets, junctionY)) {
            // 有坡圆角：低侧旁抬到不低于路面，高侧旁压到不高于路面；削局部鼓包；与台阶限幅交替。
            for (int pass = 0; pass < 16; pass++) {
                int band = clampFilletToApproachGradeBands(targets, filletOnly, approachAnchors);
                int shave = shaveFilletLocalMaxima(targets, filletOnly, approachAnchors);
                int toLoft = pullFilletDownTowardLoft(
                    targets, filletOnly, roles, safeLoft, safeFootprint, columns);
                int step = enforceAdjacentStepLimit(targets, soft, approachAnchors);
                if (band == 0 && shave == 0 && toLoft == 0 && step == 0) {
                    break;
                }
            }
            for (int pass = 0; pass < 8; pass++) {
                int shave = shaveFilletLocalMaxima(targets, filletOnly, approachAnchors);
                int step = enforceAdjacentStepLimit(targets, soft, approachAnchors);
                if (shave == 0 && step == 0) {
                    break;
                }
            }
            // 最终约束：台阶限幅之后再压回 loft/路缘高侧包络，防止鼓包被重新抬高。
            enforceFilletFinalLoftCeiling(
                targets, filletOnly, safeLoft, safeFootprint, columns, approachAnchors);
        }
        return new JunctionElevationField(
            junctionY, safeLoft, safeFootprint, columns, targets, roles);
    }

    static boolean isUniformFlatTargets(Map<ColumnKey, Integer> targets, int junctionY) {
        if (targets == null || targets.isEmpty()) {
            return true;
        }
        for (Integer y : targets.values()) {
            if (y == null || y != junctionY) {
                return false;
            }
        }
        return true;
    }

    static boolean isUniformFlatApproaches(Map<ColumnKey, Integer> approachAnchors, int junctionY) {
        if (approachAnchors == null || approachAnchors.isEmpty()) {
            return true;
        }
        for (Integer y : approachAnchors.values()) {
            if (y != null && y != junctionY) {
                return false;
            }
        }
        return true;
    }

    static int enforceFilletFinalLoftCeiling(
            Map<ColumnKey, Integer> targets,
            Set<ColumnKey> filletColumns,
            JunctionFilletElevationLoft loft,
            JunctionWorldFootprint footprint,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            Map<ColumnKey, Integer> approachAnchors) {
        if (targets == null || filletColumns == null || filletColumns.isEmpty() || loft == null) {
            return 0;
        }
        int changed = 0;
        for (ColumnKey key : filletColumns) {
            Integer y = targets.get(key);
            if (key == null || y == null) {
                continue;
            }
            Vec2d plan = footprint != null ? footprint.planPoint(key) : null;
            if (plan == null && columns != null) {
                plan = new Vec2d(key.worldX() + 0.5, key.worldZ() + 0.5);
            }
            int ceiling = loft.surfaceY(plan, footprint);
            if (approachAnchors != null) {
                int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                for (int[] dir : dirs) {
                    Integer ay = approachAnchors.get(
                        new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]));
                    if (ay != null) {
                        ceiling = Math.max(ceiling, ay);
                    }
                }
            }
            if (y > ceiling) {
                targets.put(key, ceiling);
                changed++;
            }
        }
        return changed;
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
        return clampIsolatedSpikes(targets, Set.of());
    }

    static int clampIsolatedSpikes(Map<ColumnKey, Integer> targets, Set<ColumnKey> frozen) {
        if (targets == null || targets.size() < 4) {
            return 0;
        }
        Set<ColumnKey> frozenKeys = frozen != null ? frozen : Set.of();
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (Map.Entry<ColumnKey, Integer> entry : targets.entrySet()) {
            ColumnKey key = entry.getKey();
            Integer y = entry.getValue();
            if (key == null || y == null || frozenKeys.contains(key)) {
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
        return enforceAdjacentStepLimit(targets, softColumns, Map.of());
    }

    static int enforceAdjacentStepLimit(
            Map<ColumnKey, Integer> targets,
            Set<ColumnKey> softColumns,
            Map<ColumnKey, Integer> approachAnchors) {
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
                ColumnKey neighbor = new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]);
                Integer ny = resolveNeighborY(targets, approachAnchors, neighbor);
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
     * 路缘↔接缝交界：接缝列向邻接进口锚点收一格。
     * @return 本轮修正列数
     */
    static int alignSoftColumnsToApproachSeam(
            Map<ColumnKey, Integer> targets,
            Set<ColumnKey> softColumns,
            Map<ColumnKey, Integer> approachAnchors) {
        if (targets == null || softColumns == null || softColumns.isEmpty()
                || approachAnchors == null || approachAnchors.isEmpty()) {
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
            int seen = 0;
            for (int[] dir : dirs) {
                ColumnKey neighbor = new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]);
                Integer ay = approachAnchors.get(neighbor);
                if (ay == null) {
                    continue;
                }
                seen++;
                if (y > ay + 1) {
                    pull--;
                } else if (y < ay - 1) {
                    pull++;
                }
            }
            if (seen == 0 || pull == 0) {
                continue;
            }
            int next = y + (pull > 0 ? 1 : -1);
            if (next != y) {
                adjusted.put(key, next);
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }

    /**
     * 有坡圆角带约束：邻域进口（切比雪夫半径 ≤2）内，相对低侧抬底、相对高侧压顶；
     * 并始终不得高于邻域进口最高路面（削鼓包）。每轮最多 ±1 以配合台阶限幅。
     * @return 本轮修正列数
     */
    static int clampFilletToApproachGradeBands(
            Map<ColumnKey, Integer> targets,
            Set<ColumnKey> filletColumns,
            Map<ColumnKey, Integer> approachAnchors) {
        if (targets == null || filletColumns == null || filletColumns.isEmpty()
                || approachAnchors == null || approachAnchors.isEmpty()) {
            return 0;
        }
        int globalLow = Integer.MAX_VALUE;
        int globalHigh = Integer.MIN_VALUE;
        for (Integer ay : approachAnchors.values()) {
            if (ay == null) {
                continue;
            }
            globalLow = Math.min(globalLow, ay);
            globalHigh = Math.max(globalHigh, ay);
        }
        if (globalLow == Integer.MAX_VALUE) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        for (ColumnKey key : filletColumns) {
            Integer y = targets.get(key);
            if (key == null || y == null) {
                continue;
            }
            Integer floor = null;
            Integer ceiling = null;
            Integer localMax = null;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    ColumnKey neighbor = new ColumnKey(key.worldX() + dx, key.worldZ() + dz);
                    Integer ay = approachAnchors.get(neighbor);
                    if (ay == null) {
                        continue;
                    }
                    localMax = localMax == null ? ay : Math.max(localMax, ay);
                    // 平坦时高低同值：两侧同时钉死；有坡时仅低侧抬底、高侧压顶。
                    if (ay <= globalLow) {
                        floor = floor == null ? ay : Math.max(floor, ay);
                    }
                    if (ay >= globalHigh) {
                        ceiling = ceiling == null ? ay : Math.min(ceiling, ay);
                    }
                }
            }
            if (floor == null && ceiling == null && localMax == null) {
                continue;
            }
            int next = y;
            if (floor != null && next < floor) {
                next = Math.min(floor, y + 1);
            }
            // 邻域进口最高路面是硬天花板：高出的鼓包必须压回。
            Integer hardCeiling = ceiling;
            if (localMax != null) {
                hardCeiling = hardCeiling == null ? localMax : Math.min(hardCeiling, localMax);
            }
            if (hardCeiling != null && next > hardCeiling) {
                next = Math.max(hardCeiling, y - 1);
            }
            if (next != y) {
                adjusted.put(key, next);
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }

    /**
     * 圆角局部严格极大值削峰：高于全部四邻（含进口锚点）则降 1 格。
     */
    static int shaveFilletLocalMaxima(
            Map<ColumnKey, Integer> targets,
            Set<ColumnKey> filletColumns,
            Map<ColumnKey, Integer> approachAnchors) {
        if (targets == null || filletColumns == null || filletColumns.isEmpty()) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (ColumnKey key : filletColumns) {
            Integer y = targets.get(key);
            if (key == null || y == null) {
                continue;
            }
            int maxNeighbor = Integer.MIN_VALUE;
            int count = 0;
            for (int[] dir : dirs) {
                Integer ny = resolveNeighborY(
                    targets, approachAnchors,
                    new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]));
                if (ny == null) {
                    continue;
                }
                maxNeighbor = Math.max(maxNeighbor, ny);
                count++;
            }
            if (count < 2 || y <= maxNeighbor) {
                continue;
            }
            adjusted.put(key, y - 1);
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }

    /**
     * 圆角不得被松弛/台阶限幅抬到高于路缘放样：向 loft 收一格。
     */
    static int pullFilletDownTowardLoft(
            Map<ColumnKey, Integer> targets,
            Set<ColumnKey> filletColumns,
            Map<ColumnKey, JunctionWorldFootprint.ElevationRole> roles,
            JunctionFilletElevationLoft loft,
            JunctionWorldFootprint footprint,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        if (targets == null || filletColumns == null || filletColumns.isEmpty() || loft == null) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        for (ColumnKey key : filletColumns) {
            Integer y = targets.get(key);
            if (key == null || y == null) {
                continue;
            }
            Vec2d plan = footprint != null ? footprint.planPoint(key) : null;
            if (plan == null && columns != null) {
                plan = new Vec2d(key.worldX() + 0.5, key.worldZ() + 0.5);
            }
            if (plan == null) {
                continue;
            }
            int loftY = loft.surfaceY(plan, footprint);
            if (y > loftY) {
                adjusted.put(key, y - 1);
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }

    private static Integer resolveNeighborY(
            Map<ColumnKey, Integer> targets,
            Map<ColumnKey, Integer> approachAnchors,
            ColumnKey neighbor) {
        if (neighbor == null) {
            return null;
        }
        Integer inField = targets != null ? targets.get(neighbor) : null;
        if (inField != null) {
            return inField;
        }
        return approachAnchors != null ? approachAnchors.get(neighbor) : null;
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

    /**
     * 拆掉圆角 2×2 棋盘（对角同高、邻格差 1）。收到较低一档，避免缓坡被抬高一格高出路面。
     */
    static int flattenFilletCheckerboard(
            Map<ColumnKey, Integer> targets, Set<ColumnKey> filletColumns) {
        if (targets == null || filletColumns == null || filletColumns.size() < 4) {
            return 0;
        }
        Map<ColumnKey, Integer> adjusted = new LinkedHashMap<>();
        for (ColumnKey key : filletColumns) {
            if (key == null) {
                continue;
            }
            ColumnKey east = new ColumnKey(key.worldX() + 1, key.worldZ());
            ColumnKey north = new ColumnKey(key.worldX(), key.worldZ() + 1);
            ColumnKey northEast = new ColumnKey(key.worldX() + 1, key.worldZ() + 1);
            if (!filletColumns.contains(east)
                    || !filletColumns.contains(north)
                    || !filletColumns.contains(northEast)) {
                continue;
            }
            Integer a = targets.get(key);
            Integer b = targets.get(east);
            Integer c = targets.get(north);
            Integer d = targets.get(northEast);
            if (a == null || b == null || c == null || d == null) {
                continue;
            }
            boolean checker = a.equals(d) && b.equals(c) && !a.equals(b) && Math.abs(a - b) == 1;
            if (!checker) {
                continue;
            }
            // 取较低一档：round(mean) 在 0.5 会把缓坡抬高一格，圆角高出路面。
            int flat = Math.min(a, b);
            if (a != flat) {
                adjusted.put(key, flat);
            }
            if (b != flat) {
                adjusted.put(east, flat);
            }
            if (c != flat) {
                adjusted.put(north, flat);
            }
            if (d != flat) {
                adjusted.put(northEast, flat);
            }
        }
        targets.putAll(adjusted);
        return adjusted.size();
    }
}
