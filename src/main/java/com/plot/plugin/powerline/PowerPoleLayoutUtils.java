package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 电线杆位布局（纯函数）。
 * <p>
 * 杆距、里程（stationing）、override 容差均以 Minecraft blocks 计；
 * 必须通过 {@link ICoordinateService#projectedDistance} 解析画布路径。
 */
public final class PowerPoleLayoutUtils {
    /** Override 里程匹配容差（blocks）。 */
    private static final double OVERRIDE_STATION_TOLERANCE_BLOCKS = 2.0;

    private PowerPoleLayoutUtils() {
    }

    /**
     * 沿路径计算立杆位置：起点、终点、转折顶点强制立杆；相邻强制点世界间距超过最大值时等距补插。
     */
    public static List<Vec2d> computePolePositions(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            double maxPoleSpacingBlocks,
            ICoordinateService coordinates) {
        ICoordinateService coords = requireCoordinates(coordinates);
        if (pathPoints == null || pathPoints.isEmpty()) {
            return List.of();
        }
        if (pathPoints.size() == 1) {
            return List.of(pathPoints.getFirst().copy());
        }

        double maxSpacing = Math.max(0.1, maxPoleSpacingBlocks);
        List<Vec2d> mandatory = mandatoryPolePoints(pathPoints, cornerAngleThreshold);
        if (mandatory.isEmpty()) {
            return List.of();
        }

        List<Vec2d> result = new ArrayList<>();
        result.add(mandatory.getFirst().copy());
        for (int i = 0; i < mandatory.size() - 1; i++) {
            appendInterpolatedPoles(result, mandatory.get(i), mandatory.get(i + 1), maxSpacing, coords);
        }
        return result;
    }

    /** 计算杆塔站点（位置 + 世界里程 blocks + 自动角色分类）。 */
    public static List<PowerPoleSite> computePoleSites(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            double maxPoleSpacingBlocks,
            ICoordinateService coordinates) {
        ICoordinateService coords = requireCoordinates(coordinates);
        List<Vec2d> positions = computePolePositions(pathPoints, cornerAngleThreshold, maxPoleSpacingBlocks, coords);
        List<PowerPoleSite> sites = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            PowerPoleSite site = new PowerPoleSite(positions.get(i));
            site.setStationing(computeStationing(pathPoints, positions.get(i), coords));
            site.setPathIndex(i);
            sites.add(site);
        }
        TowerRoleClassifier.classifySites(sites, cornerAngleThreshold);
        return sites;
    }

    public static List<PowerPoleSite> computePoleSites(
            PowerLineFootprint footprint,
            ICoordinateService coordinates) {
        if (footprint == null) {
            return List.of();
        }
        ICoordinateService coords = requireCoordinates(coordinates);
        List<PowerPoleSite> sites = computePoleSites(
            footprint.getPathPoints(),
            footprint.getCornerAngleThreshold(),
            footprint.getMaxPoleSpacing(),
            coords);
        insertLayoutConstraints(
            sites,
            footprint.getPathPoints(),
            footprint.getLayoutConstraints(),
            footprint.getCornerAngleThreshold(),
            coords);
        applyOverrides(sites, footprint.getPoleOverrides());
        return sites;
    }

    /** 按世界里程（blocks，自路径起点）取画布点。 */
    public static Vec2d pointAtStationing(
            List<Vec2d> pathPoints,
            double worldStationBlocks,
            ICoordinateService coordinates) {
        return WorldProjectionMath.canvasPointAtWorldStation(
            requireCoordinates(coordinates),
            pathPoints,
            worldStationBlocks);
    }

    private static void insertLayoutConstraints(
            List<PowerPoleSite> sites,
            List<Vec2d> pathPoints,
            List<PoleLayoutConstraint> constraints,
            double cornerAngleThreshold,
            ICoordinateService coordinates) {
        if (sites == null || constraints == null || constraints.isEmpty()) {
            return;
        }
        for (PoleLayoutConstraint constraint : constraints) {
            if (constraint == null) {
                continue;
            }
            boolean exists = false;
            for (PowerPoleSite site : sites) {
                if (Math.abs(site.getStationing() - constraint.getRequiredStationing())
                        <= OVERRIDE_STATION_TOLERANCE_BLOCKS) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                continue;
            }
            Vec2d position = pointAtStationing(
                pathPoints,
                constraint.getRequiredStationing(),
                coordinates);
            PowerPoleSite inserted = new PowerPoleSite(position);
            inserted.setStationing(constraint.getRequiredStationing());
            sites.add(inserted);
        }
        sites.sort(java.util.Comparator.comparingDouble(PowerPoleSite::getStationing));
        for (int i = 0; i < sites.size(); i++) {
            sites.get(i).setPathIndex(i);
        }
        TowerRoleClassifier.classifySites(sites, cornerAngleThreshold);
    }

    public static void applyOverrides(List<PowerPoleSite> sites, List<PoleOverride> overrides) {
        if (sites == null || overrides == null || overrides.isEmpty()) {
            return;
        }
        for (PowerPoleSite site : sites) {
            PoleOverride match = findNearestOverride(site.getStationing(), overrides);
            if (match == null) {
                continue;
            }
            if (match.getRoleOverride() != null) {
                site.setRole(match.getRoleOverride());
                site.setRoleAutoAssigned(false);
            }
            if (match.getPoleDesignOverrideId() != null) {
                site.setPoleDesignOverrideId(match.getPoleDesignOverrideId());
            }
        }
    }

    private static PoleOverride findNearestOverride(double stationingBlocks, List<PoleOverride> overrides) {
        PoleOverride best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PoleOverride override : overrides) {
            double distance = Math.abs(override.getPathDistance() - stationingBlocks);
            if (distance <= OVERRIDE_STATION_TOLERANCE_BLOCKS && distance < bestDistance) {
                best = override;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 世界里程（blocks）：杆位在路径上的投影距起点长度。 */
    public static double computeStationing(
            List<Vec2d> pathPoints,
            Vec2d polePosition,
            ICoordinateService coordinates) {
        ICoordinateService coords = requireCoordinates(coordinates);
        if (pathPoints == null || pathPoints.size() < 2 || polePosition == null) {
            return 0.0;
        }
        double totalWorld = 0.0;
        double bestStationing = 0.0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d a = pathPoints.get(i);
            Vec2d b = pathPoints.get(i + 1);
            double segWorldLen = coords.projectedDistance(a, b);
            if (segWorldLen < 1e-12) {
                continue;
            }
            Vec2d ab = b.subtract(a);
            double t = polePosition.subtract(a).dot(ab) / ab.lengthSquared();
            t = Math.max(0.0, Math.min(1.0, t));
            Vec2d projected = a.lerp(b, t);
            double worldDistToProjection = coords.projectedDistance(projected, polePosition);
            if (worldDistToProjection < bestDistance) {
                bestDistance = worldDistToProjection;
                bestStationing = totalWorld + segWorldLen * t;
            }
            totalWorld += segWorldLen;
        }
        return bestStationing;
    }

    /** 相邻杆塔世界档距（blocks），来自 world stationing。 */
    public static double worldSpanBlocks(PowerPoleSite from, PowerPoleSite to) {
        if (from == null || to == null) {
            return 0.0;
        }
        return Math.abs(to.getStationing() - from.getStationing());
    }

    public static double deflectionAtSite(List<PowerPoleSite> sites, int index) {
        if (sites == null || index <= 0 || index >= sites.size() - 1) {
            return 0.0;
        }
        Vec2d incoming = sites.get(index).getPlanPosition()
            .subtract(sites.get(index - 1).getPlanPosition());
        Vec2d outgoing = sites.get(index + 1).getPlanPosition()
            .subtract(sites.get(index).getPlanPosition());
        return TowerRoleClassifier.computeDeflectionAngle(incoming, outgoing);
    }

    /** 必须立杆的路径点：起点、转角顶点、终点。 */
    public static List<Vec2d> mandatoryPolePoints(List<Vec2d> pathPoints, double cornerAngleThreshold) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return List.of();
        }
        if (pathPoints.size() == 1) {
            return List.of(pathPoints.getFirst().copy());
        }
        List<Vec2d> mandatory = new ArrayList<>();
        mandatory.add(pathPoints.getFirst().copy());
        for (int i = 1; i < pathPoints.size() - 1; i++) {
            if (isCorner(pathPoints, i, cornerAngleThreshold)) {
                mandatory.add(pathPoints.get(i).copy());
            }
        }
        mandatory.add(pathPoints.getLast().copy());
        return mandatory;
    }

    public static boolean hasSiteNear(
            List<PowerPoleSite> sites,
            Vec2d point,
            double toleranceBlocks,
            ICoordinateService coordinates) {
        if (sites == null || point == null) {
            return false;
        }
        ICoordinateService coords = requireCoordinates(coordinates);
        for (PowerPoleSite site : sites) {
            if (coords.projectedDistance(site.getPlanPosition(), point) <= toleranceBlocks) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCorner(List<Vec2d> pathPoints, int index, double cornerAngleThreshold) {
        Vec2d prev = pathPoints.get(index - 1);
        Vec2d current = pathPoints.get(index);
        Vec2d next = pathPoints.get(index + 1);
        Vec2d in = current.subtract(prev);
        Vec2d out = next.subtract(current);
        if (in.lengthSquared() < 1e-12 || out.lengthSquared() < 1e-12) {
            return true;
        }
        double dot = in.normalize().dot(out.normalize());
        double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angleDeg > cornerAngleThreshold;
    }

    private static void appendInterpolatedPoles(
            List<Vec2d> result,
            Vec2d from,
            Vec2d to,
            double maxSpacingBlocks,
            ICoordinateService coordinates) {
        double worldDistance = coordinates.projectedDistance(from, to);
        if (worldDistance <= maxSpacingBlocks) {
            result.add(to.copy());
            return;
        }
        int segments = (int) Math.ceil(worldDistance / maxSpacingBlocks);
        for (int i = 1; i <= segments; i++) {
            double worldOffset = worldDistance * i / segments;
            result.add(WorldProjectionMath.canvasPointAtWorldOffsetOnSegment(
                coordinates, from, to, worldOffset));
        }
    }

    private static ICoordinateService requireCoordinates(ICoordinateService coordinates) {
        return Objects.requireNonNull(coordinates, "coordinates");
    }
}
