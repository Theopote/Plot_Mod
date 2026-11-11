package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
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
    private static final double MAX_CURB_SAMPLE = 24.0;
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
     */
    public int surfaceY(Vec2d planPoint) {
        if (planPoint == null) {
            return junctionY;
        }
        // A gore can overlap an approach voxel after rasterization. Share its exact
        // elevation rather than lofting a second, disconnected surface in that column.
        if (columns != null && !insideJunctionCore(planPoint)) {
            Integer existing = approachRoadY.get(new ColumnKey(
                columns.worldX(planPoint), columns.worldZ(planPoint)));
            if (existing != null) return existing;
        }
        for (FilletLoftCorner corner : corners) {
            if (!RoadGeometryUtils.pointInPolygon(planPoint, corner.gore())) {
                continue;
            }
            return corner.loftY(planPoint, junctionY);
        }
        Integer pinned = lookupPinnedApproachRoadY(planPoint);
        if (pinned != null) {
            return pinned;
        }
        Integer blended = blendApproachElevations(planPoint);
        return blended != null ? blended : junctionY;
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

    private Integer lookupPinnedApproachRoadY(Vec2d planPoint) {
        if (columns == null || approachRoadY.isEmpty() || insideJunctionCore(planPoint)) {
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
                Integer y = approachRoadY.get(new ColumnKey(wx + dx, wz + dz));
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
            sharp, dirA, fillet.tangentStart(), ownerA, roadYByEdge, edgeResults, centerlines, columns, junctionY);
        CurbElevationCurve curbB = sampleCurb(
            sharp, dirB, fillet.tangentEnd(), ownerB, roadYByEdge, edgeResults, centerlines, columns, junctionY);
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
            int junctionY) {
        Map<ColumnKey, Integer> scoped = scopedRoadY(ownerEdge, roadYByEdge);
        List<Double> distances = new ArrayList<>();
        List<Integer> elevations = new ArrayList<>();
        distances.add(0.0);
        // 锐角点通常不在任一路面列上：用路口共识，避免两侧进口互串。
        elevations.add(junctionY);
        for (double s = 1.0; s <= MAX_CURB_SAMPLE; s += 1.0) {
            Vec2d sample = origin.add(direction.multiply(s));
            int y = lookupElevation(
                sample, scoped, ownerEdge, edgeResults, centerlines, columns, junctionY);
            distances.add(s);
            elevations.add(y);
        }
        // 切点处强制钉在所属进口路面高程，保证与原路缘衔接。
        if (tangentPoint != null) {
            double pinS = origin.distance(tangentPoint);
            int tangentY = lookupElevation(
                tangentPoint, scoped, ownerEdge, edgeResults, centerlines, columns, junctionY);
            pinElevation(distances, elevations, pinS, tangentY);
        }
        return new CurbElevationCurve(distances, elevations);
    }

    /**
     * 在切点真实站距插入（或精确覆盖）采样，保证 curb.at(pinS) == pinY。
     * 不再把切点并入 ±0.5 的整数格，避免插值后切点 Y 漂移。
     */
    static void pinElevation(
            List<Double> distances,
            List<Integer> elevations,
            double pinS,
            int pinY) {
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
            int junctionY) {
        int wx = columns.worldX(planPoint);
        int wz = columns.worldZ(planPoint);
        Integer exact = scopedRoadY.get(new ColumnKey(wx, wz));
        if (exact != null) {
            return exact;
        }
        Integer near = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Integer y = scopedRoadY.get(new ColumnKey(wx + dx, wz + dz));
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
        if (near != null) {
            return near;
        }
        Integer fromProfile = elevationFromOwnedProfile(
            planPoint, ownerEdge, edgeResults, centerlines);
        return fromProfile != null ? fromProfile : junctionY;
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

    private record CurbElevationCurve(List<Double> distances, List<Integer> elevations) {
        int at(double s) {
            if (distances.isEmpty()) {
                return 0;
            }
            if (s <= distances.getFirst()) {
                return elevations.getFirst();
            }
            if (s >= distances.getLast()) {
                return elevations.getLast();
            }
            for (int i = 0; i < distances.size() - 1; i++) {
                double d0 = distances.get(i);
                double d1 = distances.get(i + 1);
                if (s > d1) {
                    continue;
                }
                double t = d1 - d0 < EPSILON ? 0.0 : (s - d0) / (d1 - d0);
                return (int) Math.round(elevations.get(i) + t * (elevations.get(i + 1) - elevations.get(i)));
            }
            return elevations.getLast();
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
            return (int) Math.round((1.0 - u) * ya + u * yb);
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
