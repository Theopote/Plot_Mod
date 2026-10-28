package com.plot.plugin.earthwork.solver;

/**
 * 剖面截线会话参数（世界方块平面）：走向角、参考点、看向侧。
 * <p>
 * 角度 0 表示沿 +X，π/2 沿 +Z；看向为截线走向的左侧（{@code viewSign=+1}）或右侧（{@code -1}）。
 */
public final class EarthworkSectionCutSpec {
    public static final EarthworkSectionCutSpec AUTOMATIC =
        new EarthworkSectionCutSpec(0.0, 0.0, 0.0, 1, false);

    private final double originX;
    private final double originZ;
    private final double angleRadians;
    private final int viewSign;
    private final boolean manual;

    public EarthworkSectionCutSpec(
            double originX,
            double originZ,
            double angleRadians,
            int viewSign,
            boolean manual) {
        this.originX = originX;
        this.originZ = originZ;
        this.angleRadians = normalizeAngle(angleRadians);
        this.viewSign = viewSign >= 0 ? 1 : -1;
        this.manual = manual;
    }

    public static EarthworkSectionCutSpec horizontal(double originX, double originZ) {
        return new EarthworkSectionCutSpec(originX, originZ, 0.0, 1, true);
    }

    public static EarthworkSectionCutSpec vertical(double originX, double originZ) {
        return new EarthworkSectionCutSpec(originX, originZ, Math.PI / 2.0, 1, true);
    }

    public double originX() {
        return originX;
    }

    public double originZ() {
        return originZ;
    }

    public double angleRadians() {
        return angleRadians;
    }

    public int viewSign() {
        return viewSign;
    }

    public boolean manual() {
        return manual;
    }

    /** 截线单位方向（世界 XZ）。 */
    public double dirX() {
        return Math.cos(angleRadians);
    }

    public double dirZ() {
        return Math.sin(angleRadians);
    }

    /** 看向单位法向（垂直于截线，指向观察侧）。 */
    public double viewNx() {
        return -Math.sin(angleRadians) * viewSign;
    }

    public double viewNz() {
        return Math.cos(angleRadians) * viewSign;
    }

    public EarthworkSectionCutSpec rotateDegrees(double deltaDegrees) {
        return new EarthworkSectionCutSpec(
            originX, originZ, angleRadians + Math.toRadians(deltaDegrees), viewSign, true);
    }

    /** 沿看向法向平移（正值移向看向侧）。 */
    public EarthworkSectionCutSpec nudgeBlocks(double blocks) {
        return new EarthworkSectionCutSpec(
            originX + viewNx() * blocks,
            originZ + viewNz() * blocks,
            angleRadians,
            viewSign,
            true);
    }

    public EarthworkSectionCutSpec flipView() {
        return new EarthworkSectionCutSpec(originX, originZ, angleRadians, -viewSign, true);
    }

    public EarthworkSectionCutSpec withOrigin(double ox, double oz) {
        return new EarthworkSectionCutSpec(ox, oz, angleRadians, viewSign, manual);
    }

    public EarthworkSectionCutSpec asManual() {
        if (manual) {
            return this;
        }
        return new EarthworkSectionCutSpec(originX, originZ, angleRadians, viewSign, true);
    }

    private static double normalizeAngle(double radians) {
        double twoPi = Math.PI * 2.0;
        double a = radians % twoPi;
        if (a < 0.0) {
            a += twoPi;
        }
        return a;
    }
}
