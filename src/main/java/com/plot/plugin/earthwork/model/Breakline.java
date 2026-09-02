package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 场地内硬边界折线（Phase D 合成使用；Phase A 仅持久化）。
 */
public class Breakline {
    public static final String ROLE_HARD_BOUNDARY = "HARD_BOUNDARY";
    public static final String ROLE_ELEVATION_STEP = "ELEVATION_STEP";
    public static final String ROLE_NO_BLENDING = "NO_BLENDING";

    private String id;
    private String name = "";
    private List<Vec2d> points = new ArrayList<>();
    private String role = ROLE_HARD_BOUNDARY;
    private String leftZoneId = "";
    private String rightZoneId = "";

    public Breakline() {
    }

    public Breakline(String id) {
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

    public List<Vec2d> getPoints() {
        return copyPoints(points);
    }

    public void setPoints(List<Vec2d> points) {
        this.points = copyPoints(points);
    }

    public String getRole() {
        return role != null ? role : ROLE_HARD_BOUNDARY;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getLeftZoneId() {
        return leftZoneId != null ? leftZoneId : "";
    }

    public void setLeftZoneId(String leftZoneId) {
        this.leftZoneId = leftZoneId;
    }

    public String getRightZoneId() {
        return rightZoneId != null ? rightZoneId : "";
    }

    public void setRightZoneId(String rightZoneId) {
        this.rightZoneId = rightZoneId;
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
