package com.plot.plugin.powerline.design.family;

/** 悬垂塔视觉尺寸档（与 {@link com.plot.plugin.powerline.model.TowerRole} 解耦）。 */
public enum SuspensionVariant {
    SMALL,
    MEDIUM,
    LARGE;

    public SuspensionVariant nextLarger() {
        return switch (this) {
            case SMALL -> MEDIUM;
            case MEDIUM -> LARGE;
            case LARGE -> null;
        };
    }
}
