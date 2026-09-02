package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 挡土/直立界（Phase D 使用；Phase A 仅持久化）。
 */
public class RetainingEdge {
    public static final String SIDE_CUT = "CUT";
    public static final String SIDE_FILL = "FILL";

    private String id;
    private String name = "";
    private List<Vec2d> polyline = new ArrayList<>();
    private int topElevation;
    private int bottomElevation;
    private String side = SIDE_CUT;

    public RetainingEdge() {
    }

    public RetainingEdge(String id) {
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

    public List<Vec2d> getPolyline() {
        return copyPoints(polyline);
    }

    public void setPolyline(List<Vec2d> polyline) {
        this.polyline = copyPoints(polyline);
    }

    public int getTopElevation() {
        return topElevation;
    }

    public void setTopElevation(int topElevation) {
        this.topElevation = topElevation;
    }

    public int getBottomElevation() {
        return bottomElevation;
    }

    public void setBottomElevation(int bottomElevation) {
        this.bottomElevation = bottomElevation;
    }

    public String getSide() {
        return side != null ? side : SIDE_CUT;
    }

    public void setSide(String side) {
        this.side = side;
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
