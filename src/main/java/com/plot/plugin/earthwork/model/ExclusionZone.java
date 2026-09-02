package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;

import java.util.List;

/**
 * 排除区：场地内不参与（或单独处理）挖填的子区域。
 * <p>
 * 典型用途：建筑保留区、水池、已建构筑物、中庭、禁挖区等。
 * 几何上支持外环 + 孔洞；也可通过孔洞在 GradingZone 内挖出保留岛。
 */
public class ExclusionZone {
    public static final String MODE_PRESERVE_EXISTING = "PRESERVE_EXISTING";
    public static final String MODE_NO_TOUCH = "NO_TOUCH";

    private String id;
    private String name = "";
    private RegionGeometry geometry = RegionGeometry.empty();
    private String mode = MODE_PRESERVE_EXISTING;

    public ExclusionZone() {
    }

    public ExclusionZone(String id) {
        this.id = id;
    }

    public ExclusionZone(String id, List<Vec2d> outerRing) {
        this.id = id;
        this.geometry = RegionGeometry.of(outerRing);
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

    public String getMode() {
        return mode != null ? mode : MODE_PRESERVE_EXISTING;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
