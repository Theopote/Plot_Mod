package com.plot.plugin.earthwork;

/**
 * 放坡 + 多级平台（bench）剖面几何。
 * <p>
 * {@code benchWidth <= 0} 时为连续直线放坡；否则按「坡面段 → 水平平台段」周期交替：
 * <ul>
 *   <li>坡面段：水平宽度 {@code benchWidth}，升高 {@code ceil(span / pitch)}</li>
 *   <li>平台段：水平宽度 {@code benchWidth}，高程不变</li>
 * </ul>
 */
public final class SlopeBenchProfile {

    public enum SegmentKind {
        SLOPE,
        BENCH
    }

    public record SegmentAtDistance(SegmentKind kind, int cycleIndex, double offsetInSegment, double segmentLength) {
    }

    private SlopeBenchProfile() {
    }

    /**
     * 从坡脚起算的水平距离 {@code distance} 处，相对坡脚累计升高（格）。
     */
    public static int computeRiseAtDistance(double distance, double pitchRatio, int benchWidth) {
        if (distance <= 0.0) {
            return 0;
        }
        double pitch = Math.max(0.5, pitchRatio);
        if (benchWidth <= 0) {
            return (int) Math.ceil(distance / pitch);
        }
        double remaining = distance;
        int totalRise = 0;
        boolean slopePhase = true;
        while (remaining > 1e-9) {
            double span = Math.min(remaining, benchWidth);
            if (slopePhase) {
                totalRise += (int) Math.ceil(span / pitch);
            }
            remaining -= span;
            if (remaining <= 1e-9) {
                break;
            }
            slopePhase = !slopePhase;
        }
        return totalRise;
    }

    /**
     * 定位距离所在区段，供调试与画布标注。
     */
    public static SegmentAtDistance segmentAtDistance(double distance, int benchWidth) {
        if (distance <= 0.0 || benchWidth <= 0) {
            return new SegmentAtDistance(SegmentKind.SLOPE, 0, Math.max(0.0, distance), Math.max(distance, 0.0));
        }
        double remaining = distance;
        boolean slopePhase = true;
        int cycle = 0;
        while (remaining > 1e-9) {
            double span = Math.min(remaining, benchWidth);
            SegmentKind kind = slopePhase ? SegmentKind.SLOPE : SegmentKind.BENCH;
            if (remaining <= benchWidth + 1e-9) {
                return new SegmentAtDistance(kind, cycle, span, benchWidth);
            }
            remaining -= span;
            if (remaining <= 1e-9) {
                return new SegmentAtDistance(kind, cycle, span, benchWidth);
            }
            if (!slopePhase) {
                cycle++;
            }
            slopePhase = !slopePhase;
        }
        return new SegmentAtDistance(SegmentKind.SLOPE, cycle, 0.0, benchWidth);
    }
}
