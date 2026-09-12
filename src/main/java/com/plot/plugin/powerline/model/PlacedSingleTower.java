package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;

import java.util.Objects;
import java.util.UUID;

/** 已落地单塔记录（独立于 {@link PowerLineFootprint}）。 */
public final class PlacedSingleTower {
    private final String id;
    private final double planX;
    private final double planY;
    private final int rotationQuadrant;
    private final String designLabel;
    private final String styleLineId;

    public PlacedSingleTower(
            Vec2d planPoint,
            int rotationQuadrant,
            String designLabel,
            String styleLineId) {
        this.id = UUID.randomUUID().toString();
        this.planX = planPoint != null ? planPoint.x : 0.0;
        this.planY = planPoint != null ? planPoint.y : 0.0;
        this.rotationQuadrant = rotationQuadrant;
        this.designLabel = designLabel != null ? designLabel : "";
        this.styleLineId = styleLineId;
    }

    PlacedSingleTower(
            String id,
            double planX,
            double planY,
            int rotationQuadrant,
            String designLabel,
            String styleLineId) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.planX = planX;
        this.planY = planY;
        this.rotationQuadrant = rotationQuadrant;
        this.designLabel = designLabel != null ? designLabel : "";
        this.styleLineId = styleLineId;
    }

    public String getId() {
        return id;
    }

    public Vec2d getPlanPoint() {
        return new Vec2d(planX, planY);
    }

    public int getRotationQuadrant() {
        return rotationQuadrant;
    }

    public String getDesignLabel() {
        return designLabel;
    }

    public String getStyleLineId() {
        return styleLineId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PlacedSingleTower other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
