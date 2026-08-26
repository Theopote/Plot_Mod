package com.plot.ui.canvas;

/**
 * 客户端画布访问点。Canvas 不属于 core state。
 */
public final class CanvasAccess {
    private static volatile Canvas canvas;

    private CanvasAccess() {
    }

    public static void set(Canvas value) {
        canvas = value;
    }

    public static Canvas get() {
        return canvas;
    }

    public static boolean isPresent() {
        return canvas != null;
    }
}
