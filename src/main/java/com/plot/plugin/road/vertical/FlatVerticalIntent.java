package com.plot.plugin.road.vertical;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Authoritative vertical design for {@link RoadVerticalMode#FLAT} roads. */
public final class FlatVerticalIntent {

    private double baseElevation;
    private final Map<String, Double> intersectionOverrides = new LinkedHashMap<>();

    public FlatVerticalIntent(double baseElevation) {
        if (!Double.isFinite(baseElevation)) {
            throw new IllegalArgumentException("baseElevation must be finite");
        }
        this.baseElevation = baseElevation;
    }

    public FlatVerticalIntent(double baseElevation, Map<String, Double> intersectionOverrides) {
        this(baseElevation);
        if (intersectionOverrides != null) {
            for (Map.Entry<String, Double> entry : intersectionOverrides.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && Double.isFinite(entry.getValue())) {
                    this.intersectionOverrides.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public double getBaseElevation() {
        return baseElevation;
    }

    public void setBaseElevation(double baseElevation) {
        if (!Double.isFinite(baseElevation)) {
            throw new IllegalArgumentException("baseElevation must be finite");
        }
        this.baseElevation = baseElevation;
    }

    public Map<String, Double> getIntersectionOverrides() {
        return Map.copyOf(intersectionOverrides);
    }

    public boolean hasOverride(String nodeId) {
        return nodeId != null && intersectionOverrides.containsKey(nodeId);
    }

    public Double getIntersectionOverride(String nodeId) {
        return nodeId != null ? intersectionOverrides.get(nodeId) : null;
    }

    public void setIntersectionOverride(String nodeId, double elevation) {
        if (nodeId == null || nodeId.isBlank() || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("invalid intersection override");
        }
        intersectionOverrides.put(nodeId, elevation);
    }

    public void removeOverride(String nodeId) {
        if (nodeId != null) {
            intersectionOverrides.remove(nodeId);
        }
    }

    public FlatVerticalIntent copy() {
        return new FlatVerticalIntent(baseElevation, intersectionOverrides);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FlatVerticalIntent that)) {
            return false;
        }
        return Double.compare(baseElevation, that.baseElevation) == 0
            && intersectionOverrides.equals(that.intersectionOverrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseElevation, intersectionOverrides);
    }
}
