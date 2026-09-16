package com.plot.plugin.pattern.pipeline;

/**
 * 平顶六边形网格（轴向坐标 + 最近格点取整）。
 * <p>
 * {@code circumradius} 为六边形中心到顶点的距离；与 Red Blob Games 的 flat-top layout 一致。
 */
public final class HexagonalPatternGeometry {
    public record Axial(int q, int r) {
    }

    private HexagonalPatternGeometry() {
    }

    public static Axial pixelToAxial(double x, double z, double circumradius) {
        double size = Math.max(1e-6, circumradius);
        double fq = (2.0 / 3.0 * x) / size;
        double fr = (-1.0 / 3.0 * x + Math.sqrt(3) / 3.0 * z) / size;
        return roundAxial(fq, fr);
    }

    /**
     * {@code q + r} 在六边形网格上沿对角线周期分布，三色时比 {@code q - r} 更均匀。
     */
    public static int materialIndex(Axial axial, int materialCount) {
        if (materialCount <= 0) {
            return 0;
        }
        return PatternGridMath.positiveMod(axial.q + axial.r, materialCount);
    }

    static Axial roundAxial(double q, double r) {
        double s = -q - r;
        int rq = (int) Math.round(q);
        int rr = (int) Math.round(r);
        int rs = (int) Math.round(s);
        double qDiff = Math.abs(rq - q);
        double rDiff = Math.abs(rr - r);
        double sDiff = Math.abs(rs - s);
        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs;
        } else if (rDiff > sDiff) {
            rr = -rq - rs;
        }
        return new Axial(rq, rr);
    }
}
