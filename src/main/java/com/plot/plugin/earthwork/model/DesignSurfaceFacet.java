package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 多坡面分区（{@link DesignSurfaceKind#MULTI_PLANE}）内的一个子区域及其设计面。
 */
public class DesignSurfaceFacet {
    private String id;
    private String name = "";
    private List<Vec2d> outerPoints = new ArrayList<>();
    private DesignSurface plane = new DesignSurface();

    public DesignSurfaceFacet() {
        plane.setKind(DesignSurfaceKind.LEVEL_PAD);
    }

    public DesignSurfaceFacet(String id, List<Vec2d> outerPoints) {
        this.id = id;
        setOuterPoints(outerPoints);
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
        return copyPoints(outerPoints);
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.outerPoints = copyPoints(outerPoints);
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

    private static List<Vec2d> copyPoints(List<Vec2d> source) {
        List<Vec2d> copy = new ArrayList<>();
        if (source != null) {
            for (Vec2d point : source) {
                if (point != null) {
                    copy.add(new Vec2d(point.x, point.y));
                }
            }
        }
        return copy;
    }
}
