package com.plot.plugin.earthwork.grading;

import java.util.function.IntUnaryOperator;

/**
 * 放坡日照线（daylight line）求解：沿边界外法向搜索坡面与现状地面的首次交点。
 * <p>
 * 与 {@link SlopeBenchProfile} 配合，支持连续坡面与多级平台剖面。
 */
public final class SlopeDaylightSolver {

    public enum SlopeMode {
        /** 挖方：坡脚低于现状，坡面向上升起直至与地面相交 */
        CUT,
        /** 填方：坡脚高于现状，坡面向下降落直至与地面相交 */
        FILL
    }

    private SlopeDaylightSolver() {
    }

    /**
     * 相对坡脚的水平距离 {@code distance} 处，设计坡面标高（不裁剪现状）。
     */
    public static int slopeHeightAtDistance(
            int toeY,
            SlopeMode mode,
            double distance,
            double pitchRatio,
            int benchWidth) {
        if (distance <= 0.0) {
            return toeY;
        }
        int rise = SlopeBenchProfile.computeRiseAtDistance(distance, pitchRatio, benchWidth);
        return mode == SlopeMode.CUT ? toeY + rise : toeY - rise;
    }

    /**
     * 自坡脚起算，坡面与现状地面的首次交汇水平距离（格）。
     * <p>
     * 若在 {@code maxReach} 内未交汇，返回 {@code maxReach}（坡面在 reach 处截止）。
     */
    public static int findDaylightDistance(
            int toeY,
            SlopeMode mode,
            double pitchRatio,
            int benchWidth,
            IntUnaryOperator groundHeightAtOffset,
            int maxReach) {
        if (maxReach <= 0 || groundHeightAtOffset == null) {
            return 0;
        }
        int groundAtToe = groundHeightAtOffset.applyAsInt(0);
        if (mode == SlopeMode.CUT && groundAtToe <= toeY) {
            return 0;
        }
        if (mode == SlopeMode.FILL && groundAtToe >= toeY) {
            return 0;
        }

        for (int offset = 1; offset <= maxReach; offset++) {
            int ground = groundHeightAtOffset.applyAsInt(offset);
            int slope = slopeHeightAtDistance(toeY, mode, offset, pitchRatio, benchWidth);
            if (mode == SlopeMode.CUT && slope >= ground) {
                return offset;
            }
            if (mode == SlopeMode.FILL && slope <= ground) {
                return offset;
            }
        }
        return maxReach;
    }

    /**
     * 分区外侧格点的目标标高：坡面范围内用设计坡面；超出日照线则保持现状。
     */
    public static int resolveExteriorTargetY(
            int toeY,
            int existingGroundY,
            double distanceFromBoundary,
            SlopeMode mode,
            double pitchRatio,
            int benchWidth,
            IntUnaryOperator groundHeightAtOffset,
            int maxReach) {
        if (distanceFromBoundary <= 0.0) {
            return toeY;
        }
        if (maxReach <= 0) {
            return existingGroundY;
        }

        IntUnaryOperator profile = groundHeightAtOffset != null
            ? groundHeightAtOffset
            : offset -> existingGroundY;

        int daylightDistance = findDaylightDistance(
            toeY, mode, pitchRatio, benchWidth, profile, maxReach);

        if (distanceFromBoundary > daylightDistance + 1e-9) {
            return existingGroundY;
        }

        int slopeTarget = slopeHeightAtDistance(
            toeY, mode, distanceFromBoundary, pitchRatio, benchWidth);

        if (mode == SlopeMode.CUT) {
            return Math.min(slopeTarget, existingGroundY);
        }
        return Math.max(slopeTarget, existingGroundY);
    }

    public static SlopeMode modeFor(int existingGroundY, int toeY) {
        if (existingGroundY > toeY) {
            return SlopeMode.CUT;
        }
        if (existingGroundY < toeY) {
            return SlopeMode.FILL;
        }
        return SlopeMode.CUT;
    }
}
