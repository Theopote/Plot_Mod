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
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 倒圆补铺标高：沿相邻两进口路缘边线采样走廊实际路面高程，对角向参数放样。
 * 每条路缘只读取其所属进口道路的路面/纵断面，禁止串用邻路 ±2 格高程。
 * 重叠核等非补铺区仍回退 {@code junctionY}。
 */
public final class JunctionFilletElevationLoft {
    private static final double EPSILON = 1e-9;
    private static final double MAX_CURB_SAMPLE = 24.0;
    /** 切点到中心线允许的侧向距离（含宽路路缘）。 */
    private static final double TANGENT_OWNER_MAX_DIST = 16.0;

    private final int junctionY;
    private final List<FilletLoftCorner> corners;

    private JunctionFilletElevationLoft(int junctionY, List<FilletLoftCorner> corners) {
        this.junctionY = junctionY;
        this.corners = List.copyOf(corners);
    }

    public static JunctionFilletElevationLoft inactive(int junctionY) {
        return new JunctionFilletElevationLoft(junctionY, List.of());
    }

    public static JunctionFilletElevationLoft build(
            List<JunctionGeometryResult.FilletCorner> fillets,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        if (fillets == null || fillets.isEmpty() || columns == null) {
            return inactive(junctionY);
        }
        Map<String, Map<ColumnKey, Integer>> roadYByEdge = indexRoadSurfaceYByEdge(edgeResults);
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        List<FilletLoftCorner> corners = new ArrayList<>();
        for (JunctionGeometryResult.FilletCorner fillet : fillets) {
            FilletLoftCorner corner = buildCorner(
                fillet, roadYByEdge, edgeResults, edges, network, centerlines, columns, junctionY);
            if (corner != null) {
                corners.add(corner);
            }
        }
        return new JunctionFilletElevationLoft(junctionY, corners);
    }

    public int junctionY() {
        return junctionY;
    }

    /** 补铺 gore 内放样；否则共识标高。 */
    public int surfaceY(Vec2d planPoint) {
        if (planPoint == null || corners.isEmpty()) {
            return junctionY;
        }
        for (FilletLoftCorner corner : corners) {
            if (!RoadGeometryUtils.pointInPolygon(planPoint, corner.gore())) {
                continue;
            }
            return corner.loftY(planPoint, junctionY);
        }
        return junctionY;
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
