package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路平面线形：原点、起始方位角与有序线元列表。
 * <p>
 * 线元沿链顺序拼接；里程 0 对应原点。空列表表示未定义线形（仍使用 polyline 中心线）。
 */
public final class RoadHorizontalAlignment {

    private Vec2d origin = new Vec2d(0, 0);
    private double startBearingRadians;
    private final List<HorizontalAlignmentElement> elements = new ArrayList<>();

    public RoadHorizontalAlignment() {
    }

    public RoadHorizontalAlignment(Vec2d origin, double startBearingRadians, List<HorizontalAlignmentElement> elements) {
        if (origin != null) {
            this.origin = origin.copy();
        }
        this.startBearingRadians = startBearingRadians;
        if (elements != null) {
            for (HorizontalAlignmentElement element : elements) {
                addElement(element);
            }
        }
    }

    public Vec2d getOrigin() {
        return origin.copy();
    }

    public void setOrigin(Vec2d origin) {
        this.origin = origin != null ? origin.copy() : new Vec2d(0, 0);
    }

    public double getStartBearingRadians() {
        return startBearingRadians;
    }

    public void setStartBearingRadians(double startBearingRadians) {
        this.startBearingRadians = startBearingRadians;
    }

    public List<HorizontalAlignmentElement> getElements() {
        return List.copyOf(elements);
    }

    public void addElement(HorizontalAlignmentElement element) {
        if (element != null) {
            elements.add(element);
        }
    }

    public void clearElements() {
        elements.clear();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public double totalLength() {
        double total = 0.0;
        for (HorizontalAlignmentElement element : elements) {
            total += element.getLength();
        }
        return total;
    }

    public RoadHorizontalAlignment copy() {
        List<HorizontalAlignmentElement> copied = new ArrayList<>();
        for (HorizontalAlignmentElement element : elements) {
            copied.add(element.copy());
        }
        return new RoadHorizontalAlignment(origin, startBearingRadians, copied);
    }
}
