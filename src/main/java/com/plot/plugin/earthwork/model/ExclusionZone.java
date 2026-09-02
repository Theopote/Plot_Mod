package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 不参与挖填的分区（Phase B+ 合成使用；Phase A 仅持久化）。
 */
public class ExclusionZone {
    public static final String MODE_PRESERVE_EXISTING = "PRESERVE_EXISTING";
    public static final String MODE_NO_TOUCH = "NO_TOUCH";

    private String id;
    private String name = "";
    private List<Vec2d> outerPoints = new ArrayList<>();
    private String mode = MODE_PRESERVE_EXISTING;

    public ExclusionZone() {
    }

    public ExclusionZone(String id) {
        this.id = id;
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

    public String getMode() {
        return mode != null ? mode : MODE_PRESERVE_EXISTING;
    }

    public void setMode(String mode) {
        this.mode = mode;
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
