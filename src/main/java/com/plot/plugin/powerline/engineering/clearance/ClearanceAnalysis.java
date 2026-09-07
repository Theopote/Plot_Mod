package com.plot.plugin.powerline.engineering.clearance;

import com.plot.api.geometry.Vec2d;

/** 单跨净空分析摘要。 */
public class ClearanceAnalysis {
    private double minimumClearance = Double.MAX_VALUE;
    private Vec2d criticalLocation;
    private double conductorY;
    private double terrainY;
    private String spanId;
    private String attachmentId;

    public double getMinimumClearance() {
        return minimumClearance;
    }

    public void setMinimumClearance(double minimumClearance) {
        this.minimumClearance = minimumClearance;
    }

    public Vec2d getCriticalLocation() {
        return criticalLocation != null ? criticalLocation.copy() : null;
    }

    public void setCriticalLocation(Vec2d criticalLocation) {
        this.criticalLocation = criticalLocation != null ? criticalLocation.copy() : null;
    }

    public double getConductorY() {
        return conductorY;
    }

    public void setConductorY(double conductorY) {
        this.conductorY = conductorY;
    }

    public double getTerrainY() {
        return terrainY;
    }

    public void setTerrainY(double terrainY) {
        this.terrainY = terrainY;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }
}
