package com.plot.plugin.powerline;

import java.util.ArrayList;
import java.util.List;

/**
 * 导线下垂高度计算（抛物线近似，纯函数）。
 */
public final class PowerLineSagUtils {

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
        int count = Math.max(2, sampleCount);
        List<Double> profile = new ArrayList<>(count);
        if (count == 1) {
            profile.add(startHeight);
            return profile;
        }

        double sagDepth = Math.max(0.0, spanLength) * Math.max(0.0, sagRatio);
        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            double linear = startHeight + (endHeight - startHeight) * t;
            double sag = 4.0 * sagDepth * t * (1.0 - t);
            profile.add(linear - sag);
        }
        return profile;
    }
}
