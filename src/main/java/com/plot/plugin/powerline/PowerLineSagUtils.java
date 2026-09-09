package com.plot.plugin.powerline;

import java.util.ArrayList;
import java.util.List;

/**
 * 导线下垂高度计算（抛物线近似，纯函数）。
 */
public final class PowerLineSagUtils {

    /** 单跨最大下垂深度（格），防止长跨距 + 大比例产生不合理垂弧。 */
    public static final double DEFAULT_MAX_SAG_DEPTH = 12.0;

    private PowerLineSagUtils() {
    }

    /**
     * 计算一跨导线沿跨距方向的下垂高度序列。
     */
    public static List<Double> computeSagProfile(
            double spanLength,
            double startHeight,
            double endHeight,
            double sagRatio,
            int sampleCount) {
        return computeSagProfile(
            spanLength,
            startHeight,
            endHeight,
            sagRatio,
            sampleCount,
            DEFAULT_MAX_SAG_DEPTH);
    }

    /**
     * 计算一跨导线沿跨距方向的下垂高度序列。
     *
     * @param maxSagDepth 最大下垂深度（格），{@code <= 0} 时不限制
     */
    public static List<Double> computeSagProfile(
            double spanLength,
            double startHeight,
            double endHeight,
            double sagRatio,
            int sampleCount,
            double maxSagDepth) {
        int count = Math.max(2, sampleCount);
        List<Double> profile = new ArrayList<>(count);
        if (count == 1) {
            profile.add(startHeight);
            return profile;
        }

        double rawSagDepth = Math.max(0.0, spanLength) * Math.max(0.0, sagRatio);
        double sagDepth = maxSagDepth > 0.0
            ? Math.min(rawSagDepth, maxSagDepth)
            : rawSagDepth;
        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            double linear = startHeight + (endHeight - startHeight) * t;
            double sag = 4.0 * sagDepth * t * (1.0 - t);
            profile.add(linear - sag);
        }
        return profile;
    }
}
