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
    private final String id;
    private Vec2d position;
    private Double manualElevation;
    private final Set<String> connectedEdgeIds;

    public RoadNode(Vec2d position) {
        this(UUID.randomUUID().toString(), position, null, new LinkedHashSet<>());
    }

    public RoadNode(String id, Vec2d position, Double manualElevation, Set<String> connectedEdgeIds) {
        this.id = id;
        this.position = position != null ? position.copy() : new Vec2d(0, 0);
        this.manualElevation = manualElevation;
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
        return new RoadNode(id, position, manualElevation, connectedEdgeIds);
    }
}
