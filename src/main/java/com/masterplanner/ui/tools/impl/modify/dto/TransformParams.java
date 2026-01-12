package com.masterplanner.ui.tools.impl.modify.dto;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.ui.tools.impl.modify.enums.TransformMode;
import com.masterplanner.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;

import java.util.Objects;

/**
 * 变换参数数据传输对象
 * 封装变换操作所需的所有参数
 */
public final class TransformParams {
    
    private final Vec2d dragVector;
    private final TransformMode mode;
    private final boolean maintainAspectRatio;
    private final boolean centerScale;
    private final boolean rotationMode;
    private final ControlPointType controlPointType;
    private final int controlPointIndex;
    private final Vec2d anchorPoint;
    private final Vec2d originalControlPoint;
    private final Vec2d newControlPoint;
    private final double rotationAngle;
    private final double scaleX;
    private final double scaleY;
    
    private TransformParams(Builder builder) {
        this.dragVector = builder.dragVector;
        this.mode = builder.mode;
        this.maintainAspectRatio = builder.maintainAspectRatio;
        this.centerScale = builder.centerScale;
        this.rotationMode = builder.rotationMode;
        this.controlPointType = builder.controlPointType;
        this.controlPointIndex = builder.controlPointIndex;
        this.anchorPoint = builder.anchorPoint;
        this.originalControlPoint = builder.originalControlPoint;
        this.newControlPoint = builder.newControlPoint;
        this.rotationAngle = builder.rotationAngle;
        this.scaleX = builder.scaleX;
        this.scaleY = builder.scaleY;
    }
    
    // Getters
    public Vec2d getDragVector() { return dragVector; }
    public TransformMode getMode() { return mode; }
    public boolean isCenterScale() { return centerScale; }
    public boolean isRotationMode() { return rotationMode; }
    public ControlPointType getControlPointType() { return controlPointType; }
    public int getControlPointIndex() { return controlPointIndex; }
    public Vec2d getAnchorPoint() { return anchorPoint; }
    public double getRotationAngle() { return rotationAngle; }

    /**
     * 变换参数构建器
     */
    public static class Builder {
        private Vec2d dragVector;
        private TransformMode mode = TransformMode.FREE;
        private boolean maintainAspectRatio = false;
        private boolean centerScale = false;
        private boolean rotationMode = false;
        private ControlPointType controlPointType;
        private int controlPointIndex = -1;
        private Vec2d anchorPoint;
        private Vec2d originalControlPoint;
        private Vec2d newControlPoint;
        private double rotationAngle = 0.0;
        private double scaleX = 1.0;
        private double scaleY = 1.0;

        // Builder setter methods
        public Builder dragVector(Vec2d dragVector) {
            this.dragVector = dragVector;
            return this;
        }

        public Builder mode(TransformMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder maintainAspectRatio(boolean maintainAspectRatio) {
            this.maintainAspectRatio = maintainAspectRatio;
            return this;
        }

        public Builder centerScale(boolean centerScale) {
            this.centerScale = centerScale;
            return this;
        }

        public Builder controlPointType(ControlPointType controlPointType) {
            this.controlPointType = controlPointType;
            return this;
        }

        public Builder controlPointIndex(int controlPointIndex) {
            this.controlPointIndex = controlPointIndex;
            return this;
        }

        public Builder anchorPoint(Vec2d anchorPoint) {
            this.anchorPoint = anchorPoint;
            return this;
        }

        public TransformParams build() {
            return new TransformParams(this);
        }
    }
    
    /**
     * 创建默认的变换参数构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformParams that = (TransformParams) o;
        return maintainAspectRatio == that.maintainAspectRatio &&
               centerScale == that.centerScale &&
               rotationMode == that.rotationMode &&
               controlPointIndex == that.controlPointIndex &&
               Double.compare(that.rotationAngle, rotationAngle) == 0 &&
               Double.compare(that.scaleX, scaleX) == 0 &&
               Double.compare(that.scaleY, scaleY) == 0 &&
               Objects.equals(dragVector, that.dragVector) &&
               mode == that.mode &&
               controlPointType == that.controlPointType &&
               Objects.equals(anchorPoint, that.anchorPoint) &&
               Objects.equals(originalControlPoint, that.originalControlPoint) &&
               Objects.equals(newControlPoint, that.newControlPoint);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(dragVector, mode, maintainAspectRatio, centerScale, 
                           rotationMode, controlPointType, controlPointIndex, 
                           anchorPoint, originalControlPoint, newControlPoint, 
                           rotationAngle, scaleX, scaleY);
    }
    
    @Override
    public String toString() {
        return "TransformParams{" +
               "mode=" + mode +
               ", maintainAspectRatio=" + maintainAspectRatio +
               ", centerScale=" + centerScale +
               ", rotationMode=" + rotationMode +
               ", controlPointIndex=" + controlPointIndex +
               ", rotationAngle=" + rotationAngle +
               ", scaleX=" + scaleX +
               ", scaleY=" + scaleY +
               '}';
    }
}