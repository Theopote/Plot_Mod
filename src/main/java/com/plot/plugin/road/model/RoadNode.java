package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadParameterLimits;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 道路网络节点（路口或端点）
 */
public class RoadNode {
    public static final double MIN_CORNER_RADIUS = 0.0;
    public static final double MAX_CORNER_RADIUS = 12.0;
    public static final double DEFAULT_CORNER_RADIUS = 2.0;

    private final String id;
    private Vec2d position;
    private Double manualElevation;
    /** 是否标记为立体交叉（false 时忽略 elevatedRoadId/crossingClearance） */
    private boolean gradeSeparated;
    /** 跨越方道路 ID；null 表示由程序自动判断 */
    private String elevatedRoadId;
    /** 立体交叉净空高度（格），null 表示使用全局默认值 */
    private Double crossingClearance;
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
        this.manualElevation = RoadParameterLimits.clampManualElevation(manualElevation);
    }

    public boolean isGradeSeparated() {
        return gradeSeparated;
    }

    public void setGradeSeparated(boolean gradeSeparated) {
        this.gradeSeparated = gradeSeparated;
    }

    public String getElevatedRoadId() {
        return elevatedRoadId;
    }

    public void setElevatedRoadId(String elevatedRoadId) {
        this.elevatedRoadId = elevatedRoadId;
    }

    public Double getCrossingClearance() {
        return crossingClearance;
    }

    public void setCrossingClearance(Double crossingClearance) {
        this.crossingClearance = RoadParameterLimits.clampCrossingClearanceNullable(crossingClearance);
    }

    public void clearGradeSeparation() {
        this.gradeSeparated = false;
        this.elevatedRoadId = null;
        this.crossingClearance = null;
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
        copy.gradeSeparated = gradeSeparated;
        copy.elevatedRoadId = elevatedRoadId;
        copy.crossingClearance = crossingClearance;
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
