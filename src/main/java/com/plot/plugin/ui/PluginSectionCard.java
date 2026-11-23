package com.plot.plugin.ui;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数分区卡片：主题派生表面 + 左侧色条 + 可折叠；高度按内容测量，不填满剩余空间。
 */
public final class PluginSectionCard {
    private static final float HEADER_PAD_Y = 4f;
    private static final float ACCENT_WIDTH = 3f;
    private static final float BODY_PAD_X = 8f;
    private static final float BODY_PAD_Y = 6f;
    private static final float COLLAPSED_FALLBACK = 28f;
    private static final float OPEN_FALLBACK = 48f;

    private static final Map<String, Float> MEASURED_HEIGHT = new HashMap<>();
    private static final Map<String, Boolean> OPEN_STATE = new HashMap<>();

    public enum SectionTone {
        NEUTRAL,
        LEVEL_0,
        LEVEL_1,
        LEVEL_2
    }

    private PluginSectionCard() {
    }

    /**
     * @param id            稳定唯一 id（建议含 {@code ##}）
     * @param title         标题
     * @param summaryOrNull 折叠时标题右侧摘要，可为 null
     * @param tone          色条语气
     * @param defaultOpen   首次出现时是否展开
     * @param content       展开时渲染的控件
     * @return 本帧卡片是否展开
     */
    public static boolean render(
            String id,
            String title,
            String summaryOrNull,
            SectionTone tone,
            boolean defaultOpen,
            Runnable content) {
        return renderResult(id, title, summaryOrNull, tone, defaultOpen, content).open();
    }

    /**
     * 同 {@link #render}，额外返回本帧是否悬停/激活（用于预览联动）。
     */
    public static Result renderResult(
            String id,
            String title,
            String summaryOrNull,
            SectionTone tone,
            boolean defaultOpen,
            Runnable content) {
        boolean open = OPEN_STATE.computeIfAbsent(id, ignored -> defaultOpen);
        PluginSectionColors colors = PluginSectionColors.current();
        int accent = colors.accentFor(tone);
        float measured = MEASURED_HEIGHT.getOrDefault(id, open ? OPEN_FALLBACK : COLLAPSED_FALLBACK);
        float headerH = ImGui.getFontSize() + HEADER_PAD_Y * 2f + ImGui.getStyle().getFramePaddingY();
        float childH = open ? Math.max(measured, headerH + 8f) : headerH;
        boolean hoveredOrActive = false;
        boolean headerClicked = false;

        int pushedColors = 0;
        int pushedVars = 0;
        try {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, colors.surface);
            pushedColors++;
            ImGui.pushStyleColor(ImGuiCol.Border, colors.border);
            pushedColors++;
            ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1f);
            pushedVars++;
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, BODY_PAD_X, BODY_PAD_Y);
            pushedVars++;

            boolean childOpen = ImGui.beginChild(
                id,
                0f,
                childH,
                true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
            try {
                if (!childOpen) {
                    return new Result(open, false);
                }
                float contentStartY = ImGui.getCursorScreenPosY();
                drawAccentBar(accent, childH);
                HeaderInteraction header = renderHeader(id, title, summaryOrNull, open);
                open = header.open();
                headerClicked = header.clicked();
                OPEN_STATE.put(id, open);
                if (open && content != null) {
                    ImGui.dummy(0f, 2f);
                    content.run();
                }
                // 下一帧用本帧内容高度；折叠时固定为标题条。
                float used = open
                    ? Math.max(headerH + 8f, ImGui.getCursorScreenPosY() - contentStartY + BODY_PAD_Y)
                    : headerH;
                MEASURED_HEIGHT.put(id, used);
            } finally {
                ImGui.endChild();
            }
            // endChild 后 IsItemHovered 指向整张卡片（dummy 间距之前读取）。
            hoveredOrActive = ImGui.isItemHovered() || ImGui.isItemActive() || headerClicked;
        } finally {
            if (pushedVars > 0) {
                ImGui.popStyleVar(pushedVars);
            }
            if (pushedColors > 0) {
                ImGui.popStyleColor(pushedColors);
            }
        }
        ImGui.dummy(0f, 4f);
        return new Result(open, hoveredOrActive);
    }

    public record Result(boolean open, boolean hoveredOrActive) {
    }

    private record HeaderInteraction(boolean open, boolean clicked) {
    }

    /** 测试/重置用：清空高度与折叠缓存。 */
    public static void clearCachesForTests() {
        MEASURED_HEIGHT.clear();
        OPEN_STATE.clear();
    }

    static Float measuredHeightForTests(String id) {
        return MEASURED_HEIGHT.get(id);
    }

    static Boolean openStateForTests(String id) {
        return OPEN_STATE.get(id);
    }

    private static HeaderInteraction renderHeader(String id, String title, String summaryOrNull, boolean open) {
        // ▶ 在多数字体的 em-box 里视觉重心偏低，单独绘制并做光学上移，与标题水平居中对齐。
        String chevron = open ? "▼" : "▶";
        ImGui.dummy(ACCENT_WIDTH, 0f);
        ImGui.sameLine(0f, 4f);
        boolean clicked = ImGui.selectable("##sec_hdr_" + id, false);
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX();
        float maxY = ImGui.getItemRectMaxY();
        var draw = ImGui.getWindowDrawList();
        float fontSize = ImGui.getFontSize();
        float textY = minY + (maxY - minY - fontSize) * 0.5f;
        float chevronY = textY + (open ? 0f : -fontSize * 0.12f);
        int textColor = ImGui.getColorU32(ImGuiCol.Text);
        draw.addText(minX, chevronY, textColor, chevron);
        float titleX = minX + ImGui.calcTextSize(chevron).x + 6f;
        draw.addText(titleX, textY, textColor, title);
        if (summaryOrNull != null && !summaryOrNull.isBlank()) {
            float summaryW = ImGui.calcTextSize(summaryOrNull).x;
            draw.addText(maxX - summaryW, textY, ImGui.getColorU32(ImGuiCol.TextDisabled), summaryOrNull);
        }
        if (clicked) {
            open = !open;
        }
        return new HeaderInteraction(open, clicked);
    }

    private static void drawAccentBar(int accent, float childH) {
        float x = ImGui.getWindowPosX();
        float y = ImGui.getWindowPosY();
        ImGui.getWindowDrawList().addRectFilled(x, y, x + ACCENT_WIDTH, y + childH, accent);
    }
}
