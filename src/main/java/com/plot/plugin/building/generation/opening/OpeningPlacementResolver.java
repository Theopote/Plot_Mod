package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.model.spec.OpeningKind;
import com.plot.plugin.building.model.spec.OpeningSpec;

import java.util.List;

/**
 * 将 {@link OpeningSpec} 解析为可体素化的墙洞几何。
 */
public final class OpeningPlacementResolver {
    private OpeningPlacementResolver() {
    }

    public record ResolvedOpening(
            OpeningSpec opening,
            Vec2d centerPoint,
            Vec2d tangent,
            Vec2d inwardNormal,
            int width,
            int height,
            int startY) {
    }

    public static ResolvedOpening resolve(
            OpeningSpec opening,
            List<Vec2d> outerPoints,
            int baseElevation,
            int floorHeight) {
        if (opening == null || outerPoints == null || outerPoints.size() < 3) {
            return null;
        }

        int segmentCount = outerPoints.size();
        int segmentIndex = Math.floorMod(opening.wallSegmentIndex(), segmentCount);
        Vec2d point = BuildingGeometryUtils.pointOnWallSegment(
            outerPoints, segmentIndex, opening.positionRatio());
        if (point == null) {
            return null;
        }

        Vec2d start = outerPoints.get(segmentIndex);
        Vec2d end = outerPoints.get((segmentIndex + 1) % segmentCount);
        Vec2d tangent = end.subtract(start).normalize();
        Vec2d inwardNormal = BuildingGeometryUtils.outwardNormal(outerPoints, segmentIndex).multiply(-1);

        int floorBaseY = baseElevation + opening.floor() * floorHeight;
        int bottom = opening.bottomOffset();
        int maxHeight = maxOpeningHeight(opening.kind(), floorHeight, bottom);
        int height = Math.min(opening.height(), maxHeight);
        if (height <= 0) {
            return null;
        }

        return new ResolvedOpening(
            opening,
            point,
            tangent,
            inwardNormal,
            opening.width(),
            height,
            floorBaseY + bottom
        );
    }

    private static int maxOpeningHeight(OpeningKind kind, int floorHeight, int bottomOffset) {
        return switch (kind) {
            case WINDOW -> Math.max(1, floorHeight - bottomOffset - 1);
            case DOOR, ARCH -> Math.max(1, floorHeight - bottomOffset - 1);
        };
    }
}
