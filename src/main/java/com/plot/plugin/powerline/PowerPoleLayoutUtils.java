package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.path.ClosedPathLayout;
import com.plot.plugin.powerline.path.PowerLinePathLayout;
import com.plot.plugin.powerline.path.PowerLineSourcePath;
import com.plot.plugin.powerline.path.PolylineSourcePath;

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
    /** 重复顶点/杆位合并容差（blocks），与 UI 杆位命中判定一致。 */
    private static final double POSITION_DEDUP_TOLERANCE_BLOCKS = 0.15;

    private PowerPoleLayoutUtils() {
    }

    /**
     * 沿路径计算立杆位置：起点、终点、转折顶点强制立杆；相邻强制点世界间距超过最大值时等距补插。
     */
    public static List<Vec2d> computePolePositions(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            PoleSpacingMode mode,
            double maxPoleSpacingBlocks,
            int targetTowerCount,
            ICoordinateService coordinates) {
        return computePolePositions(
            PolylineSourcePath.open(pathPoints),
            cornerAngleThreshold,
            mode,
            maxPoleSpacingBlocks,
            targetTowerCount,
            coordinates);
    }

    public static List<Vec2d> computePolePositions(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            double maxPoleSpacingBlocks,
            ICoordinateService coordinates) {
        return computePolePositions(
            PolylineSourcePath.open(pathPoints),
            cornerAngleThreshold,
            maxPoleSpacingBlocks,
            coordinates);
    }

    public static List<Vec2d> computePolePositions(
            PowerLineSourcePath sourcePath,
            double cornerAngleThreshold,
            PoleSpacingMode mode,
            double maxPoleSpacingBlocks,
            int targetTowerCount,
            ICoordinateService coordinates) {
        ICoordinateService coords = requireCoordinates(coordinates);
        if (sourcePath == null) {
            return List.of();
        }
        if (sourcePath.isClosed()) {
            return ClosedPathLayout.computePolePositions(
                sourcePath,
                mode,
                maxPoleSpacingBlocks,
                targetTowerCount,
                cornerAngleThreshold,
                coords);
        }
        PoleSpacingMode resolved = mode != null ? mode : PoleSpacingMode.AUTO_SPACING;
        return switch (resolved) {
            case AUTO_SPACING -> computePolePositions(
                sourcePath,
                cornerAngleThreshold,
                maxPoleSpacingBlocks,
                coords);
            case TOWER_COUNT -> computeEvenlySpacedPoles(
                sourcePath,
                Math.max(2, targetTowerCount),
                coords);
            case ENDPOINTS_ONLY -> computeEndpointsOnly(sourcePath, coords);
            case ENDPOINTS_WITH_CORNERS -> mandatoryStationsAsPoints(
                sourcePath.mandatoryStations(cornerAngleThreshold, coords),
                sourcePath,
                coords);
        };
    }

    public static List<Vec2d> computePolePositions(
            PowerLineSourcePath sourcePath,
            double cornerAngleThreshold,
            double maxPoleSpacingBlocks,
            ICoordinateService coordinates) {
        ICoordinateService coords = requireCoordinates(coordinates);
        if (sourcePath == null) {
            return List.of();
        }
        double totalLength = sourcePath.worldLength(coords);
        if (totalLength <= POSITION_DEDUP_TOLERANCE_BLOCKS) {
            return List.of(sourcePath.pointAtStation(0.0, coords).copy());
        }

        double maxSpacing = Math.max(0.1, maxPoleSpacingBlocks);
        List<Double> mandatoryStations = dedupeMandatoryStations(
            sourcePath.mandatoryStations(cornerAngleThreshold, coords),
            sourcePath,
            coords);
        if (mandatoryStations.isEmpty()) {
            return List.of();
        }

        List<Vec2d> result = new ArrayList<>();
        addPoleIfDistinct(
            result,
            sourcePath.pointAtStation(mandatoryStations.getFirst(), coords),
            coords);
        for (int i = 0; i < mandatoryStations.size() - 1; i++) {
            appendInterpolatedPolesAlongPath(
                result,
                sourcePath,
                mandatoryStations.get(i),
                mandatoryStations.get(i + 1),
                maxSpacing,
                coords);
        }
        return result;
    }

    /** 计算杆塔站点（位置 + 世界里程 blocks + 自动角色分类）。 */
    public static List<PowerPoleSite> computePoleSites(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            double maxPoleSpacingBlocks,
            ICoordinateService coordinates) {
        return computePoleSites(
            pathPoints,
            cornerAngleThreshold,
            PoleSpacingMode.AUTO_SPACING,
            maxPoleSpacingBlocks,
            2,
            coordinates);
    }

    public static List<PowerPoleSite> computePoleSites(
            List<Vec2d> pathPoints,
            double cornerAngleThreshold,
            PoleSpacingMode mode,
            double maxPoleSpacingBlocks,
            int targetTowerCount,
            ICoordinateService coordinates) {
        return computePoleSites(
            PolylineSourcePath.open(pathPoints),
            cornerAngleThreshold,
            mode,
            maxPoleSpacingBlocks,
            targetTowerCount,
            coordinates);
    }

    public static List<PowerPoleSite> computePoleSites(
            PowerLineSourcePath sourcePath,
            double cornerAngleThreshold,
            PoleSpacingMode mode,
            double maxPoleSpacingBlocks,
            int targetTowerCount,
            ICoordinateService coordinates) {
        ICoordinateService coords = requireCoordinates(coordinates);
        List<Vec2d> positions = computePolePositions(
            sourcePath,
            cornerAngleThreshold,
            mode,
            maxPoleSpacingBlocks,
            targetTowerCount,
            coords);
        List<PowerPoleSite> sites = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            PowerPoleSite site = new PowerPoleSite(positions.get(i));
            site.setStationing(sourcePath.stationAtPoint(positions.get(i), coords));
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
        PowerLineSourcePath sourcePath = footprint.resolveSourcePath();
        List<PowerPoleSite> sites = computePoleSites(
            sourcePath,
            footprint.getCornerAngleThreshold(),
            footprint.getPoleSpacingMode(),
            footprint.getMaxPoleSpacing(),
            footprint.getTargetTowerCount(),
            coords);
        if (footprint.getPoleSpacingMode() != PoleSpacingMode.ENDPOINTS_ONLY) {
            insertLayoutConstraints(
                sites,
                sourcePath,
                footprint.getLayoutConstraints(),
                footprint.getCornerAngleThreshold(),
                coords);
        }
        applyOverrides(sites, footprint.getPoleOverrides());
        return sites;
    }

    /** 由参考路径计算杆塔折线并写回 footprint.pathPoints。 */
    public static List<PowerPoleSite> layoutAndSyncFootprint(
            PowerLineFootprint footprint,
            ICoordinateService coordinates) {
        return PowerLinePathLayout.layoutAndSync(footprint, coordinates);
    }

    /** 按世界里程（blocks，自路径起点）取画布点。 */
    public static Vec2d pointAtStationing(
            List<Vec2d> pathPoints,
            double worldStationBlocks,
            ICoordinateService coordinates) {
        return PolylineSourcePath.open(pathPoints)
            .pointAtStation(worldStationBlocks, coordinates);
    }

    public static Vec2d pointAtStationing(
            PowerLineSourcePath sourcePath,
            double worldStationBlocks,
            ICoordinateService coordinates) {
        return requireSourcePath(sourcePath)
            .pointAtStation(worldStationBlocks, coordinates);
    }

    private static void insertLayoutConstraints(
            List<PowerPoleSite> sites,
            PowerLineSourcePath sourcePath,
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
                sourcePath,
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

    /** 世界里程（blocks）：杆位在折线顶点路径上的投影距起点长度。 */
    public static double computeStationing(
            List<Vec2d> pathPoints,
            Vec2d polePosition,
            ICoordinateService coordinates) {
        return computeStationingOnPolyline(pathPoints, polePosition, coordinates);
    }

    public static double computeStationingOnPolyline(
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

    private static List<Vec2d> computeEndpointsOnly(
            PowerLineSourcePath sourcePath,
            ICoordinateService coordinates) {
        double length = sourcePath.worldLength(coordinates);
        if (length <= POSITION_DEDUP_TOLERANCE_BLOCKS) {
            return List.of(sourcePath.pointAtStation(0.0, coordinates).copy());
        }
        return List.of(
            sourcePath.pointAtStation(0.0, coordinates).copy(),
            sourcePath.pointAtStation(length, coordinates).copy());
    }

    private static List<Vec2d> computeEvenlySpacedPoles(
            PowerLineSourcePath sourcePath,
            int towerCount,
            ICoordinateService coordinates) {
        if (sourcePath == null) {
            return List.of();
        }
        double totalLength = sourcePath.worldLength(coordinates);
        if (totalLength <= POSITION_DEDUP_TOLERANCE_BLOCKS || towerCount <= 1) {
            return List.of(sourcePath.pointAtStation(0.0, coordinates).copy());
        }
        int count = Math.max(2, towerCount);
        List<Vec2d> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double stationing = totalLength * i / (count - 1);
            Vec2d point = sourcePath.pointAtStation(stationing, coordinates);
            addPoleIfDistinct(result, point, coordinates);
        }
        return result;
    }

    private static void appendInterpolatedPolesAlongPath(
            List<Vec2d> result,
            PowerLineSourcePath sourcePath,
            double fromStationBlocks,
            double toStationBlocks,
            double maxSpacingBlocks,
            ICoordinateService coordinates) {
        double worldDistance = toStationBlocks - fromStationBlocks;
        if (worldDistance <= POSITION_DEDUP_TOLERANCE_BLOCKS) {
            addPoleIfDistinct(
                result,
                sourcePath.pointAtStation(toStationBlocks, coordinates),
                coordinates);
            return;
        }
        if (worldDistance <= maxSpacingBlocks) {
            addPoleIfDistinct(
                result,
                sourcePath.pointAtStation(toStationBlocks, coordinates),
                coordinates);
            return;
        }
        int segments = (int) Math.ceil(worldDistance / maxSpacingBlocks);
        for (int i = 1; i <= segments; i++) {
            double station = fromStationBlocks + worldDistance * i / segments;
            addPoleIfDistinct(
                result,
                sourcePath.pointAtStation(station, coordinates),
                coordinates);
        }
    }

    private static List<Double> dedupeMandatoryStations(
            List<Double> mandatoryStations,
            PowerLineSourcePath sourcePath,
            ICoordinateService coordinates) {
        if (mandatoryStations == null || mandatoryStations.isEmpty()) {
            return List.of();
        }
        List<Double> deduped = new ArrayList<>(mandatoryStations.size());
        for (double station : mandatoryStations) {
            if (deduped.isEmpty()) {
                deduped.add(station);
                continue;
            }
            Vec2d previous = sourcePath.pointAtStation(deduped.getLast(), coordinates);
            Vec2d current = sourcePath.pointAtStation(station, coordinates);
            if (!samePosition(previous, current, coordinates)) {
                deduped.add(station);
            }
        }
        return deduped;
    }

    private static List<Vec2d> mandatoryStationsAsPoints(
            List<Double> mandatoryStations,
            PowerLineSourcePath sourcePath,
            ICoordinateService coordinates) {
        List<Double> stations = dedupeMandatoryStations(mandatoryStations, sourcePath, coordinates);
        List<Vec2d> points = new ArrayList<>(stations.size());
        for (double station : stations) {
            points.add(sourcePath.pointAtStation(station, coordinates).copy());
        }
        return points;
    }

    private static List<Vec2d> dedupeMandatoryPolePoints(
            List<Vec2d> mandatory,
            ICoordinateService coordinates) {
        if (mandatory == null || mandatory.isEmpty()) {
            return List.of();
        }
        List<Vec2d> deduped = new ArrayList<>(mandatory.size());
        for (Vec2d point : mandatory) {
            addPoleIfDistinct(deduped, point, coordinates);
        }
        return deduped;
    }

    private static void addPoleIfDistinct(
            List<Vec2d> result,
            Vec2d point,
            ICoordinateService coordinates) {
        if (point == null) {
            return;
        }
        if (!result.isEmpty() && samePosition(result.getLast(), point, coordinates)) {
            return;
        }
        result.add(point.copy());
    }

    private static boolean samePosition(Vec2d a, Vec2d b, ICoordinateService coordinates) {
        return coordinates.projectedDistance(a, b) <= POSITION_DEDUP_TOLERANCE_BLOCKS;
    }

    private static ICoordinateService requireCoordinates(ICoordinateService coordinates) {
        return Objects.requireNonNull(coordinates, "coordinates");
    }

    private static PowerLineSourcePath requireSourcePath(PowerLineSourcePath sourcePath) {
        return Objects.requireNonNull(sourcePath, "sourcePath");
    }
}
