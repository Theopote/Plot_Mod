package com.plot.plugin.earthwork.ui;

import imgui.ImGui;

/** 土方 ImGui 侧栏布局常量与宽度辅助。 */
public final class EarthworkUiLayout {
    /** 低于此宽度时侧栏控件改为堆叠布局（与道路浮动编辑器一致）。 */
    public static final float NARROW_PANEL_WIDTH = 420f;

    private EarthworkUiLayout() {
    }

    public static boolean isNarrowPanel() {
        return ImGui.getContentRegionAvailX() < NARROW_PANEL_WIDTH;
    }

    /** 区域行内容列宽度（窄面板全宽；宽面板在缩略图 sameLine 之后调用）。 */
    public static float regionContentWidth() {
        return Math.max(1f, ImGui.getContentRegionAvailX());
    }

    public static float regionRowAvailWidth() {
        return Math.max(1f, ImGui.getContentRegionAvailX());
    }

    public static void pushTextWrapForContent(float contentWidth) {
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + contentWidth);
    }

    public static void popTextWrap() {
        ImGui.popTextWrapPos();
    }
}
