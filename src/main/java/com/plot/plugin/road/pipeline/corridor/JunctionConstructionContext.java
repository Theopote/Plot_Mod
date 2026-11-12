package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 路口世界列施工归属与清障权限的权威上下文。
 * <p>
 * 核区/圆角/侧向洞腔共用同一保护掩膜；禁止仅凭「最近中心线施工类型」授权露天开挖。
 * 隧道进口漏检或矛盾时 fail-closed：跳过破坏性清障并记入诊断，不得退化为挖到地表。
 */
public final class JunctionConstructionContext {
    private static final Logger LOG = Logger.getLogger(JunctionConstructionContext.class.getName());

    public enum ColumnRole {
        OPEN,
        ENCLOSED_TUNNEL,
        PORTAL_TRANSITION,
        GRADE_SEPARATED
    }

    public record ColumnRule(
            ColumnRole role,
            int designRoadY,
            Integer cavityRoofY,
            int naturalSurfaceY,
            Integer maxAuthorizedClearY,
            boolean destructiveClearAllowed) {
    }

    private final String nodeId;
    private final JunctionWorldFootprint footprint;
    private final JunctionElevationField elevationField;
    private final Map<ColumnKey, ColumnRule> rulesByColumn;
    private final Set<ColumnKey> protectedCavityColumns;
    private final boolean undergroundJunction;
    private final boolean tunnelEntryPlaza;
    private final boolean failClosed;
    private final String failClosedReason;
    private final Integer tunnelClearHeight;
    private final Integer elevatedClearCap;
    private final List<String> diagnostics;

    private JunctionConstructionContext(
            String nodeId,
            JunctionWorldFootprint footprint,
            JunctionElevationField elevationField,
            Map<ColumnKey, ColumnRule> rulesByColumn,
            Set<ColumnKey> protectedCavityColumns,
            boolean undergroundJunction,
            boolean tunnelEntryPlaza,
            boolean failClosed,
            String failClosedReason,
            Integer tunnelClearHeight,
            Integer elevatedClearCap,
            List<String> diagnostics) {
        this.nodeId = nodeId != null ? nodeId : "";
        this.footprint = footprint != null ? footprint : JunctionWorldFootprint.empty();
        this.elevationField = elevationField != null
            ? elevationField
            : JunctionElevationField.inactive(0);
        this.rulesByColumn = Collections.unmodifiableMap(new LinkedHashMap<>(rulesByColumn));
        this.protectedCavityColumns = Collections.unmodifiableSet(new LinkedHashSet<>(protectedCavityColumns));
        this.undergroundJunction = undergroundJunction;
        this.tunnelEntryPlaza = tunnelEntryPlaza;
        this.failClosed = failClosed;
        this.failClosedReason = failClosedReason;
        this.tunnelClearHeight = tunnelClearHeight;
        this.elevatedClearCap = elevatedClearCap;
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static JunctionConstructionContext openOnly(
            String nodeId,
            JunctionWorldFootprint footprint,
            JunctionElevationField elevationField,
            Integer elevatedClearCap) {
        return new JunctionConstructionContext(
            nodeId,
            footprint,
            elevationField,
            Map.of(),
            Set.of(),
            false,
            false,
            false,
            null,
            null,
            elevatedClearCap,
            List.of());
    }

    public String nodeId() {
        return nodeId;
    }

    public JunctionWorldFootprint footprint() {
        return footprint;
    }

    public JunctionElevationField elevationField() {
        return elevationField;
    }

    public boolean undergroundJunction() {
        return undergroundJunction;
    }

    /** 进口首段即为隧道族（非开敞 stub 远端才进洞）。 */
    public boolean tunnelEntryPlaza() {
        return tunnelEntryPlaza;
    }

    public boolean failClosed() {
        return failClosed;
    }

    public String failClosedReason() {
        return failClosedReason;
    }

    public Set<ColumnKey> protectedCavityColumns() {
        return protectedCavityColumns;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public Integer tunnelClearHeight() {
        return tunnelClearHeight;
    }

    public ColumnRule rule(ColumnKey column) {
        if (column == null) {
            return null;
        }
        return rulesByColumn.get(column);
    }

    public boolean isProtectedCavity(ColumnKey column) {
        return column != null && protectedCavityColumns.contains(column);
    }

    /**
     * 逐列清障上限。null 仅表示 OPEN 露天授权；fail-closed / 保护列绝不返回「可挖到地表」的 null。
     * 返回 {@link Integer#MIN_VALUE} 哨兵表示禁止破坏性清障（调用方应跳过 grade/clear）。
     */
    public static final int CLEAR_DENIED = Integer.MIN_VALUE;

    public Integer maxAuthorizedClearY(ColumnKey column, int designY) {
        ColumnRule rule = rule(column);
        if (rule != null) {
            if (!rule.destructiveClearAllowed()) {
                return CLEAR_DENIED;
            }
            return rule.maxAuthorizedClearY();
        }
        if (failClosed && undergroundJunction) {
            return CLEAR_DENIED;
        }
        if (elevatedClearCap != null) {
            return elevatedClearCap;
        }
        // 未登记列：开敞平交可露天；地下路口缺掩膜时 fail-closed 已在上分支处理。
        return undergroundJunction ? CLEAR_DENIED : null;
    }

    public static JunctionConstructionContext build(
            RoadNode node,
            RoadNetwork network,
            JunctionWorldFootprint footprint,
            JunctionElevationField elevationField,
            TerrainSampler terrain,
            List<TunnelJunctionSeedResolver.TunnelApproach> tunnelApproaches,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            Integer tunnelClearHeight,
            Integer flatTunnelCavityCap,
            Integer elevatedClearCap,
            int liningThickness,
            int tunnelThreshold,
            int sideClearance) {
        return build(
            node, network, footprint, elevationField, terrain, tunnelApproaches, edgeResults, edges,
            config, coordinateTransformer, tunnelClearHeight, flatTunnelCavityCap, elevatedClearCap,
            liningThickness, tunnelThreshold, sideClearance, List.of());
    }

    public static JunctionConstructionContext build(
            RoadNode node,
            RoadNetwork network,
            JunctionWorldFootprint footprint,
            JunctionElevationField elevationField,
            TerrainSampler terrain,
            List<TunnelJunctionSeedResolver.TunnelApproach> tunnelApproaches,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadSystemConfig config,
            ICoordinateService coordinateTransformer,
            Integer tunnelClearHeight,
            Integer flatTunnelCavityCap,
            Integer elevatedClearCap,
            int liningThickness,
            int tunnelThreshold,
            int sideClearance,
            List<Vec2d> junctionPolygon) {
        String nodeId = node != null ? node.getId() : "";
        JunctionWorldFootprint safeFootprint = footprint != null ? footprint : JunctionWorldFootprint.empty();
        JunctionElevationField field = elevationField != null
            ? elevationField
            : JunctionElevationField.inactive(0);
        List<String> diagnostics = new java.util.ArrayList<>();
        boolean gradeSeparated = node != null
            && (node.isGradeSeparated() || node.isStackGradeSeparated());

        List<TunnelJunctionSeedResolver.TunnelApproach> approaches =
            tunnelApproaches != null ? tunnelApproaches : List.of();
        boolean hasApproaches = !approaches.isEmpty();
        Integer clearHeight = tunnelClearHeight;
        if (clearHeight == null && flatTunnelCavityCap != null) {
            // 绝对 cap 时用典型路口 Y 近似净空；逐列仍用 roof = designY + clear 或 flat cap。
            clearHeight = Math.max(1, flatTunnelCavityCap - field.junctionY());
        }
        if (clearHeight == null && hasApproaches) {
            clearHeight = TunnelJunctionSeedResolver.resolveTunnelClearHeight(approaches);
        }
        if (clearHeight == null) {
            for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
                if (approach != null && approach.tunnelStyle() != null) {
                    clearHeight = Math.max(1, approach.tunnelStyle().clearHeight());
                    break;
                }
            }
        }

        boolean underground = hasApproaches || gradeSeparated;
        boolean failClosed = false;
        String failReason = null;

        // 矛盾：声称有隧道样式/近节点隧道族，但 approach 收集为空且广场深覆土 → fail-closed。
        if (!hasApproaches && !gradeSeparated && node != null && network != null && config != null) {
            boolean nearTunnel = hasNearNodeTunnelEvidence(network, node, edgeResults, config);
            boolean deepCover = plazaHasDeepCover(
                safeFootprint, field, terrain, tunnelThreshold, liningThickness);
            if (nearTunnel && deepCover) {
                underground = true;
                failClosed = true;
                failReason = "tunnel evidence near node but collectTunnelApproaches empty under deep cover";
                diagnostics.add(failReason);
                String reason = failReason;
                LOG.warning(() -> "junction " + nodeId + " fail-closed: " + reason);
            }
        }

        Map<ColumnKey, ColumnRule> rules = new LinkedHashMap<>();
        Set<ColumnKey> protectedColumns = new LinkedHashSet<>();

        if (gradeSeparated) {
            for (ColumnKey column : safeFootprint.paveColumns()) {
                int designY = field.surfaceY(column);
                int surfaceY = sampleSurface(terrain, safeFootprint, column, coordinateTransformer);
                rules.put(column, new ColumnRule(
                    ColumnRole.GRADE_SEPARATED,
                    designY,
                    null,
                    surfaceY,
                    elevatedClearCap,
                    true));
            }
            return new JunctionConstructionContext(
                nodeId, safeFootprint, field, rules, protectedColumns,
                true, false, false, null, clearHeight, elevatedClearCap, diagnostics);
        }

        if (!hasApproaches) {
            if (failClosed) {
                for (ColumnKey column : safeFootprint.paveColumns()) {
                    int designY = field.surfaceY(column);
                    int surfaceY = sampleSurface(terrain, safeFootprint, column, coordinateTransformer);
                    rules.put(column, new ColumnRule(
                        ColumnRole.ENCLOSED_TUNNEL,
                        designY,
                        clearHeight != null ? designY + Math.max(1, clearHeight) : designY + 1,
                        surfaceY,
                        CLEAR_DENIED,
                        false));
                    protectedColumns.add(column);
                }
                return new JunctionConstructionContext(
                    nodeId, safeFootprint, field, rules, protectedColumns,
                    true, true, true, failReason, clearHeight, elevatedClearCap, diagnostics);
            }
            // 纯开敞平交
            return new JunctionConstructionContext(
                nodeId, safeFootprint, field, Map.of(), Set.of(),
                false, false, false, null, null, elevatedClearCap, diagnostics);
        }

        int resolvedClear = clearHeight != null ? Math.max(1, clearHeight) : 5;
        int lining = Math.max(1, liningThickness);
        int side = Math.max(0, sideClearance);
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            if (approach != null && approach.tunnelStyle() != null) {
                side = Math.max(side, Math.max(0, approach.tunnelStyle().sideClearance()));
            }
        }

        RoadTerrainClearanceUtils.BlockColumnResolver columns = columnResolver(coordinateTransformer);
        Function<RoadEdge, List<Vec2d>> centerlines = network != null
            ? RoadJunctionCenterlineResolver.forNetwork(network)
            : edge -> List.of();
        List<RoadEdge> connected = edges != null ? edges : List.of();
        Collection<RoadGenerationResult> carriers = approachCarriers(approaches, edgeResults);

        // 候选列 = 铺装外扩 ∪ 清障足迹：圆角外缘草地楔形多为 CLEARANCE_ONLY，
        // 若不纳入规则表，侧向外扩后又会落入 OPEN 露天挖穿。
        Set<ColumnKey> candidates = TunnelJunctionShellPass.expandPaveBySideClearancePublic(
            safeFootprint.paveColumns(), side);
        candidates = new LinkedHashSet<>(candidates);
        candidates.addAll(safeFootprint.clearance());
        // 必须传入路口填筑多边形：空多边形会使 open-volume 在第二采样点立即退出，
        // 洞腔掩膜几乎为空，圆角列被误标 OPEN 并露天挖穿山体。
        List<Vec2d> polygon = junctionPolygon != null && junctionPolygon.size() >= 3
            ? junctionPolygon
            : polygonFromFootprint(safeFootprint);
        Set<ColumnKey> approachCavity = TunnelJunctionShellPass.cavityColumnsFromOpenVolume(
            TunnelJunctionSeedResolver.collectOpenVolume(
                approaches,
                polygon,
                carriers,
                coordinateTransformer,
                plan -> {
                    ColumnKey key = new ColumnKey(columns.worldX(plan), columns.worldZ(plan));
                    return field.surfaceY(key);
                }));
        Set<ColumnKey> filletColumns = safeFootprint.filletAdditions();
        // 进口首段即为隧道族：真地下广场。开敞 stub（首段 ROAD、远端才隧道）为 false，
        // 允许清障外缘露天，避免「远处有洞」毒化开敞街角。
        boolean tunnelEntryPlaza = approachesHaveTunnelFamilyAtEntry(approaches, edgeResults);

        // 共享洞腔：进口 open-volume 扫掠到的封闭列一律保护（不要求最近中心线为隧道族）。
        // 圆角铺装/接缝始终保护；清障外缘仅在真隧道进口广场保护。
        for (ColumnKey column : candidates) {
            if (column == null) {
                continue;
            }
            Vec2d plan = planPoint(safeFootprint, column, coordinateTransformer);
            int designY = field.surfaceY(column);
            int surfaceY = sampleSurface(terrain, plan, column);
            boolean enclosed = TunnelJunctionShellPass.columnHasEnclosedCover(
                terrain, plan, column, designY, resolvedClear, lining, tunnelThreshold);
            boolean inApproachCavity = approachCavity.contains(column);
            boolean tunnelFamily = TunnelJunctionSeedResolver.isTunnelFamilyAtPlan(
                plan, connected, edgeResults != null ? edgeResults : Map.of(), centerlines);
            JunctionWorldFootprint.ElevationRole elevRole = safeFootprint.elevationRole(column);
            boolean filletZone = isFilletProtectionZone(
                elevRole, column, filletColumns, tunnelEntryPlaza);

            Integer roofY = designY + resolvedClear;
            if (flatTunnelCavityCap != null) {
                roofY = Math.min(roofY, flatTunnelCavityCap);
            }

            ColumnRole role;
            Integer maxClear;
            boolean destructive;
            boolean spatialProtect = inApproachCavity || tunnelFamily || filletZone;
            if (enclosed && spatialProtect) {
                role = ColumnRole.ENCLOSED_TUNNEL;
                maxClear = roofY;
                destructive = true;
                protectedColumns.add(column);
            } else if (spatialProtect) {
                // 浅覆 / 洞口 / 隧道路口圆角外缘：不得露天打穿；清障止于洞顶 cap。
                role = ColumnRole.PORTAL_TRANSITION;
                maxClear = roofY;
                destructive = true;
                if (filletZone) {
                    protectedColumns.add(column);
                }
            } else {
                role = ColumnRole.OPEN;
                maxClear = elevatedClearCap; // null = daylight
                destructive = true;
            }

            rules.put(column, new ColumnRule(
                role, designY, roofY, surfaceY, maxClear, destructive));

            diagnostics.add(String.format(
                "col=(%d,%d) role=%s elev=%s designY=%d roofY=%d surfaceY=%d clearCap=%s source=context",
                column.worldX(), column.worldZ(), role, elevRole, designY, roofY, surfaceY,
                maxClear == null ? "daylight" : String.valueOf(maxClear)));
        }

        if (protectedColumns.isEmpty() && hasApproaches) {
            // 有进口但掩膜为空：可能全是浅覆洞口；仍禁止整片 null 露天——未登记列走 portal 保守。
            diagnostics.add("tunnel approaches present but protected cavity empty; unlisted columns deny daylight");
            underground = true;
        }

        // 保护带外扩一圈：堵住与洞腔/圆角邻接但仍标 OPEN 的零星列，避免棋盘格穿孔。
        dilateProtectedClearCaps(rules, protectedColumns, resolvedClear, flatTunnelCavityCap, elevatedClearCap);

        boolean undergroundFinal = underground;
        LOG.fine(() -> "junction " + nodeId + " protectedColumns=" + protectedColumns.size()
            + " rules=" + rules.size() + " underground=" + undergroundFinal);

        return new JunctionConstructionContext(
            nodeId, safeFootprint, field, rules, protectedColumns,
            undergroundFinal, tunnelEntryPlaza, failClosed, failReason,
            resolvedClear, elevatedClearCap, diagnostics);
    }

    /**
     * 将仍为 OPEN/null 的邻接列升级为 PORTAL 洞顶 cap，消除保护边界上的零星露天穿孔。
     */
    static void dilateProtectedClearCaps(
            Map<ColumnKey, ColumnRule> rules,
            Set<ColumnKey> protectedColumns,
            int resolvedClear,
            Integer flatTunnelCavityCap,
            Integer elevatedClearCap) {
        if (rules == null || protectedColumns == null || protectedColumns.isEmpty()) {
            return;
        }
        List<ColumnKey> seeds = new java.util.ArrayList<>(protectedColumns);
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (ColumnKey seed : seeds) {
            if (seed == null) {
                continue;
            }
            for (int[] dir : dirs) {
                ColumnKey neighbor = new ColumnKey(seed.worldX() + dir[0], seed.worldZ() + dir[1]);
                ColumnRule existing = rules.get(neighbor);
                if (existing == null) {
                    continue;
                }
                if (existing.role() != ColumnRole.OPEN
                        || existing.maxAuthorizedClearY() != null) {
                    continue;
                }
                int designY = existing.designRoadY();
                int roofY = designY + Math.max(1, resolvedClear);
                if (flatTunnelCavityCap != null) {
                    roofY = Math.min(roofY, flatTunnelCavityCap);
                }
                if (elevatedClearCap != null) {
                    roofY = Math.min(roofY, elevatedClearCap);
                }
                rules.put(neighbor, new ColumnRule(
                    ColumnRole.PORTAL_TRANSITION,
                    designY,
                    roofY,
                    existing.naturalSurfaceY(),
                    roofY,
                    true));
                protectedColumns.add(neighbor);
            }
        }
    }

    private static boolean hasNearNodeTunnelEvidence(
            RoadNetwork network,
            RoadNode node,
            Map<String, RoadGenerationResult> edgeResults,
            RoadSystemConfig config) {
        if (edgeResults == null || edgeResults.isEmpty()) {
            return false;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            RoadGenerationResult result = edgeResults.get(edgeId);
            if (edge == null || result == null) {
                continue;
            }
            if (TunnelJunctionSeedResolver.edgeHasTunnelFamilyConstruction(result)) {
                ResolvedTunnelStyle style = com.plot.plugin.road.tunnel.TunnelStyle.resolve(
                    com.plot.plugin.road.model.RoadModelUtils.resolveRoad(network, edge), config);
                if (style != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean plazaHasDeepCover(
            JunctionWorldFootprint footprint,
            JunctionElevationField field,
            TerrainSampler terrain,
            int tunnelThreshold,
            int liningThickness) {
        if (terrain == null || footprint == null || footprint.paveColumns().isEmpty()) {
            return false;
        }
        int checked = 0;
        int deep = 0;
        for (ColumnKey column : footprint.paveColumns()) {
            if (column == null) {
                continue;
            }
            Vec2d plan = footprint.planPoint(column);
            if (plan == null) {
                plan = new Vec2d(column.worldX() + 0.5, column.worldZ() + 0.5);
            }
            int designY = field.surfaceY(column);
            if (TunnelJunctionShellPass.columnHasEnclosedCover(
                    terrain, plan, column, designY, 5, liningThickness, tunnelThreshold)) {
                deep++;
            }
            checked++;
            if (checked >= 32) {
                break;
            }
        }
        return deep >= Math.max(1, checked / 3);
    }

    private static int sampleSurface(
            TerrainSampler terrain,
            JunctionWorldFootprint footprint,
            ColumnKey column,
            ICoordinateService transformer) {
        return sampleSurface(terrain, planPoint(footprint, column, transformer), column);
    }

    private static int sampleSurface(TerrainSampler terrain, Vec2d plan, ColumnKey column) {
        if (terrain == null) {
            return 0;
        }
        if (plan != null) {
            return terrain.sampleSurfaceY(plan);
        }
        return terrain.sampleSurfaceY(new Vec2d(column.worldX() + 0.5, column.worldZ() + 0.5));
    }

    private static Vec2d planPoint(
            JunctionWorldFootprint footprint,
            ColumnKey column,
            ICoordinateService transformer) {
        if (footprint != null) {
            Vec2d plan = footprint.planPoint(column);
            if (plan != null) {
                return plan;
            }
        }
        if (column == null) {
            return null;
        }
        return new Vec2d(column.worldX() + 0.5, column.worldZ() + 0.5);
    }

    /**
     * 圆角保护带：圆角铺装与接缝始终纳入。
     * 清障楔形 / 邻接外缘仅在「进口首段为隧道族」的地下广场纳入——
     * 否则开敞 stub 街角无法露天清到地表。
     */
    static boolean isFilletProtectionZone(
            JunctionWorldFootprint.ElevationRole elevRole,
            ColumnKey column,
            Set<ColumnKey> filletColumns,
            boolean tunnelEntryPlaza) {
        if (elevRole == JunctionWorldFootprint.ElevationRole.FILLET
                || elevRole == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            return true;
        }
        if (!tunnelEntryPlaza) {
            return false;
        }
        if (elevRole == JunctionWorldFootprint.ElevationRole.CLEARANCE_ONLY) {
            return true;
        }
        return isAdjacentToFillet(column, filletColumns);
    }

    static boolean approachesHaveTunnelFamilyAtEntry(
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches,
            Map<String, RoadGenerationResult> edgeResults) {
        if (approaches == null || approaches.isEmpty() || edgeResults == null) {
            return false;
        }
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            if (approach == null || approach.edge() == null) {
                continue;
            }
            RoadGenerationResult result = edgeResults.get(approach.edge().getId());
            if (result == null || result.constructionTypes == null || result.constructionTypes.isEmpty()) {
                continue;
            }
            com.plot.plugin.road.RoadConstructionType type =
                com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier.constructionTypeAt(
                    result.constructionTypes, 0);
            if (type != null && type.isTunnelFamily()) {
                return true;
            }
        }
        return false;
    }

    static boolean isAdjacentToFillet(ColumnKey column, Set<ColumnKey> filletColumns) {
        if (column == null || filletColumns == null || filletColumns.isEmpty()) {
            return false;
        }
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] dir : dirs) {
            if (filletColumns.contains(
                    new ColumnKey(column.worldX() + dir[0], column.worldZ() + dir[1]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 足迹列外包矩形作为 open-volume 裁剪多边形兜底（优先使用调用方传入的 fillPolygon）。
     */
    private static List<Vec2d> polygonFromFootprint(JunctionWorldFootprint footprint) {
        if (footprint == null || footprint.paveColumns().isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ColumnKey key : footprint.paveColumns()) {
            if (key == null) {
                continue;
            }
            minX = Math.min(minX, key.worldX());
            maxX = Math.max(maxX, key.worldX());
            minZ = Math.min(minZ, key.worldZ());
            maxZ = Math.max(maxZ, key.worldZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return List.of();
        }
        // 外扩 1 格，避免边界采样被 pointInPolygon 立刻截断。
        return List.of(
            new Vec2d(minX - 1.5, minZ - 1.5),
            new Vec2d(maxX + 2.5, minZ - 1.5),
            new Vec2d(maxX + 2.5, maxZ + 2.5),
            new Vec2d(minX - 1.5, maxZ + 2.5));
    }

    private static Collection<RoadGenerationResult> approachCarriers(
            List<TunnelJunctionSeedResolver.TunnelApproach> approaches,
            Map<String, RoadGenerationResult> edgeResults) {
        Set<RoadGenerationResult> carriers = new LinkedHashSet<>();
        if (approaches == null || edgeResults == null) {
            return carriers;
        }
        for (TunnelJunctionSeedResolver.TunnelApproach approach : approaches) {
            if (approach == null || approach.edge() == null) {
                continue;
            }
            RoadGenerationResult result = edgeResults.get(approach.edge().getId());
            if (result != null) {
                carriers.add(result);
            }
        }
        return carriers;
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver columnResolver(
            ICoordinateService transformer) {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                if (planPoint == null) {
                    return 0;
                }
                if (transformer != null) {
                    return com.plot.plugin.road.RoadGeometryUtils.canvasToBlockXZ(
                        planPoint, transformer).getX();
                }
                return (int) Math.floor(planPoint.x);
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                if (planPoint == null) {
                    return 0;
                }
                if (transformer != null) {
                    return com.plot.plugin.road.RoadGeometryUtils.canvasToBlockXZ(
                        planPoint, transformer).getZ();
                }
                return (int) Math.floor(planPoint.y);
            }
        };
    }
}
