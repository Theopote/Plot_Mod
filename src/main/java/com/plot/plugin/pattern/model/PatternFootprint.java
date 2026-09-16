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
    private List<List<Vec2d>> holes = new ArrayList<>();
    private PatternSource source = PatternSource.PROCEDURAL;
    private ProceduralPatternConfig proceduralPattern = new ProceduralPatternConfig();
    private ImagePatternConfig imagePattern = new ImagePatternConfig();

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

    public List<List<Vec2d>> getHoles() {
        return copyHoles(holes);
    }

    public void setHoles(List<List<Vec2d>> holes) {
        this.holes = copyHoles(holes);
    }

    public PatternSource getSource() {
        return source != null ? source : PatternSource.PROCEDURAL;
    }

    public void setSource(PatternSource source) {
        this.source = source != null ? source : PatternSource.PROCEDURAL;
    }

    public ProceduralPatternConfig getPattern() {
        return proceduralPattern != null ? proceduralPattern.copy() : new ProceduralPatternConfig();
    }

    public void setPattern(ProceduralPatternConfig pattern) {
        this.proceduralPattern = pattern != null ? pattern.copy() : new ProceduralPatternConfig();
        if (this.source == null) {
            this.source = PatternSource.PROCEDURAL;
        }
    }

    public ImagePatternConfig getImagePattern() {
        return imagePattern != null ? imagePattern.copy() : new ImagePatternConfig();
    }

    public void setImagePattern(ImagePatternConfig imagePattern) {
        this.imagePattern = imagePattern != null ? imagePattern.copy() : new ImagePatternConfig();
    }

    public double computeArea() {
        return Math.abs(PolygonRegionUtils.computeSignedArea(outerPoints, holes));
    }

    public Vec2d computeCentroid() {
        return PolygonRegionUtils.computeCentroid(outerPoints);
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        return PolygonRegionUtils.copyPoints(points);
    }

    private static List<List<Vec2d>> copyHoles(List<List<Vec2d>> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<List<Vec2d>> copied = new ArrayList<>(source.size());
        for (List<Vec2d> hole : source) {
            if (hole != null && hole.size() >= 3) {
                copied.add(copyPoints(hole));
            }
        }
        return copied;
    }
}
