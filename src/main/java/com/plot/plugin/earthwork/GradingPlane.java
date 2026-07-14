package com.plot.plugin.earthwork;

/**
 * 整平目标平面：worldY = coeffX * worldX + coeffZ * worldZ + intercept。
 */
public record GradingPlane(double coeffX, double coeffZ, double intercept) {

    public static GradingPlane flat(int elevation) {
        return new GradingPlane(0.0, 0.0, elevation);
    }

    public int evaluateAt(int worldX, int worldZ) {
        return (int) Math.round(coeffX * worldX + coeffZ * worldZ + intercept);
    }

    public double evaluateAtExact(double worldX, double worldZ) {
        return coeffX * worldX + coeffZ * worldZ + intercept;
    }

    public boolean isFlat() {
        return Math.abs(coeffX) < 1e-9 && Math.abs(coeffZ) < 1e-9;
    }
}
