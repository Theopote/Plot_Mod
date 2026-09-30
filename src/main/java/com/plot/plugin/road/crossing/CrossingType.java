package com.plot.plugin.road.crossing;

/** 平面交叉关系类型。 */
public enum CrossingType {
    AT_GRADE,
    GRADE_SEPARATED;

    public static CrossingType fromStored(String value) {
        if (value == null || value.isBlank()) {
            return AT_GRADE;
        }
        try {
            return CrossingType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AT_GRADE;
        }
    }
}
