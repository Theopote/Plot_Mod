package com.plot.utils;

import net.minecraft.text.Text;

/**
 * Plot 模组国际化工具，基于 Minecraft 语言文件（assets/plot/lang）。
 */
public final class PlotI18n {

    private PlotI18n() {
    }

    public static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    public static String toolLabel(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return tr("tool.plot.unknown");
        }
        String normalized = toolId.toLowerCase().trim();
        if ("freedraw".equals(normalized)) {
            return tr("tool.plot.freehand");
        }
        String key = "tool.plot." + normalized;
        String translated = tr(key);
        return translated.equals(key) ? toolId : translated;
    }

    public static String toolDescription(String toolId) {
        return tr(toolDescriptionKey(toolId));
    }

    public static String toolUsageHint(String toolId) {
        return tr(toolUsageHintKey(toolId));
    }

    private static String toolDescriptionKey(String toolId) {
        return "tool.plot." + normalizeToolId(toolId) + ".desc";
    }

    private static String toolUsageHintKey(String toolId) {
        return "tool.plot." + normalizeToolId(toolId) + ".hint";
    }

    private static String normalizeToolId(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return "unknown";
        }
        return toolId.toLowerCase().trim();
    }
}
