package com.plot.plugin.pattern.pipeline;

/**
 * 轴对齐人字形铺装：奇偶行半砖偏移，砖内对角线方向交替翻转。
 */
public final class HerringbonePatternGeometry {
    private static final double BRICK_ASPECT = 0.5;

    private HerringbonePatternGeometry() {
    }

    public static int materialIndex(double x, double z, double brickWidth, int materialCount) {
        if (materialCount <= 0) {
            return 0;
        }
        double width = Math.max(1e-6, brickWidth);
        double height = width * BRICK_ASPECT;
        int row = PatternGridMath.floorDiv(z, height);
        double offsetX = (row & 1) != 0 ? width * 0.5 : 0.0;
        double effectiveX = x - offsetX;
        int col = PatternGridMath.floorDiv(effectiveX, width);
        double localX = effectiveX - col * width;
        double localZ = z - row * height;

        boolean upperRight;
        if ((row & 1) == 0) {
            upperRight = localX * height > localZ * width;
        } else {
            upperRight = (width - localX) * height > localZ * width;
        }
        int baseIndex = row * 2 + col;
        return PatternGridMath.positiveMod(baseIndex + (upperRight ? 1 : 0), materialCount);
    }
}
