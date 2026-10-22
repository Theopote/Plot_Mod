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
    /** 手动标高来源；null 表示遗留数据或未设置。 */
    private ManualElevationOrigin manualElevationOrigin;
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
    /** 自交叠交注册表 ID（物化后写入，供生成识别）。 */
    private String stackCrossingId;
    /** 同路自交立交：上下层由 {@link #stackUpperEdgeIds} 区分。 */
    private boolean stackGradeSeparated;
    private Double stackCrossingClearance;
    private final Set<String> stackLowerEdgeIds = new LinkedHashSet<>();
    private final Set<String> stackUpperEdgeIds = new LinkedHashSet<>();
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

    public ManualElevationOrigin getManualElevationOrigin() {
        return manualElevationOrigin;
    }

    /** 便捷写入：视为用户锁定（{@link ManualElevationOrigin#USER}）。 */
    public void setManualElevation(Double manualElevation) {
        setManualElevation(manualElevation, manualElevation == null ? null : ManualElevationOrigin.USER);
    }

    public void setManualElevation(Double manualElevation, ManualElevationOrigin origin) {
        if (manualElevation == null) {
            this.manualElevation = null;
            this.manualElevationOrigin = null;
            return;
        }
        this.manualElevation = RoadParameterLimits.clampManualElevation(manualElevation);
        this.manualElevationOrigin = origin;
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

    public String getStackCrossingId() {
        return stackCrossingId;
    }

    public void setStackCrossingId(String stackCrossingId) {
        this.stackCrossingId = stackCrossingId;
    }

    public boolean isStackGradeSeparated() {
        return stackGradeSeparated;
    }

    public void setStackGradeSeparated(boolean stackGradeSeparated) {
        this.stackGradeSeparated = stackGradeSeparated;
    }

    public Double getStackCrossingClearance() {
        return stackCrossingClearance;
    }

    public void setStackCrossingClearance(Double stackCrossingClearance) {
        this.stackCrossingClearance = RoadParameterLimits.clampCrossingClearanceNullable(stackCrossingClearance);
    }

    public Set<String> getStackLowerEdgeIds() {
        return Collections.unmodifiableSet(stackLowerEdgeIds);
    }

    public Set<String> getStackUpperEdgeIds() {
        return Collections.unmodifiableSet(stackUpperEdgeIds);
    }

    public void addStackLowerEdgeId(String edgeId) {
        if (edgeId != null) {
            stackLowerEdgeIds.add(edgeId);
        }
    }

    public void addStackUpperEdgeId(String edgeId) {
        if (edgeId != null) {
            stackUpperEdgeIds.add(edgeId);
        }
    }

    public void clearStackCrossingMetadata() {
        stackCrossingId = null;
        stackGradeSeparated = false;
        stackCrossingClearance = null;
        stackLowerEdgeIds.clear();
        stackUpperEdgeIds.clear();
    }

    public boolean isStackUpperEdge(String edgeId) {
        return edgeId != null && stackUpperEdgeIds.contains(edgeId);
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

    /** 清空连接边列表（加载后从边重建拓扑时使用）。 */
    public void clearConnectedEdges() {
        connectedEdgeIds.clear();
    }

    public int getDegree() {
        return connectedEdgeIds.size();
    }

    RoadNode copy() {
        RoadNode copy = new RoadNode(id, position, manualElevation, cornerRadius, connectedEdgeIds);
        copy.manualElevationOrigin = manualElevationOrigin;
        copy.gradeSeparated = gradeSeparated;
        copy.elevatedRoadId = elevatedRoadId;
        copy.crossingClearance = crossingClearance;
        copy.stopLines = stopLines;
        copy.continuedMarkings = continuedMarkings;
        copy.crosswalks = crosswalks;
        copy.turnArrows = turnArrows;
        copy.stackCrossingId = stackCrossingId;
        copy.stackGradeSeparated = stackGradeSeparated;
        copy.stackCrossingClearance = stackCrossingClearance;
        copy.stackLowerEdgeIds.addAll(stackLowerEdgeIds);
        copy.stackUpperEdgeIds.addAll(stackUpperEdgeIds);
        return copy;
    }

    private static Double clampCornerRadius(Double radius) {
        if (radius == null) {
            return null;
        }
        return Math.clamp(radius, MIN_CORNER_RADIUS, MAX_CORNER_RADIUS);
    }
}
