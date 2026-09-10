package com.plot.plugin.powerline.ui;

import com.plot.plugin.ui.PluginUiColors;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** PowerLine 状态行图标：用 ImDrawList 几何图元，不依赖字体 glyph。 */
public final class PowerLineStatusIcon {
    public enum Kind {
        OK,
        WARNING,
        ERROR,
        BULLET
    }

    private static final float ICON_SLOT_RATIO = 0.85f;
    private static final float ICON_SIZE_RATIO = 0.42f;
    private static final float ICON_TEXT_GAP = 6f;

    private PowerLineStatusIcon() {
    }

    public static void renderOkLine(String text) {
        renderLine(Kind.OK, PluginUiColors.STATUS_OK, text);
    }

    public static void renderWarningLine(String text) {
        renderLine(Kind.WARNING, PluginUiColors.WARNING, text);
    }

    public static void renderErrorLine(String text) {
        renderLine(Kind.ERROR, PluginUiColors.ERROR_SOFT, text);
    }

    public static void renderBulletLine(String text) {
        renderLine(Kind.BULLET, PluginUiColors.HINT_GRAY, text);
    }

    public static void renderLine(Kind kind, int color, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        float lineHeight = ImGui.getTextLineHeight();
        float iconSlot = lineHeight * ICON_SLOT_RATIO;
        float iconSize = lineHeight * ICON_SIZE_RATIO;
        ImVec2 screenPos = ImGui.getCursorScreenPos();
        draw(
            ImGui.getWindowDrawList(),
            kind,
            screenPos.x + iconSlot * 0.5f,
            screenPos.y + lineHeight * 0.5f,
            iconSize,
            color);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + iconSlot + ICON_TEXT_GAP);
        ImGui.pushTextWrapPos(PowerLineUiWidgets.wrapPos());
        ImGui.textColored(color, text);
        ImGui.popTextWrapPos();
    }

    public static void renderIndentedHint(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        float indent = ImGui.getTextLineHeight() * ICON_SLOT_RATIO + ICON_TEXT_GAP + ImGui.getStyle().getIndentSpacing();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, text);
    }

    static void draw(ImDrawList drawList, Kind kind, float centerX, float centerY, float size, int color) {
        if (drawList == null) {
            return;
        }
        switch (kind) {
            case OK -> drawList.addCircleFilled(centerX, centerY, size * 0.5f, color, 12);
            case WARNING -> {
                float half = size * 0.55f;
                drawList.addTriangleFilled(
                    centerX,
                    centerY - half,
                    centerX - half,
                    centerY + half * 0.85f,
                    centerX + half,
                    centerY + half * 0.85f,
                    color);
            }
            case ERROR -> drawList.addCircleFilled(centerX, centerY, size * 0.5f, color, 12);
            case BULLET -> drawList.addCircleFilled(centerX, centerY, size * 0.28f, color, 10);
            default -> { }
        }
    }
}
