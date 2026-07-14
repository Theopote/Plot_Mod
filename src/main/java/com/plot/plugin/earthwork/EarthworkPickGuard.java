package com.plot.plugin.earthwork;

/**
 * 土方插件画布拾取模式全局开关，避免与绘制/选择工具争抢点击。
 */
public final class EarthworkPickGuard {
    private static volatile boolean active;

    private EarthworkPickGuard() {
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
