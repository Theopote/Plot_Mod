package com.plot.plugin.road;

/**
 * 建筑/道路工程坡度格式转换与解析（百分比 %、放坡比例 1:n）。
 */
public final class SlopeFormatUtils {
    public static final float MIN_BATTER_RATIO = 0.5f;
    public static final float MAX_BATTER_RATIO = 5.0f;
    public static final float MIN_GRADE_PERCENT = 0.0f;
    public static final float MAX_GRADE_PERCENT = 45.0f;

    public enum DisplayFormat {
        PERCENT,
        RATIO
    }

    private SlopeFormatUtils() {
    }

    /**
     * 纵坡百分比 → 放坡比例 1:n 中的 n（水平延伸 / 垂直变化 1 单位）。
     */
    public static float percentToHorizontalRatio(float percent) {
        if (percent <= 0.0f) {
            return Float.POSITIVE_INFINITY;
        }
        return 100.0f / percent;
    }

    /**
     * 放坡比例 1:n 中的 n → 纵坡百分比。
     */
    public static float horizontalRatioToPercent(float ratio) {
        if (ratio <= 0.0f) {
            return 0.0f;
        }
        return 100.0f / ratio;
    }

    public static float clampGradePercent(float percent) {
        return Math.max(MIN_GRADE_PERCENT, Math.min(MAX_GRADE_PERCENT, percent));
    }

    public static float clampBatterRatio(float ratio) {
        return Math.max(MIN_BATTER_RATIO, Math.min(MAX_BATTER_RATIO, ratio));
    }

    public static String formatPercent(float percent) {
        return String.format("%.1f%%", percent);
    }

    public static String formatRatio(float horizontalRatio) {
        if (Float.isInfinite(horizontalRatio) || horizontalRatio <= 0.0f) {
            return "1:∞";
        }
        return String.format("1:%.1f", horizontalRatio);
    }

    public static String formatDualGrade(float percent) {
        float ratio = percentToHorizontalRatio(percent);
        if (Float.isInfinite(ratio)) {
            return formatPercent(percent);
        }
        return formatPercent(percent) + " ≈ " + formatRatio(ratio);
    }

    public static String formatDualBatter(float horizontalRatio) {
        float percent = horizontalRatioToPercent(horizontalRatio);
        return formatRatio(horizontalRatio) + " ≈ " + formatPercent(percent);
    }

    /**
     * 解析工程坡度文本输入。
     * 支持：{@code 10%}、{@code 10}、{@code 1:1.5}、{@code 1.5}（比例模式下 n 值）。
     */
    public static Float parseInput(String raw, DisplayFormat preferredFormat, boolean batterMode) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        if (text.contains(":")) {
            String[] parts = text.split(":", 2);
            if (parts.length == 2) {
                try {
                    float ratioN = Float.parseFloat(parts[1].trim().replace("%", ""));
                    if (batterMode) {
                        return clampBatterRatio(ratioN);
                    }
                    return clampGradePercent(horizontalRatioToPercent(ratioN));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        boolean hasPercent = text.endsWith("%");
        if (hasPercent) {
            text = text.substring(0, text.length() - 1).trim();
        }

        try {
            float value = Float.parseFloat(text);
            if (batterMode) {
                if (hasPercent || preferredFormat == DisplayFormat.PERCENT) {
                    return clampBatterRatio(percentToHorizontalRatio(value));
                }
                return clampBatterRatio(value);
            }
            if (hasPercent || preferredFormat == DisplayFormat.PERCENT) {
                return clampGradePercent(value);
            }
            return clampGradePercent(horizontalRatioToPercent(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
