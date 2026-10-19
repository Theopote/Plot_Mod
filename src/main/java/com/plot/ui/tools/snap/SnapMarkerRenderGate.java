package com.plot.ui.tools.snap;

/**
 * 控制 SnapManager 是否可以在当前线程/帧内绘制 ImGui 吸附标记。
 * BackgroundDrawList 只能在 ImGui 渲染阶段安全使用。
 */
public final class SnapMarkerRenderGate {

    private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

    private SnapMarkerRenderGate() {
    }

    public static void setAllowed(boolean allowed) {
        ALLOWED.set(allowed);
    }

    public static boolean isAllowed() {
        return Boolean.TRUE.equals(ALLOWED.get());
    }
}
