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
 * 重叠核等非补铺区仍回退 {@code junctionY}。
 */
public final class JunctionFilletElevationLoft {
    private static final double EPSILON = 1e-9;
    private static final double MAX_CURB_SAMPLE = 24.0;

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
        Map<ColumnKey, Integer> roadY = indexRoadSurfaceY(edgeResults);
        Function<RoadEdge, List<Vec2d>> centerlines = RoadJunctionCenterlineResolver.forNetwork(network);
        List<FilletLoftCorner> corners = new ArrayList<>();
        for (JunctionGeometryResult.FilletCorner fillet : fillets) {
            FilletLoftCorner corner = buildCorner(
                fillet, roadY, edgeResults, edges, network, centerlines, columns, junctionY);
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
            Map<ColumnKey, Integer> roadY,
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
        Vec2d sharp = RoadJunctionGenerator.reconstructFilletSharpCorner(fillet, sweep);
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
        CurbElevationCurve curbA = sampleCurb(
            sharp, dirA, roadY, edgeResults, edges, network, centerlines, columns, junctionY);
        CurbElevationCurve curbB = sampleCurb(
            sharp, dirB, roadY, edgeResults, edges, network, centerlines, columns, junctionY);
        return new FilletLoftCorner(gore, sharp, dirA, dirB, curbA, curbB);
    }

    private static CurbElevationCurve sampleCurb(
            Vec2d origin,
            Vec2d direction,
            Map<ColumnKey, Integer> roadY,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            Function<RoadEdge, List<Vec2d>> centerlines,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        List<Double> distances = new ArrayList<>();
        List<Integer> elevations = new ArrayList<>();
        distances.add(0.0);
        elevations.add(lookupElevation(
            origin, roadY, edgeResults, edges, network, centerlines, columns, junctionY));
        for (double s = 1.0; s <= MAX_CURB_SAMPLE; s += 1.0) {
            Vec2d sample = origin.add(direction.multiply(s));
            int y = lookupElevation(
                sample, roadY, edgeResults, edges, network, centerlines, columns, junctionY);
            distances.add(s);
            elevations.add(y);
        }
        return new CurbElevationCurve(distances, elevations);
    }

    private static int lookupElevation(
            Vec2d planPoint,
            Map<ColumnKey, Integer> roadY,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            Function<RoadEdge, List<Vec2d>> centerlines,
            RoadTerrainClearanceUtils.BlockColumnResolver columns,
            int junctionY) {
        int wx = columns.worldX(planPoint);
        int wz = columns.worldZ(planPoint);
        Integer exact = roadY.get(new ColumnKey(wx, wz));
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
                Integer y = roadY.get(new ColumnKey(wx + dx, wz + dz));
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
        Integer fromProfile = elevationFromBuildProfile(
            planPoint, edgeResults, edges, network, centerlines);
        return fromProfile != null ? fromProfile : junctionY;
    }

    private static Integer elevationFromBuildProfile(
            Vec2d planPoint,
            Map<String, RoadGenerationResult> edgeResults,
            List<RoadEdge> edges,
            RoadNetwork network,
            Function<RoadEdge, List<Vec2d>> centerlines) {
        if (edgeResults == null || edges == null || planPoint == null) {
            return null;
        }
        RoadEdge best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStation = 0.0;
        for (RoadEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            List<Vec2d> centerline = centerlines.apply(edge);
            if (centerline == null || centerline.size() < 2) {
                continue;
            }
            Projection proj = projectToPolyline(planPoint, centerline);
            if (proj == null || proj.distance > bestDist) {
                continue;
            }
            bestDist = proj.distance;
            best = edge;
            bestStation = proj.station;
        }
        if (best == null || bestDist > 8.0) {
            return null;
        }
        RoadGenerationResult result = edgeResults.get(best.getId());
        if (result == null || result.buildProfile == null || !result.buildProfile.isActive()) {
            return null;
        }
        return result.buildProfile.elevationAtWorldStation(bestStation);
    }

    private static Map<ColumnKey, Integer> indexRoadSurfaceY(
            Map<String, RoadGenerationResult> edgeResults) {
        Map<ColumnKey, Integer> roadY = new HashMap<>();
        if (edgeResults == null) {
            return roadY;
        }
        for (RoadGenerationResult result : edgeResults.values()) {
            if (result == null || result.placementRecords == null) {
                continue;
            }
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
        return roadY;
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
