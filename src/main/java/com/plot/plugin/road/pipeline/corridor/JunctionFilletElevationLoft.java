package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver;
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
     * 路口核区半径：核内遗留边路不得 pin 为设计路面标高（应交清掉并按 junctionY 重建）；
     * 核外进口已铺装列仍可 pin，避免纵坡接缝台阶。
     */
    static final double APPROACH_PIN_MIN_DIST = 6.0;

    private final int junctionY;
    private final Vec2d junctionPlan;
    private final List<FilletLoftCorner> corners;
    private final Map<ColumnKey, Integer> approachRoadY;
    private final List<ApproachElevationConstraint> approaches;
    private final RoadTerrainClearanceUtils.BlockColumnResolver columns;

    private JunctionFilletElevationLoft(
            int junctionY,
            Vec2d junctionPlan,
            List<FilletLoftCorner> corners,
            Map<ColumnKey, Integer> approachRoadY,
            List<ApproachElevationConstraint> approaches,
            RoadTerrainClearanceUtils.BlockColumnResolver columns) {
        this.junctionY = junctionY;
        this.junctionPlan = junctionPlan;
        this.corners = List.copyOf(corners);
        this.approachRoadY = approachRoadY != null ? Map.copyOf(approachRoadY) : Map.of();
        this.approaches = approaches != null ? List.copyOf(approaches) : List.of();
        this.columns = columns;
    }

    public static JunctionFilletElevationLoft inactive(int junctionY) {
        return new JunctionFilletElevationLoft(junctionY, null, List.of(), Map.of(), List.of(), null);
    }

    public static JunctionFilletElevationLoft build(
            List<JunctionGeometryResult.FilletCorner> fillets,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        Vec2d junctionPlan = inferJunctionPlan(edges, network);
        Map<String, Map<ColumnKey, Integer>> roadYByEdge = indexRoadSurfaceYByEdge(edgeResults);
        // 只合并本路口责任进口（立交下层生成时 edges 已排除上层），禁止 max 到上层桥面 Y。
        Map<ColumnKey, Integer> mergedRoadY = mergeApproachRoadY(roadYByEdge, edges);
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        List<ApproachElevationConstraint> approachConstraints = buildApproachConstraints(
            edges, roadYByEdge, edgeResults, centerlines, junctionPlan);
        if (fillets == null || fillets.isEmpty() || columns == null) {
            return new JunctionFilletElevationLoft(
                junctionY, junctionPlan, List.of(), mergedRoadY, approachConstraints, columns);
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
            junctionY, junctionPlan, corners, mergedRoadY, approachConstraints, columns);
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
        return insideJunctionCore(planPoint, junctionPlan);
    }

    private static boolean insideJunctionCore(Vec2d planPoint, Vec2d junctionPlan) {
        if (planPoint == null || junctionPlan == null) {
            return false;
        }
        return planPoint.distance(junctionPlan) < APPROACH_PIN_MIN_DIST;
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

    private static Map<ColumnKey, Integer> mergeApproachRoadY(
            Map<String, Map<ColumnKey, Integer>> roadYByEdge,
            List<RoadEdge> edges) {
        Map<ColumnKey, Integer> merged = new HashMap<>();
        if (roadYByEdge == null || edges == null) {
            return merged;
        }
        for (RoadEdge edge : edges) {
            if (edge == null || edge.getId() == null) {
                continue;
            }
            Map<ColumnKey, Integer> perEdge = roadYByEdge.get(edge.getId());
            if (perEdge == null) {
                continue;
            }
            for (Map.Entry<ColumnKey, Integer> entry : perEdge.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                // 同列多进口：取更低者，避免立交/叠层把下层清障起点抬到桥面。
                merged.merge(entry.getKey(), entry.getValue(), Math::min);
            }
        }
        return merged;
    }

    private static List<ApproachElevationConstraint> buildApproachConstraints(
            List<RoadEdge> edges,
            Map<String, Map<ColumnKey, Integer>> roadYByEdge,
            Map<String, RoadGenerationResult> edgeResults,
            Function<RoadEdge, List<Vec2d>> centerlines,
            Vec2d junctionPlan) {
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
            constraints.add(new ApproachElevationConstraint(centerline, scoped, result, junctionPlan));
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
            Vec2d junctionPlan) {
        Integer elevationAt(double station) {
            if (edgeResult != null
                    && edgeResult.buildProfile != null
                    && edgeResult.buildProfile.isActive()) {
                return edgeResult.buildProfile.elevationAtWorldStation(station);
            }
            if (roadYByColumn == null || roadYByColumn.isEmpty() || centerline == null || centerline.isEmpty()) {
                return null;
            }
            // 无纵断面时：取中心线附近已铺装列的众数近似；核内遗留边路忽略。
            Vec2d sample = pointAtStation(centerline, station);
            if (sample == null || insideJunctionCore(sample, junctionPlan)) {
                return null;
            }
            Integer best = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (Map.Entry<ColumnKey, Integer> entry : roadYByColumn.entrySet()) {
                ColumnKey key = entry.getKey();
                if (key == null || entry.getValue() == null) {
                    continue;
                }
                double dist = Math.hypot(key.worldX() + 0.5 - sample.x, key.worldZ() + 0.5 - sample.y);
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
