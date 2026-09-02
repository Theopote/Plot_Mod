package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;

import java.util.List;

/**
 * 多坡面分区（{@link DesignSurfaceKind#MULTI_PLANE}）内的一个子区域及其设计面。
 */
public class DesignSurfaceFacet {
    private String id;
    private String name = "";
    private RegionGeometry geometry = RegionGeometry.empty();
    private DesignSurface plane = new DesignSurface();

    public DesignSurfaceFacet() {
        plane.setKind(DesignSurfaceKind.LEVEL_PAD);
    }

    public DesignSurfaceFacet(String id, List<Vec2d> outerPoints) {
        this(id, RegionGeometry.of(outerPoints));
    }

    public DesignSurfaceFacet(String id, RegionGeometry geometry) {
        this.id = id;
        setGeometry(geometry);
        plane.setKind(DesignSurfaceKind.LEVEL_PAD);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Vec2d> getOuterPoints() {
        return geometry.outerRing();
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.geometry = geometry.withOuterRing(outerPoints);
    }

    public RegionGeometry getGeometry() {
        return geometry;
    }

    public void setGeometry(RegionGeometry geometry) {
        this.geometry = geometry != null ? geometry : RegionGeometry.empty();
    }

    public List<List<Vec2d>> getHoles() {
        return geometry.holes();
    }

    public void setHoles(List<List<Vec2d>> holes) {
        this.geometry = geometry.withHoles(holes);
    }

    public boolean containsCanvasPoint(Vec2d canvasPoint) {
        return geometry.contains(canvasPoint);
    }

    public DesignSurface getPlane() {
        if (plane == null) {
            plane = new DesignSurface();
            plane.setKind(DesignSurfaceKind.LEVEL_PAD);
        }
        return plane;
    }

    public void setPlane(DesignSurface plane) {
        this.plane = plane != null ? plane : new DesignSurface();
    }
}
