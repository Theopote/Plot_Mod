package com.plot.plugin.powerline.placement;

/** 单塔放置模式全局开关，避免与绘制/选择工具争抢画布点击。 */
public final class SingleTowerPickGuard {
    private static volatile boolean active;

    private SingleTowerPickGuard() {
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
