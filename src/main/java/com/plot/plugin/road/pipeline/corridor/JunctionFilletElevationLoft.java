package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.VoxelElevationDiscretizer;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 路口铺装标高场：圆角 gore 内沿两侧路缘放样；其余铺装/过渡列按最近进口路面与纵断面约束，
 * 禁止整片回退固定 {@code junctionY} 造成纵坡接缝台阶。
 */
public final class JunctionFilletElevationLoft {
    private static final double EPSILON = 1e-9;
    /** 切点到中心线允许的侧向距离（含宽路路缘）。 */
    private static final double TANGENT_OWNER_MAX_DIST = 16.0;
    private static final double APPROACH_BLEND_MAX_DIST = 24.0;
    /**
     * 路口核区半径下限：核内遗留边路不得 pin 为设计路面标高；
     * 实际半径按进口硬质半宽放大，避免宽路核区外缘误 pin。
     */
    static final double APPROACH_PIN_MIN_DIST = 6.0;

    private final int junctionY;
    private final Vec2d junctionPlan;
    private final double approachPinMinDist;
    private final List<FilletLoftCorner> corners;
    private final Map<ColumnKey, Integer> approachRoadY;
    private final List<ApproachElevationConstraint> approaches;
    private final RoadTerrainClearanceUtils.BlockColumnResolver columns;

    private JunctionFilletElevationLoft(
            int junctionY,
            Vec2d junctionPlan,
            double approachPinMinDist,
            List<FilletLoftCorner> corners,
            Map<ColumnKey, Integer> approachRoadY,
            List<ApproachElevationConstraint> approaches,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        this.junctionY = junctionY;
        this.junctionPlan = junctionPlan;
        this.approachPinMinDist = Math.max(APPROACH_PIN_MIN_DIST, approachPinMinDist);
        this.corners = List.copyOf(corners);
        this.approachRoadY = approachRoadY != null ? Map.copyOf(approachRoadY) : Map.of();
        this.approaches = approaches != null ? List.copyOf(approaches) : List.of();
        this.columns = columns;
    }

    public static JunctionFilletElevationLoft inactive(int junctionY) {
        return new JunctionFilletElevationLoft(
            junctionY, null, APPROACH_PIN_MIN_DIST, List.of(), Map.of(), List.of(), null);
    }

    public static JunctionFilletElevationLoft build(
            List<JunctionGeometryResult.FilletCorner> fillets,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        Vec2d junctionPlan = inferJunctionPlan(edges, network);
        double pinMinDist = resolveApproachPinMinDist(edges, network);
        Map<String, Map<ColumnKey, Integer>> roadYByEdge = indexRoadSurfaceYByEdge(edgeResults);
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        // 同列多进口：按最近中心线归属（与横断面材质归属一致），禁止盲目 Math::min。
        Map<ColumnKey, Integer> mergedRoadY = mergeApproachRoadY(roadYByEdge, edges, centerlines, columns);
        List<ApproachElevationConstraint> approachConstraints = buildApproachConstraints(
            edges, roadYByEdge, edgeResults, centerlines, junctionPlan, columns, pinMinDist);
        if (fillets == null || fillets.isEmpty() || columns == null) {
            return new JunctionFilletElevationLoft(
                junctionY, junctionPlan, pinMinDist, List.of(), mergedRoadY, approachConstraints, columns);
        }
        List<FilletLoftCorner> corners = new ArrayList<>();
        for (JunctionGeometryResult.FilletCorner fillet : fillets) {
            FilletLoftCorner corner = buildCorner(
                fillet, roadYByEdge, edgeResults, edges, network, centerlines, columns, junctionY);
            if (corner != null) {
                corners.add(corner);
            }
        }
        return new JunctionFilletElevationLoft(
            junctionY, junctionPlan, pinMinDist, corners, mergedRoadY, approachConstraints, columns);
    }

    public int junctionY() {
        return junctionY;
    }

    /**
     * 逐点路面标高：gore 内放样；否则优先命中进口已铺装列，再按进口中心线约束混合，最后才用 junctionY。
     * 无足迹时回退固定核区半径（兼容旧调用）。
     */
    public int surfaceY(Vec2d planPoint) {
        return surfaceY(planPoint, null);
    }

    /**
     * 足迹感知高程：{@link JunctionWorldFootprint.ElevationRole#ROAD_CORE} 独占列禁止遗留边路 pin；
     * 圆角/接缝/清障列可信任进口接口 Y（即使靠近交叉点）。
     */
    public int surfaceY(Vec2d planPoint, JunctionWorldFootprint footprint) {
        if (planPoint == null) {
            return junctionY;
        }
        ColumnKey key = columns != null
            ? new ColumnKey(columns.worldX(planPoint), columns.worldZ(planPoint))
            : null;
        // 同列已有进口路面：接缝/清障钉在该 Y。圆角 gore 必须走两条路缘放样，
        // 进口残留由接缝剥离，不能把补铺钉成两路拼贴台阶。
        if (key != null
                && !isFilletGore(planPoint)
                && allowExactApproachPin(key, planPoint, footprint)) {
            Integer exact = approachRoadY.get(key);
            if (exact != null) {
                return exact;
            }
        }
        boolean allowNeighborhoodPin = allowNeighborhoodApproachPin(key, planPoint, footprint);
        if (allowNeighborhoodPin && key != null) {
            Integer existing = approachRoadY.get(key);
            if (existing != null) {
                return existing;
            }
        }
        for (FilletLoftCorner corner : corners) {
            if (!RoadGeometryUtils.pointInPolygon(planPoint, corner.gore())) {
                continue;
            }
            return corner.loftY(planPoint, junctionY);
        }
        Integer pinned = lookupPinnedApproachRoadY(planPoint, footprint);
        if (pinned != null) {
            return pinned;
        }
        if (footprint != null && key != null) {
            JunctionWorldFootprint.ElevationRole role = footprint.elevationRole(key);
            if (role == JunctionWorldFootprint.ElevationRole.FILLET
                    || role == JunctionWorldFootprint.ElevationRole.ROAD_CORE) {
                // 核区保持路口共识，禁止把缓坡进口纵断面混进广场造成零星突起。
                return junctionY;
            }
        }
        Integer blended = blendApproachElevations(planPoint);
        return blended != null ? blended : junctionY;
    }

    /**
     * 同列 exact pin：核区仍拒绝遗留边路；FILLET/SEAM/清障可钉，避免圆角叠在进口路面之上。
     */
    private boolean allowExactApproachPin(
            ColumnKey key, Vec2d planPoint, JunctionWorldFootprint footprint) {
        if (columns == null) {
            return false;
        }
        if (footprint != null && !footprint.isEmpty() && key != null) {
            JunctionWorldFootprint.ElevationRole role = footprint.elevationRole(key);
            return role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION
                || role == JunctionWorldFootprint.ElevationRole.CLEARANCE_ONLY
                || role == JunctionWorldFootprint.ElevationRole.OUTSIDE;
        }
        return !insideJunctionCore(planPoint);
    }

    /**
     * 邻域 pin 仅接缝/清障外缘：gore 内禁止邻域污染，否则两进口会钉出棋盘台阶。
     */
    private boolean allowNeighborhoodApproachPin(
            ColumnKey key, Vec2d planPoint, JunctionWorldFootprint footprint) {
        if (columns == null) {
            return false;
        }
        if (footprint != null && !footprint.isEmpty() && key != null) {
            JunctionWorldFootprint.ElevationRole role = footprint.elevationRole(key);
            return role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION
                || role == JunctionWorldFootprint.ElevationRole.CLEARANCE_ONLY
                || role == JunctionWorldFootprint.ElevationRole.OUTSIDE;
        }
        if (isFilletGore(planPoint)) {
            return false;
        }
        return !insideJunctionCore(planPoint);
    }

    public boolean isFilletGore(Vec2d planPoint) {
        if (planPoint == null) {
            return false;
        }
        for (FilletLoftCorner corner : corners) {
            if (RoadGeometryUtils.pointInPolygon(planPoint, corner.gore())) {
                return true;
            }
        }
        return false;
    }

    private Integer lookupPinnedApproachRoadY(Vec2d planPoint, JunctionWorldFootprint footprint) {
        ColumnKey key = columns != null
            ? new ColumnKey(columns.worldX(planPoint), columns.worldZ(planPoint))
            : null;
        if (columns == null || approachRoadY.isEmpty()
                || !allowNeighborhoodApproachPin(key, planPoint, footprint)) {
            return null;
        }
        int wx = columns.worldX(planPoint);
        int wz = columns.worldZ(planPoint);
        Integer exact = approachRoadY.get(new ColumnKey(wx, wz));
        if (exact != null) {
            return exact;
        }
        Integer near = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ColumnKey neighbor = new ColumnKey(wx + dx, wz + dz);
                if (footprint != null && !footprint.isEmpty()
                        && footprint.elevationRole(neighbor)
                        == JunctionWorldFootprint.ElevationRole.ROAD_CORE) {
                    continue;
                }
                Integer y = approachRoadY.get(neighbor);
                if (y == null) {
                    continue;
                }
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist < bestDist) {
                    bestDist = dist;
                    near = y;
                }
            }
        }
        return near;
    }

    Map<ColumnKey, Integer> approachRoadY() {
        return approachRoadY;
    }

    private boolean insideJunctionCore(Vec2d planPoint) {
        return insideJunctionCore(planPoint, junctionPlan, approachPinMinDist);
    }

    private static boolean insideJunctionCore(Vec2d planPoint, Vec2d junctionPlan, double pinMinDist) {
        if (planPoint == null || junctionPlan == null) {
            return false;
        }
        double radius = pinMinDist > 0.0 ? pinMinDist : APPROACH_PIN_MIN_DIST;
        return planPoint.distance(junctionPlan) < radius;
    }

    private Integer blendApproachElevations(Vec2d planPoint) {
        if (approaches.isEmpty()) {
            return null;
        }
        double weightSum = 0.0;
        double elevSum = 0.0;
        for (ApproachElevationConstraint approach : approaches) {
            Projection proj = projectToPolyline(planPoint, approach.centerline());
            if (proj == null || proj.distance > APPROACH_BLEND_MAX_DIST) {
                continue;
            }
            Integer y = approach.elevationAt(proj.station);
            if (y == null) {
                continue;
            }
            double weight = 1.0 / Math.max(proj.distance, 0.25);
            weightSum += weight;
            elevSum += weight * y;
        }
        if (weightSum <= EPSILON) {
            return null;
        }
        return (int) Math.round(elevSum / weightSum);
    }

    /**
     * 同层纵坡/路缘冲突的最大 Y 跨度；超过则视为立交叠层，只在低层带内选归属。
     */
    static final int SAME_LEVEL_Y_SPAN = 3;

    /**
     * 同世界列多进口路面 Y：
     * <ul>
     *   <li>同层（ΔY ≤ {@link #SAME_LEVEL_Y_SPAN}）：归属最近中心线进口，与横断面材质一致</li>
     *   <li>立交叠层（ΔY 更大）：只保留最低层带（minY..minY+1），禁止抬到桥面</li>
     * </ul>
     * 距离并列时取字典序更小的 edgeId，保证确定性。
     */
    static Map<ColumnKey, Integer> mergeApproachRoadY(
            Map<String, Map<ColumnKey, Integer>> roadYByEdge,
            List<RoadEdge> edges,
            Function<RoadEdge, List<Vec2d>> centerlines,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        Map<ColumnKey, Integer> merged = new HashMap<>();
        if (roadYByEdge == null || edges == null) {
            return merged;
        }
        Map<ColumnKey, List<ApproachYCandidate>> candidates = new HashMap<>();
        for (RoadEdge edge : edges) {
            if (edge == null || edge.getId() == null) {
                continue;
            }
            Map<ColumnKey, Integer> perEdge = roadYByEdge.get(edge.getId());
            if (perEdge == null || perEdge.isEmpty()) {
                continue;
            }
            List<Vec2d> centerline = centerlines != null ? centerlines.apply(edge) : null;
            for (Map.Entry<ColumnKey, Integer> entry : perEdge.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                ColumnKey key = entry.getKey();
                double dist = distanceColumnToCenterline(key, centerline, columns);
                candidates.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new ApproachYCandidate(edge.getId(), entry.getValue(), dist));
            }
        }
        for (Map.Entry<ColumnKey, List<ApproachYCandidate>> entry : candidates.entrySet()) {
            ApproachYCandidate chosen = selectApproachYOwner(entry.getValue());
            if (chosen != null) {
                merged.put(entry.getKey(), chosen.y());
            }
        }
        return merged;
    }

    static ApproachYCandidate selectApproachYOwner(List<ApproachYCandidate> all) {
        if (all == null || all.isEmpty()) {
            return null;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (ApproachYCandidate candidate : all) {
            if (candidate == null) {
                continue;
            }
            minY = Math.min(minY, candidate.y());
            maxY = Math.max(maxY, candidate.y());
        }
        if (minY == Integer.MAX_VALUE) {
            return null;
        }
        boolean gradeSeparatedStack = maxY - minY > SAME_LEVEL_Y_SPAN;
        int bandTop = gradeSeparatedStack ? minY + 1 : maxY;
        ApproachYCandidate best = null;
        for (ApproachYCandidate candidate : all) {
            if (candidate == null || candidate.y() > bandTop) {
                continue;
            }
            if (best == null
                    || candidate.dist() < best.dist() - EPSILON
                    || (Math.abs(candidate.dist() - best.dist()) <= EPSILON
                        && candidate.edgeId().compareTo(best.edgeId()) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    record ApproachYCandidate(String edgeId, int y, double dist) {
    }

    /**
     * 世界列到画布中心线的距离：沿中心线采样并经 {@code columns} 投到世界 XZ，避免画布/世界混比。
     */
    static double distanceColumnToCenterline(
            ColumnKey key,
            List<Vec2d> centerline,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        if (key == null || centerline == null || centerline.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        double cx = key.worldX() + 0.5;
        double cz = key.worldZ() + 0.5;
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d a = centerline.get(i);
            Vec2d b = centerline.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            double len = a.distance(b);
            int steps = Math.max(1, (int) Math.ceil(len * 2.0));
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / (double) steps;
                Vec2d sample = a.lerp(b, t);
                double wx;
                double wz;
                if (columns != null) {
                    wx = columns.worldX(sample) + 0.5;
                    wz = columns.worldZ(sample) + 0.5;
                } else {
                    wx = sample.x;
                    wz = sample.y;
                }
                best = Math.min(best, Math.hypot(cx - wx, cz - wz));
            }
        }
        return best;
    }

    static double resolveApproachPinMinDist(List<RoadEdge> edges, RoadNetwork network) {
        double radius = APPROACH_PIN_MIN_DIST;
        if (edges == null || network == null) {
            return radius;
        }
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            Road road = network.getRoadForEdge(edge);
            if (road == null || road.getWidth() == null) {
                continue;
            }
            double half = Math.max(0.5, road.getWidth() / 2.0);
            // 半宽 + 路缘裕量，覆盖宽路实际路口核。
            radius = Math.max(radius, half + 2.0);
        }
        return radius;
    }

    private static List<ApproachElevationConstraint> buildApproachConstraints(
            List<RoadEdge> edges,
            Map<String, Map<ColumnKey, Integer>> roadYByEdge,
            Map<String, RoadGenerationResult> edgeResults,
            Function<RoadEdge, List<Vec2d>> centerlines,
            Vec2d junctionPlan,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            double pinMinDist) {
        List<ApproachElevationConstraint> constraints = new ArrayList<>();
        if (edges == null || centerlines == null) {
            return constraints;
        }
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            List<Vec2d> centerline = centerlines.apply(edge);
            if (centerline == null || centerline.size() < 2) {
                continue;
            }
            Map<ColumnKey, Integer> scoped = scopedRoadY(edge, roadYByEdge);
            RoadGenerationResult result = edgeResults != null ? edgeResults.get(edge.getId()) : null;
            constraints.add(new ApproachElevationConstraint(
                centerline, scoped, result, junctionPlan, columns, pinMinDist));
        }
        return constraints;
    }

    /**
     * 责任边端点中出现次数最多的节点视为路口中心（立交下层 edges 已不含上层跨线）。
     */
    private static Vec2d inferJunctionPlan(List<RoadEdge> edges, RoadNetwork network) {
        if (edges == null || network == null || edges.isEmpty()) {
            return null;
        }
        Map<String, Integer> endpointHits = new HashMap<>();
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            if (edge.getStartNodeId() != null) {
                endpointHits.merge(edge.getStartNodeId(), 1, Integer::sum);
            }
            if (edge.getEndNodeId() != null) {
                endpointHits.merge(edge.getEndNodeId(), 1, Integer::sum);
            }
        }
        String bestId = null;
        int bestHits = 0;
        for (Map.Entry<String, Integer> entry : endpointHits.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int hits = entry.getValue();
            // 平票时按 nodeId 稳定选取，避免 HashMap 遍历顺序导致路口中心抖动。
            if (hits > bestHits
                    || (hits == bestHits && (bestId == null || entry.getKey().compareTo(bestId) < 0))) {
                bestHits = hits;
                bestId = entry.getKey();
            }
        }
        RoadNode node = bestId != null ? network.getNode(bestId) : null;
        return node != null ? node.getPosition() : null;
    }

    private static FilletLoftCorner buildCorner(
            JunctionGeometryResult.FilletCorner fillet,
            Map<String, Map<ColumnKey, Integer>> roadYByEdge,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            Function<RoadEdge, List<Vec2d>> centerlines,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        List<Vec2d> gore = RoadJunctionGenerator.filletGorePolygon(fillet);
        if (gore.size() < 3 || fillet.center() == null) {
            return null;
        }
        double sweep = fillet.sweepRadians();
        if (!Double.isFinite(sweep) || Math.abs(sweep) < EPSILON) {
            sweep = FilletArcSweep.resolveShort(
                fillet.center(), fillet.tangentStart(), fillet.tangentEnd());
        }
        Vec2d sharp = fillet.sharpCorner() != null
            ? fillet.sharpCorner()
            : RoadJunctionGenerator.reconstructFilletSharpCorner(fillet, sweep);
        if (sharp == null
                || fillet.tangentStart() == null
                || fillet.tangentEnd() == null) {
            return null;
        }
        Vec2d dirA = fillet.tangentStart().subtract(sharp);
        Vec2d dirB = fillet.tangentEnd().subtract(sharp);
        if (dirA.lengthSquared() < EPSILON || dirB.lengthSquared() < EPSILON) {
            return null;
        }
        dirA = dirA.normalize();
        dirB = dirB.normalize();
        // 优先几何生成写入的切点归属；仅缺省时回退最近中心线。
        RoadEdge ownerA = edgeById(fillet.ownerStartEdgeId(), edges);
        if (ownerA == null) {
            ownerA = resolveTangentOwner(fillet.tangentStart(), edges, centerlines);
        }
        RoadEdge ownerB = edgeById(fillet.ownerEndEdgeId(), edges);
        if (ownerB == null) {
            ownerB = resolveTangentOwner(fillet.tangentEnd(), edges, centerlines);
        }
        CurbElevationCurve curbA = sampleCurb(
            sharp, dirA, fillet.tangentStart(), ownerA, roadYByEdge, edgeResults, centerlines, columns,
            junctionY, gore);
        CurbElevationCurve curbB = sampleCurb(
            sharp, dirB, fillet.tangentEnd(), ownerB, roadYByEdge, edgeResults, centerlines, columns,
            junctionY, gore);
        return new FilletLoftCorner(gore, sharp, dirA, dirB, curbA, curbB);
    }

    static RoadEdge edgeById(String edgeId, List<RoadEdge> edges) {
        if (edgeId == null || edgeId.isBlank() || edges == null) {
            return null;
        }
        for (RoadEdge edge : edges) {
            if (edge != null && edgeId.equals(edge.getId())) {
                return edge;
            }
        }
        return null;
    }

    /**
     * 切点归属回退：距中心线最近且在侧向容差内的进口边。
     * 不等宽场景应优先使用 {@link JunctionGeometryResult.FilletCorner} 携带的 owner id。
     */
    static RoadEdge resolveTangentOwner(
            Vec2d tangent,
            List<RoadEdge> edges,
            Function<RoadEdge, List<Vec2d>> centerlines) {
        if (tangent == null || edges == null || centerlines == null) {
            return null;
        }
        RoadEdge best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            List<Vec2d> centerline = centerlines.apply(edge);
            if (centerline == null || centerline.size() < 2) {
                continue;
            }
            Projection proj = projectToPolyline(tangent, centerline);
            if (proj == null || proj.distance >= bestDist) {
                continue;
            }
            bestDist = proj.distance;
            best = edge;
        }
        if (best == null || bestDist > TANGENT_OWNER_MAX_DIST) {
            return null;
        }
        return best;
    }

    private static CurbElevationCurve sampleCurb(
            Vec2d origin,
            Vec2d direction,
            Vec2d tangentPoint,
            RoadEdge ownerEdge,
            Map<String, Map<ColumnKey, Integer>> roadYByEdge,
            Map<String, RoadGenerationResult> edgeResults,
            Function<RoadEdge, List<Vec2d>> centerlines,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY,
            List<Vec2d> filletGore) {
        Map<ColumnKey, Integer> scoped = scopedRoadY(ownerEdge, roadYByEdge);
        // 路缘两端都取所属进口真实标高（纵断面优先），不再把锐角点钉死在 junctionY。
        // 从路口缓坡抬升时，锐角点已经离开节点半宽，强制 junctionY 会让轨的坡比道路更陡，
        // 圆角一侧提前抬升并高出路面。direction 定义放样轨方向，标高只取两端。
        // 锐点常落在 gore 内：禁止用同列进口残留作起点（接缝稍后剥离），否则补铺被钉成残留 Y。
        // 切点在路缘真实站：允许 gore 边界精确命中。邻域一律要求贴齐路缘射线，避免偏列污染（Y=90）。
        int startY = lookupElevation(
            origin, scoped, ownerEdge, edgeResults, centerlines, columns, junctionY, filletGore,
            origin, tangentPoint, false);
        int endY = startY;
        double length = 0.0;
        if (tangentPoint != null) {
            length = origin.distance(tangentPoint);
            endY = lookupElevation(
                tangentPoint, scoped, ownerEdge, edgeResults, centerlines, columns, junctionY, filletGore,
                origin, tangentPoint, true);
            if (length <= EPSILON) {
                startY = endY;
                length = 0.0;
            }
        }
        return new CurbElevationCurve(startY, endY, length);
    }

    /**
     * 沿路缘采样序列强制相邻 |ΔY|≤1（双向扫），消除离散路面查找引入的尖峰。
     */
    static void smoothCurbElevations(List<Integer> elevations) {
        if (elevations == null || elevations.size() < 2) {
            return;
        }
        for (int i = 1; i < elevations.size(); i++) {
            int prev = elevations.get(i - 1);
            int cur = elevations.get(i);
            if (cur > prev + 1) {
                elevations.set(i, prev + 1);
            } else if (cur < prev - 1) {
                elevations.set(i, prev - 1);
            }
        }
        for (int i = elevations.size() - 2; i >= 0; i--) {
            int next = elevations.get(i + 1);
            int cur = elevations.get(i);
            if (cur > next + 1) {
                elevations.set(i, next + 1);
            } else if (cur < next - 1) {
                elevations.set(i, next - 1);
            }
        }
    }

    /**
     * 在切点真实站距插入（或精确覆盖）采样，保证 curb.at(pinS) == pinY。
     * 不再把切点并入 ±0.5 的整数格，避免插值后切点 Y 漂移。
     */
    static void pinElevation(
            List<Double> distances,
            List<Double> elevations,
            double pinS,
            double pinY) {
        if (distances == null || elevations == null || distances.isEmpty() || pinS < 0.0) {
            return;
        }
        for (int i = 0; i < distances.size(); i++) {
            if (Math.abs(distances.get(i) - pinS) < EPSILON) {
                elevations.set(i, pinY);
                return;
            }
        }
        for (int i = 0; i < distances.size(); i++) {
            if (distances.get(i) > pinS) {
                distances.add(i, pinS);
                elevations.add(i, pinY);
                return;
            }
        }
        distances.add(pinS);
        elevations.add(pinY);
    }

    private static Map<ColumnKey, Integer> scopedRoadY(
            RoadEdge ownerEdge,
            Map<String, Map<ColumnKey, Integer>> roadYByEdge) {
        if (ownerEdge == null || roadYByEdge == null) {
            return Map.of();
        }
        Map<ColumnKey, Integer> scoped = roadYByEdge.get(ownerEdge.getId());
        return scoped != null ? scoped : Map.of();
    }

    private static int lookupElevation(
            Vec2d planPoint,
            Map<ColumnKey, Integer> scopedRoadY,
            RoadEdge ownerEdge,
            Map<String, RoadGenerationResult> edgeResults,
            Function<RoadEdge, List<Vec2d>> centerlines,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY,
            List<Vec2d> filletGore,
            Vec2d curbOrigin,
            Vec2d curbTangent,
            boolean allowGoreExact) {
        // 优先连续纵断面：离散路面列含 Minecraft 台阶噪声，直接 loft 会让圆角看起来粗糙。
        Integer fromProfile = elevationFromOwnedProfile(
            planPoint, ownerEdge, edgeResults, centerlines);
        if (fromProfile != null) {
            return fromProfile;
        }
        // 锐点由圆心/三角函数重建时常为 1.999…；floor 会落到旁列，再被 gore 剥离后
        // 邻域误命中偏离路缘的污染标高（集成测试中的 Y=90）。近整数先吸附再投影。
        Vec2d sample = snapNearIntegerPlan(planPoint);
        int wx = columns.worldX(sample);
        int wz = columns.worldZ(sample);
        Integer exact = scopedRoadY.get(new ColumnKey(wx, wz));
        boolean exactInGore = columnInsideFilletGore(wx, wz, filletGore, columns);
        if (exact != null && (!exactInGore || allowGoreExact)) {
            return exact;
        }
        Integer near = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = wx + dx;
                int nz = wz + dz;
                Integer y = scopedRoadY.get(new ColumnKey(nx, nz));
                if (y == null || columnInsideFilletGore(nx, nz, filletGore, columns)) {
                    continue;
                }
                if (!columnNearCurbRay(nx, nz, curbOrigin, curbTangent, columns)) {
                    continue;
                }
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist < bestDist) {
                    bestDist = dist;
                    near = y;
                }
            }
        }
        if (near != null) {
            return near;
        }
        return junctionY;
    }

    /** 邻域命中须贴近路缘段，排除 gore 外偏列污染。 */
    private static boolean columnNearCurbRay(
            int worldX,
            int worldZ,
            Vec2d curbOrigin,
            Vec2d curbTangent,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        if (curbOrigin == null || curbTangent == null || columns == null) {
            return true;
        }
        Vec2d a = snapNearIntegerPlan(curbOrigin);
        Vec2d b = snapNearIntegerPlan(curbTangent);
        Vec2d ab = b.subtract(a);
        double lenSq = ab.lengthSquared();
        if (lenSq < EPSILON) {
            return true;
        }
        Vec2d p = new Vec2d(worldX + 0.5, worldZ + 0.5);
        double t = Math.clamp(p.subtract(a).dot(ab) / lenSq, 0.0, 1.0);
        double lateral = p.distance(a.add(ab.multiply(t)));
        return lateral <= 1.25;
    }

    /** 将距整数 ≤ 1e-9 的平面坐标吸附到整数，避免 floor(1.999…) 落入旁列。 */
    static Vec2d snapNearIntegerPlan(Vec2d planPoint) {
        if (planPoint == null) {
            return null;
        }
        double x = planPoint.x;
        double y = planPoint.y;
        long rx = Math.round(x);
        long ry = Math.round(y);
        boolean snapX = Math.abs(x - rx) <= 1e-9;
        boolean snapY = Math.abs(y - ry) <= 1e-9;
        if (!snapX && !snapY) {
            return planPoint;
        }
        return new Vec2d(snapX ? rx : x, snapY ? ry : y);
    }

    private static boolean columnInsideFilletGore(
            int worldX,
            int worldZ,
            List<Vec2d> filletGore,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        if (filletGore == null || filletGore.size() < 3) {
            return false;
        }
        return RoadGeometryUtils.pointInPolygon(new Vec2d(worldX + 0.5, worldZ + 0.5), filletGore);
    }

    private static Integer elevationFromOwnedProfile(
            Vec2d planPoint,
            RoadEdge ownerEdge,
            Map<String, RoadGenerationResult> edgeResults,
            Function<RoadEdge, List<Vec2d>> centerlines) {
        if (ownerEdge == null || edgeResults == null || planPoint == null || centerlines == null) {
            return null;
        }
        List<Vec2d> centerline = centerlines.apply(ownerEdge);
        if (centerline == null || centerline.size() < 2) {
            return null;
        }
        Projection proj = projectToPolyline(planPoint, centerline);
        if (proj == null || proj.distance > TANGENT_OWNER_MAX_DIST) {
            return null;
        }
        RoadGenerationResult result = edgeResults.get(ownerEdge.getId());
        if (result == null || result.buildProfile == null || !result.buildProfile.isActive()) {
            return null;
        }
        return result.buildProfile.elevationAtWorldStation(proj.station);
    }

    private static Map<String, Map<ColumnKey, Integer>> indexRoadSurfaceYByEdge(
            Map<String, RoadGenerationResult> edgeResults) {
        Map<String, Map<ColumnKey, Integer>> byEdge = new HashMap<>();
        if (edgeResults == null) {
            return byEdge;
        }
        for (Map.Entry<String, RoadGenerationResult> edgeEntry : edgeResults.entrySet()) {
            String edgeId = edgeEntry.getKey();
            RoadGenerationResult result = edgeEntry.getValue();
            if (edgeId == null || result == null || result.placementRecords == null) {
                continue;
            }
            Map<ColumnKey, Integer> roadY = byEdge.computeIfAbsent(edgeId, ignored -> new HashMap<>());
            for (Map.Entry<BlockPos, ?> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos == null) {
                    continue;
                }
                RoadSolidLayer layer = result.placementLayers != null
                    ? result.placementLayers.get(pos) : null;
                if (layer != RoadSolidLayer.ROAD) {
                    continue;
                }
                if (entry.getValue() instanceof com.plot.core.command.BlockRecord record
                        && "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                ColumnKey key = new ColumnKey(pos.getX(), pos.getZ());
                roadY.merge(key, pos.getY(), Math::max);
            }
        }
        return byEdge;
    }

    private static Projection projectToPolyline(Vec2d point, List<Vec2d> polyline) {
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStation = 0.0;
        double walked = 0.0;
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec2d a = polyline.get(i);
            Vec2d b = polyline.get(i + 1);
            Vec2d ab = b.subtract(a);
            double len = ab.length();
            if (len < EPSILON) {
                continue;
            }
            double t = Math.clamp(point.subtract(a).dot(ab) / (len * len), 0.0, 1.0);
            Vec2d proj = a.add(ab.multiply(t));
            double dist = point.distance(proj);
            if (dist < bestDist) {
                bestDist = dist;
                bestStation = walked + t * len;
            }
            walked += len;
        }
        if (!Double.isFinite(bestDist)) {
            return null;
        }
        return new Projection(bestDist, bestStation);
    }

    private record Projection(double distance, double station) {
    }

    private record ApproachElevationConstraint(
            List<Vec2d> centerline,
            Map<ColumnKey, Integer> roadYByColumn,
            RoadGenerationResult edgeResult,
            Vec2d junctionPlan,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            double pinMinDist) {
        Integer elevationAt(double station) {
            if (edgeResult != null
                    && edgeResult.buildProfile != null
                    && edgeResult.buildProfile.isActive()) {
                return edgeResult.buildProfile.elevationAtWorldStation(station);
            }
            if (roadYByColumn == null || roadYByColumn.isEmpty() || centerline == null || centerline.isEmpty()) {
                return null;
            }
            // 无纵断面时：取中心线附近已铺装列；核内遗留边路忽略。
            Vec2d sample = pointAtStation(centerline, station);
            if (sample == null || insideJunctionCore(sample, junctionPlan, pinMinDist)) {
                return null;
            }
            // 统一在世界 XZ 比较：sample 是画布点，须经 columns 投影后再与 ColumnKey 比距。
            double sampleWorldX;
            double sampleWorldZ;
            if (columns != null) {
                sampleWorldX = columns.worldX(sample) + 0.5;
                sampleWorldZ = columns.worldZ(sample) + 0.5;
            } else {
                sampleWorldX = sample.x;
                sampleWorldZ = sample.y;
            }
            Integer best = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (Map.Entry<ColumnKey, Integer> entry : roadYByColumn.entrySet()) {
                ColumnKey key = entry.getKey();
                if (key == null || entry.getValue() == null) {
                    continue;
                }
                double dist = Math.hypot(
                    key.worldX() + 0.5 - sampleWorldX,
                    key.worldZ() + 0.5 - sampleWorldZ);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entry.getValue();
                }
            }
            return bestDist <= 3.0 ? best : null;
        }
    }

    private static Vec2d pointAtStation(List<Vec2d> centerline, double station) {
        if (centerline == null || centerline.isEmpty()) {
            return null;
        }
        if (station <= 0.0) {
            return centerline.getFirst();
        }
        double walked = 0.0;
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d a = centerline.get(i);
            Vec2d b = centerline.get(i + 1);
            double len = a.distance(b);
            if (len < EPSILON) {
                continue;
            }
            if (walked + len >= station) {
                double t = (station - walked) / len;
                return a.lerp(b, t);
            }
            walked += len;
        }
        return centerline.getLast();
    }

    private record CurbElevationCurve(int startY, int endY, double length) {
        int at(double s) {
            if (length <= EPSILON) {
                return startY;
            }
            double t = Math.clamp(s / length, 0.0, 1.0);
            // 与道路纵断面同一套台阶：floor(ratio*|ΔY|)，缓坡未走完前不提前抬一格。
            return VoxelElevationDiscretizer.linearElevationAtRatio(startY, endY, t);
        }
    }

    private record FilletLoftCorner(
            List<Vec2d> gore,
            Vec2d sharp,
            Vec2d dirA,
            Vec2d dirB,
            CurbElevationCurve curbA,
            CurbElevationCurve curbB) {
        int loftY(Vec2d planPoint, int fallback) {
            double s = planPoint.distance(sharp);
            int ya = curbA.at(s);
            int yb = curbB.at(s);
            double u = angularBlend(sharp, dirA, dirB, planPoint);
            if (u <= 0.15) {
                return ya;
            }
            if (u >= 0.85) {
                return yb;
            }
            return VoxelElevationDiscretizer.linearElevationAtRatio(ya, yb, u);
        }
    }

    /** 锐点处从路缘 A 扫到 B 的角向参数 ∈ [0,1]。 */
    static double angularBlend(Vec2d sharp, Vec2d dirA, Vec2d dirB, Vec2d point) {
        Vec2d v = point.subtract(sharp);
        if (v.lengthSquared() < EPSILON) {
            return 0.5;
        }
        double a0 = Math.atan2(dirA.y, dirA.x);
        double a1 = Math.atan2(dirB.y, dirB.x);
        double ap = Math.atan2(v.y, v.x);
        double sweep = wrapToPi(a1 - a0);
        double fromStart = wrapToPi(ap - a0);
        if (sweep < 0.0) {
            sweep = -sweep;
            fromStart = -fromStart;
        }
        if (fromStart < 0.0) {
            fromStart = 0.0;
        }
        if (fromStart > sweep) {
            fromStart = sweep;
        }
        return sweep < EPSILON ? 0.5 : fromStart / sweep;
    }

    private static double wrapToPi(double angle) {
        double a = angle;
        while (a <= -Math.PI) {
            a += Math.PI * 2.0;
        }
        while (a > Math.PI) {
            a -= Math.PI * 2.0;
        }
        return a;
    }
}
