package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.TunnelShape;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * Minimal 2D tunnel cross-section preview for {@link TunnelStyleEditor}.
 */
public final class TunnelCrossSectionPreviewRenderer {
    private static final int FILL_COLOR = 0x334488CC;
    private static final int AIR_COLOR = 0x22FFFFFF;
    private static final int OUTLINE_COLOR = 0xFFCCCCCC;

    private TunnelCrossSectionPreviewRenderer() {
    }

    public static void render(TunnelShape shape, int clearHeight, int sideClearance, int liningThickness) {
        float width = Math.max(80f, ImGui.getContentRegionAvailX());
        float height = 72f;
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();

        float centerX = origin.x + width * 0.5f;
        float floorY = origin.y + height - 8f;
        float halfClear = Math.max(2, sideClearance) * 4f;
        float clearTop = floorY - Math.max(3, clearHeight) * 4f;
        float outerHalf = halfClear + Math.max(1, liningThickness) * 3f;

        switch (shape == null ? TunnelShape.RECTANGULAR : shape) {
            case RECTANGULAR -> drawRect(drawList, centerX, floorY, halfClear, clearTop, outerHalf);
            case ARCH -> drawArch(drawList, centerX, floorY, halfClear, clearTop, outerHalf);
            case HORSESHOE -> drawHorseshoe(drawList, centerX, floorY, halfClear, clearTop, outerHalf);
        }

        ImGui.dummy(width, height);
    }

    private static void drawRect(
            ImDrawList drawList,
            float centerX,
            float floorY,
            float halfClear,
            float clearTop,
            float outerHalf) {
        drawList.addRectFilled(
            centerX - halfClear, clearTop, centerX + halfClear, floorY, AIR_COLOR, 2f);
        drawList.addRect(
            centerX - outerHalf, clearTop - 4f, centerX + outerHalf, floorY + 2f, OUTLINE_COLOR, 2f);
        drawList.addRectFilled(
            centerX - outerHalf, clearTop - 4f, centerX - halfClear, floorY + 2f, FILL_COLOR, 1f);
        drawList.addRectFilled(
            centerX + halfClear, clearTop - 4f, centerX + outerHalf, floorY + 2f, FILL_COLOR, 1f);
    }

    private static void drawArch(
            ImDrawList drawList,
            float centerX,
            float floorY,
            float halfClear,
            float clearTop,
            float outerHalf) {
        drawList.addRectFilled(
            centerX - halfClear, clearTop + 8f, centerX + halfClear, floorY, AIR_COLOR, 2f);
        drawList.addCircleFilled(
            centerX, clearTop + 8f, halfClear, AIR_COLOR, 24);
        drawList.addCircle(
            centerX, clearTop + 8f, outerHalf, OUTLINE_COLOR, 24, 2f);
        drawList.addLine(centerX - outerHalf, clearTop + 8f, centerX - outerHalf, floorY, OUTLINE_COLOR, 2f);
        drawList.addLine(centerX + outerHalf, clearTop + 8f, centerX + outerHalf, floorY, OUTLINE_COLOR, 2f);
        drawList.addLine(centerX - outerHalf, floorY, centerX + outerHalf, floorY, OUTLINE_COLOR, 2f);
    }

    private static void drawHorseshoe(
            ImDrawList drawList,
            float centerX,
            float floorY,
            float halfClear,
            float clearTop,
            float outerHalf) {
        drawRect(drawList, centerX, floorY, halfClear * 0.85f, clearTop, outerHalf);
        drawList.addCircleFilled(
            centerX, clearTop + 10f, halfClear * 0.85f, AIR_COLOR, 24);
    }
}
