package com.plot.plugin.pattern.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已认领的铺装区域及图案参数。
 */
public class PatternFootprint {
    private final String id;
    private String name;
    private List<Vec2d> outerPoints;
    private ProceduralPatternConfig pattern = new ProceduralPatternConfig();

    public PatternFootprint(List<Vec2d> outerPoints) {
        this(UUID.randomUUID().toString(), outerPoints);
    }

    public PatternFootprint(String id, List<Vec2d> outerPoints) {
        this.id = id;
        this.outerPoints = copyPoints(outerPoints);
        this.name = id.substring(0, Math.min(8, id.length()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isBlank() ? name.trim() : this.name;
    }

    public List<Vec2d> getOuterPoints() {
        return copyPoints(outerPoints);
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.outerPoints = copyPoints(outerPoints);
    }

    public ProceduralPatternConfig getPattern() {
        return pattern != null ? pattern.copy() : new ProceduralPatternConfig();
    }

    public void setPattern(ProceduralPatternConfig pattern) {
        this.pattern = pattern != null ? pattern.copy() : new ProceduralPatternConfig();
    }

    public double computeArea() {
        return Math.abs(PolygonRegionUtils.signedAreaOfRing(outerPoints));
    }

    public Vec2d computeCentroid() {
        return PolygonRegionUtils.computeCentroid(outerPoints);
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        return PolygonRegionUtils.copyPoints(points);
    }
}
