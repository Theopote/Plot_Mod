package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 建筑轮廓几何与身份标识。
 */
public final class FootprintSpec {
    private final String id;
    private final String name;
    private final List<Vec2d> outerPoints;
    private final boolean rectangular;

    public FootprintSpec(String id, String name, List<Vec2d> outerPoints, boolean rectangular) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name != null && !name.isBlank() ? name.trim() : id.substring(0, Math.min(8, id.length()));
        this.outerPoints = copyPoints(outerPoints);
        this.rectangular = rectangular;
    }

    public static FootprintSpec from(BuildingFootprint footprint) {
        Objects.requireNonNull(footprint, "footprint");
        return new FootprintSpec(
            footprint.getId(),
            footprint.getName(),
            footprint.getOuterPoints(),
            footprint.isRectangular()
        );
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<Vec2d> outerPoints() {
        return copyPoints(outerPoints);
    }

    public boolean rectangular() {
        return rectangular;
    }

    public double area() {
        return Math.abs(BuildingFootprint.signedArea(outerPoints));
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point != null ? point.copy() : new Vec2d(0, 0));
            }
        }
        return List.copyOf(copy);
    }
}
