package com.plot.ui.panel.gallery;

/**
 * 图库放置模式全局开关，避免画布工具与放置点击冲突。
 */
public final class GalleryPlacementGuard {
    private static volatile boolean active;

    private GalleryPlacementGuard() {
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
