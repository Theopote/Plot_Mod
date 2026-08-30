package com.plot.plugin.road.alignment;

/**
 * 单个平面线形线元。
 * <p>
 * 参数按 {@link HorizontalAlignmentElementType} 使用：
 * <ul>
 *   <li>TANGENT — {@code length}</li>
 *   <li>CIRCULAR_ARC — {@code length}, {@code radius}（正数）, {@code direction}</li>
 *   <li>SPIRAL — {@code length}, {@code spiralParameterA}（clothoid A，从切线 κ₀=0 起）</li>
 * </ul>
 */
public final class HorizontalAlignmentElement {

    private final HorizontalAlignmentElementType type;
    private final double length;
    private final Double radius;
    private final TurnDirection direction;
    private final Double spiralParameterA;

    private HorizontalAlignmentElement(
            HorizontalAlignmentElementType type,
            double length,
            Double radius,
            TurnDirection direction,
            Double spiralParameterA) {
        this.type = type;
        this.length = length;
        this.radius = radius;
        this.direction = direction != null ? direction : TurnDirection.LEFT;
        this.spiralParameterA = spiralParameterA;
    }

    public static HorizontalAlignmentElement tangent(double length) {
        if (length <= 0.0 || !Double.isFinite(length)) {
            throw new IllegalArgumentException("tangent length must be positive");
        }
        return new HorizontalAlignmentElement(HorizontalAlignmentElementType.TANGENT, length, null, null, null);
    }

    public static HorizontalAlignmentElement circularArc(double length, double radius, TurnDirection direction) {
        if (length <= 0.0 || !Double.isFinite(length)) {
            throw new IllegalArgumentException("arc length must be positive");
        }
        if (radius <= 0.0 || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("arc radius must be positive");
        }
        return new HorizontalAlignmentElement(
            HorizontalAlignmentElementType.CIRCULAR_ARC,
            length,
            radius,
            direction,
            null
        );
    }

    public static HorizontalAlignmentElement spiral(double length, double spiralParameterA) {
        if (length <= 0.0 || !Double.isFinite(length)) {
            throw new IllegalArgumentException("spiral length must be positive");
        }
        if (spiralParameterA <= 0.0 || !Double.isFinite(spiralParameterA)) {
            throw new IllegalArgumentException("spiral parameter A must be positive");
        }
        return new HorizontalAlignmentElement(
            HorizontalAlignmentElementType.SPIRAL,
            length,
            null,
            null,
            spiralParameterA
        );
    }

    public HorizontalAlignmentElementType getType() {
        return type;
    }

    public double getLength() {
        return length;
    }

    public Double getRadius() {
        return radius;
    }

    public TurnDirection getDirection() {
        return direction;
    }

    public Double getSpiralParameterA() {
        return spiralParameterA;
    }

    public HorizontalAlignmentElement copy() {
        return new HorizontalAlignmentElement(type, length, radius, direction, spiralParameterA);
    }
}
