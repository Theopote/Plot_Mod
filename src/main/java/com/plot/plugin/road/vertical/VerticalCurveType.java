package com.plot.plugin.road.vertical;

/**
 * 竖曲线类型：由相邻切线坡度代数差判定。
 */
public enum VerticalCurveType {
    /** 凸形竖曲线（上坡转下坡或坡度减小） */
    CREST,
    /** 凹形竖曲线（下坡转上坡或坡度增大） */
    SAG;

    public static VerticalCurveType fromGradesPercent(double incomingGradePercent, double outgoingGradePercent) {
        return outgoingGradePercent < incomingGradePercent ? CREST : SAG;
    }

    public static VerticalCurveType fromStored(String value) {
        if (value == null || value.isBlank()) {
            return CREST;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return CREST;
        }
    }
}
