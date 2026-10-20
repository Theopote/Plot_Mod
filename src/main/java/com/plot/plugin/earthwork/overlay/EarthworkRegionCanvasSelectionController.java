package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.ui.canvas.Canvas;
import imgui.ImGui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 土方区域画布选择：点击已认领区域选中，空白处清除选中。
 * 拾取会话激活时不抢占选择工具交互。
 */
public final class EarthworkRegionCanvasSelectionController {

    private static final double DRAG_THRESHOLD_PX = 4.0;

    private boolean pointerDown;
    private Vec2d pointerDownScreen;

    public void resetPointer() {
        pointerDown = false;
        pointerDownScreen = null;
    }

    public void tick(
            Canvas canvas,
            AppState appState,
            List<EarthworkRegionOverlayEntry> overlayEntries,
            boolean pickSessionActive,
            BooleanSupplier overlayVisible,
            Consumer<String> onRegionSelected) {
        if (canvas == null || appState == null || overlayVisible == null || onRegionSelected == null) {
            return;
        }
        if (!overlayVisible.getAsBoolean() || pickSessionActive) {
            resetPointer();
            return;
        }
        Vec2d mouseScreen = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (!canvas.isScreenPosInsideCanvas(mouseScreen)) {
            resetPointer();
            return;
        }
        if (ImGui.getIO().getWantCaptureMouse()) {
            resetPointer();
            return;
        }
        BaseTool tool = appState.getCurrentTool();
        if (tool == null || !"select".equals(tool.getId())) {
            resetPointer();
            return;
        }

        if (ImGui.isMouseClicked(0)) {
            pointerDown = true;
            pointerDownScreen = mouseScreen;
            return;
        }
        if (!pointerDown || !ImGui.isMouseReleased(0)) {
            return;
        }

        pointerDown = false;
        double dragPx = pointerDownScreen != null ? pointerDownScreen.distance(mouseScreen) : 0.0;
        if (dragPx >= DRAG_THRESHOLD_PX) {
            return;
        }

        Vec2d world = canvas.screenToWorld(mouseScreen);
        String hit = EarthworkRegionOverlayController.hitTestAdopted(overlayEntries, world.x, world.y);
        onRegionSelected.accept(hit != null ? hit : "");
    }
}
