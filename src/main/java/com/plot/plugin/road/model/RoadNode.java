package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 道路网络节点（路口或端点）
 */
public class RoadNode {
    public static final double MIN_CORNER_RADIUS = 0.0;
    public static final double MAX_CORNER_RADIUS = 8.0;
    public static final double DEFAULT_CORNER_RADIUS = 2.0;

    private final String id;
    private Vec2d position;
    private Double manualElevation;
    /** 路缘石圆角半径（格），null 表示使用网络默认值 */
    private Double cornerRadius;
    private JunctionMarkingSetting stopLines = JunctionMarkingSetting.AUTO;
    private JunctionMarkingSetting continuedMarkings = JunctionMarkingSetting.AUTO;
    private JunctionMarkingSetting crosswalks = JunctionMarkingSetting.AUTO;
    private JunctionMarkingSetting turnArrows = JunctionMarkingSetting.AUTO;
    private final Set<String> connectedEdgeIds;

    public RoadNode(Vec2d position) {
        this(UUID.randomUUID().toString(), position, null, null, new LinkedHashSet<>());
    }

    public RoadNode(String id, Vec2d position, Double manualElevation, Set<String> connectedEdgeIds) {
        this(id, position, manualElevation, null, connectedEdgeIds);
    }

    public RoadNode(
            String id,
            Vec2d position,
            Double manualElevation,
            Double cornerRadius,
            Set<String> connectedEdgeIds) {
        this.id = id;
        this.position = position != null ? position.copy() : new Vec2d(0, 0);
        this.manualElevation = manualElevation;
        this.cornerRadius = clampCornerRadius(cornerRadius);
        this.connectedEdgeIds = connectedEdgeIds != null
            ? new LinkedHashSet<>(connectedEdgeIds)
            : new LinkedHashSet<>();
    }

    public String getId() {
        return id;
    }

    public Vec2d getPosition() {
        return position.copy();
    }

    public void setPosition(Vec2d position) {
        this.position = position != null ? position.copy() : new Vec2d(0, 0);
    }

    public Double getManualElevation() {
        return manualElevation;
    }

    public void setManualElevation(Double manualElevation) {
        this.manualElevation = manualElevation;
    }

    public Double getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(Double cornerRadius) {
        this.cornerRadius = clampCornerRadius(cornerRadius);
    }

    public double getEffectiveCornerRadius(double networkDefault) {
        return cornerRadius != null ? cornerRadius : networkDefault;
    }

    public JunctionMarkingSetting getStopLines() {
        return stopLines;
    }

    public void setStopLines(JunctionMarkingSetting stopLines) {
        this.stopLines = stopLines != null ? stopLines : JunctionMarkingSetting.AUTO;
    }

    public JunctionMarkingSetting getContinuedMarkings() {
        return continuedMarkings;
    }

    public void setContinuedMarkings(JunctionMarkingSetting continuedMarkings) {
        this.continuedMarkings = continuedMarkings != null ? continuedMarkings : JunctionMarkingSetting.AUTO;
    }

    public JunctionMarkingSetting getCrosswalks() {
        return crosswalks;
    }

    public void setCrosswalks(JunctionMarkingSetting crosswalks) {
        this.crosswalks = crosswalks != null ? crosswalks : JunctionMarkingSetting.AUTO;
    }

    public JunctionMarkingSetting getTurnArrows() {
        return turnArrows;
    }

    public void setTurnArrows(JunctionMarkingSetting turnArrows) {
        this.turnArrows = turnArrows != null ? turnArrows : JunctionMarkingSetting.AUTO;
    }

    public boolean isJunction() {
        return getDegree() >= 3;
    }

    public Set<String> getConnectedEdgeIds() {
        return Collections.unmodifiableSet(connectedEdgeIds);
    }

    public void addEdge(String edgeId) {
        if (edgeId != null) {
            connectedEdgeIds.add(edgeId);
        }
    }

    public void removeEdge(String edgeId) {
        connectedEdgeIds.remove(edgeId);
    }

    public int getDegree() {
        return connectedEdgeIds.size();
    }

    RoadNode copy() {
        RoadNode copy = new RoadNode(id, position, manualElevation, cornerRadius, connectedEdgeIds);
        copy.stopLines = stopLines;
        copy.continuedMarkings = continuedMarkings;
        copy.crosswalks = crosswalks;
        copy.turnArrows = turnArrows;
        return copy;
    }

    private static Double clampCornerRadius(Double radius) {
        if (radius == null) {
            return null;
        }
        return Math.max(MIN_CORNER_RADIUS, Math.min(MAX_CORNER_RADIUS, radius));
    }
}
