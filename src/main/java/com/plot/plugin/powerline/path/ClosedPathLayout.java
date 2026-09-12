package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PoleSpacingMode;

import java.util.ArrayList;
import java.util.List;

/** 闭合参考路径上的环形布塔。 */
public final class ClosedPathLayout {
    private static final double POSITION_DEDUP_TOLERANCE_BLOCKS = 0.15;

    private ClosedPathLayout() {
    }

    public static List<Vec2d> computePolePositions(
            PowerLineSourcePath sourcePath,
            PoleSpacingMode mode,
            double maxPoleSpacingBlocks,
            int targetTowerCount,
            double cornerAngleThresholdDeg,
            ICoordinateService coordinates) {
        if (sourcePath == null || !sourcePath.isClosed()) {
            return List.of();
        }
        double perimeter = sourcePath.worldLength(coordinates);
        if (perimeter <= POSITION_DEDUP_TOLERANCE_BLOCKS) {
            return List.of(sourcePath.pointAtStation(0.0, coordinates).copy());
        }
        PoleSpacingMode resolved = mode != null ? mode : PoleSpacingMode.AUTO_SPACING;
        boolean piecewiseLinear = sourcePath instanceof PolylineSourcePath;
        List<Vec2d> towers = switch (resolved) {
            case AUTO_SPACING -> piecewiseLinear
                ? PowerPoleLayoutUtils.computeClosedPolylinePolePositions(
                    (PolylineSourcePath) sourcePath,
                    cornerAngleThresholdDeg,
                    maxPoleSpacingBlocks,
                    coordinates)
                : uniformRing(
                    sourcePath,
                    Math.max(3, (int) Math.ceil(perimeter / Math.max(0.1, maxPoleSpacingBlocks))),
                    coordinates);
            case TOWER_COUNT -> piecewiseLinear
                ? polylineTowerCount(
                    (PolylineSourcePath) sourcePath,
                    targetTowerCount,
                    cornerAngleThresholdDeg,
                    maxPoleSpacingBlocks,
                    coordinates)
                : uniformRing(
                    sourcePath,
                    Math.max(3, targetTowerCount),
                    coordinates);
            case ENDPOINTS_ONLY -> piecewiseLinear
                ? PowerPoleLayoutUtils.computeClosedPolylineVerticesOnly(
                    (PolylineSourcePath) sourcePath,
                    coordinates)
                : uniformRing(sourcePath, 3, coordinates);
            case ENDPOINTS_WITH_CORNERS -> cornerRing(
                sourcePath,
                cornerAngleThresholdDeg,
                coordinates);
        };
        if (!ClosedPathGeometry.isValidTowerLoop(towers)) {
            return List.of();
        }
        return towers;
    }

    public static List<Vec2d> uniformRing(
            PowerLineSourcePath sourcePath,
            int towerCount,
            ICoordinateService coordinates) {
        if (towerCount < 3) {
            return List.of();
        }
        double perimeter = sourcePath.worldLength(coordinates);
        if (perimeter <= POSITION_DEDUP_TOLERANCE_BLOCKS) {
            return List.of();
        }
        double spacing = perimeter / towerCount;
        List<Vec2d> towers = new ArrayList<>(towerCount);
        for (int i = 0; i < towerCount; i++) {
            towers.add(sourcePath.pointAtStation(spacing * i, coordinates).copy());
        }
        return towers;
    }

    private static List<Vec2d> polylineTowerCount(
            PolylineSourcePath sourcePath,
            int targetTowerCount,
            double cornerAngleThresholdDeg,
            double maxPoleSpacingBlocks,
            ICoordinateService coordinates) {
        List<Vec2d> cornerTowers = PowerPoleLayoutUtils.computeClosedPolylinePolePositions(
            sourcePath,
            cornerAngleThresholdDeg,
            maxPoleSpacingBlocks,
            coordinates);
        int vertexCount = cornerTowers.size();
        int desired = Math.max(3, targetTowerCount);
        if (vertexCount >= desired) {
            return cornerTowers;
        }
        return uniformRing(sourcePath, desired, coordinates);
    }

    private static List<Vec2d> cornerRing(
            PowerLineSourcePath sourcePath,
            double cornerAngleThresholdDeg,
            ICoordinateService coordinates) {
        if (sourcePath instanceof PolylineSourcePath polyline) {
            List<Vec2d> vertices = PowerPoleLayoutUtils.computeClosedPolylineVerticesOnly(
                polyline,
                coordinates);
            if (vertices.size() >= 3) {
                return vertices;
            }
        }
        List<Double> mandatory = sourcePath.mandatoryStations(cornerAngleThresholdDeg, coordinates);
        if (mandatory == null || mandatory.size() < 3) {
            return uniformRing(sourcePath, 3, coordinates);
        }
        List<Vec2d> towers = new ArrayList<>(mandatory.size());
        for (double station : mandatory) {
            towers.add(sourcePath.pointAtStation(station, coordinates).copy());
        }
        if (!ClosedPathGeometry.isValidTowerLoop(towers)) {
            return uniformRing(sourcePath, 3, coordinates);
        }
        return towers;
    }
}
