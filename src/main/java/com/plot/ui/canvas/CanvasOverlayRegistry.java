package com.plot.ui.canvas;

import imgui.ImDrawList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 画布图层之上的插件叠加绘制注册表。
 */
public final class CanvasOverlayRegistry {

    @FunctionalInterface
    public interface Overlay {
        void render(ImDrawList drawList, CanvasCamera camera);
    }

    private static final List<Overlay> OVERLAYS = new CopyOnWriteArrayList<>();

    private CanvasOverlayRegistry() {
    }

    public static void register(Overlay overlay) {
        if (overlay != null && !OVERLAYS.contains(overlay)) {
            OVERLAYS.add(overlay);
        }
    }

    public static void unregister(Overlay overlay) {
        if (overlay != null) {
            OVERLAYS.remove(overlay);
        }
    }

    public static void renderAll(ImDrawList drawList, CanvasCamera camera) {
        if (drawList == null || camera == null || OVERLAYS.isEmpty()) {
            return;
        }
        for (Overlay overlay : OVERLAYS) {
            try {
                overlay.render(drawList, camera);
            } catch (Exception ignored) {
                // 单个叠加层失败不应影响画布主渲染
            }
        }
    }
}
