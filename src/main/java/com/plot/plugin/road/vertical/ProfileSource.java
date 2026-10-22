package com.plot.plugin.road.vertical;

/** 适应地形模式下纵断面生成权威来源。 */
public enum ProfileSource {
    /** 按当前路径与地形自动设计。 */
    AUTO,
    /** 按已确认的用户纵断面生成。 */
    EDITED;

    public static ProfileSource fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
