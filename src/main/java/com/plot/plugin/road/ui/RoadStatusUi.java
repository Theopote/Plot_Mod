package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadStatus;
import com.plot.plugin.ui.PluginUiColors;
import imgui.ImGui;

/**
 * 道路状态栏在 ImGui 中的颜色与图标映射。
 */
public final class RoadStatusUi {

    private RoadStatusUi() {
    }

    public static void render(RoadStatus status) {
        if (status == null || status.isEmpty()) {
            return;
        }
        ImGui.textColored(colorFor(status.severity()), formatLabel(status));
    }

    public static int colorFor(RoadStatus.Severity severity) {
        if (severity == null) {
            return PluginUiColors.STATUS_INFO;
        }
        return switch (severity) {
            case SUCCESS -> PluginUiColors.STATUS_OK;
            case WARNING -> PluginUiColors.WARNING;
            case ERROR -> PluginUiColors.ERROR;
            case PROGRESS -> PluginUiColors.STATUS_INFO;
            case INFO -> PluginUiColors.HINT_GRAY;
        };
    }

    public static String formatLabel(RoadStatus status) {
        return prefixFor(status.severity()) + status.message();
    }

    public static String prefixFor(RoadStatus.Severity severity) {
        if (severity == null) {
            return "";
        }
        return switch (severity) {
            case SUCCESS -> "\u2713 ";
            case WARNING -> "\u26a0 ";
            case ERROR -> "\u2717 ";
            case PROGRESS -> "\u2026 ";
            case INFO -> "\u2139 ";
        };
    }
}
