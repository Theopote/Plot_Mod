package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadStatus;
import com.plot.utils.PlotI18n;

/**
 * 应出现在对应工作区面板、而非标签上方顶栏的状态文案识别与清理。
 */
public final class RoadPanelScopedStatus {

    /**
     * 浮动编辑器 / 路灯设计器等面板内反馈的 i18n key（精确匹配翻译结果）。
     * 含格式参数的 key 用 {@link #PANEL_SCOPED_FRAGMENTS} 兜底。
     */
    private static final String[] PANEL_SCOPED_KEYS = {
        "plugin.road.lamp.custom_saved",
        "plugin.road.lamp.custom_duplicate",
        "plugin.road.lamp.custom_deleted",
        "plugin.road.lamp.custom_error",
        "plugin.road.floating_editor.invalid_draft",
        "plugin.road.floating_editor.confirm_conflict",
        "plugin.road.floating_editor.reload_from_live_done",
        // road_missing 常在关窗后发出，仍走顶栏以免用户看不见
        "plugin.road.profile_editor_edge_without_road",
        "plugin.road.profile_editor_confirm_conflict",
        "plugin.road.uniform_flat_profile_editor_blocked",
        "plugin.road.edit.tools.coming_soon",
        // 建造页方案状态：展示在「建造」标签下的面板内，不压在标签上方
        "plugin.road.generate_preview_ready",
        "plugin.road.generate_preview_blocked"
    };

    private RoadPanelScopedStatus() {
    }

    /** 顶栏渲染前：面板作用域消息不画在标签上方。 */
    public static boolean shouldHideFromToolbar(RoadStatus status) {
        if (status == null || status.isEmpty()) {
            return false;
        }
        return isPanelScopedMessage(status.message());
    }

    /** 进入纵断面等面板时清掉顶栏残留的面板作用域文案。 */
    public static void clearFromToolbarIfPresent(RoadProjectStatus status) {
        if (status == null || status.isEmpty()) {
            return;
        }
        if (isPanelScopedMessage(status.get())) {
            status.clear();
        }
    }

    static boolean isPanelScopedMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        for (String key : PANEL_SCOPED_KEYS) {
            String expected = PlotI18n.tr(key);
            if (expected != null && !expected.isBlank() && expected.equals(message)) {
                return true;
            }
        }
        // 固定前缀 / 特征片段（含中英），覆盖带格式参数的文案与旧硬编码。
        return containsAny(
            message,
            "正在读取",
            "已取消地形读取",
            "无法读取地形",
            "地形纵断面",
            "Reading terrain",
            "terrain profile",
            "Cancelled terrain",
            "推荐 Y=",
            "Suggested Y=",
            "统一标高",
            "全网统一标高",
            "Uniform elevation",
            "uniform flat",
            "不可编辑单路纵断面",
            "cannot edit per-road",
            "编辑纵断面因路径变化",
            "edited vertical profile",
            "已保存自定义路灯预设",
            "已有同名自定义预设",
            "已删除自定义路灯预设",
            "无法读取或保存自定义路灯预设",
            "Custom lamp preset",
            "custom lamp preset",
            "请先修正无效",
            "路网已在别处变更",
            "已从当前路网重新加载",
            "invalid draft",
            "network changed elsewhere",
            "reloaded from the live",
            "vertical profile",
            "方案已生成，可以落地建造",
            "方案已生成，但存在校验问题",
            "Plan generated. You can build",
            "Plan generated, but validation",
            "条边成功",
            "edges succeeded",
            // 单元测试无语言包时 tr() 原样返回 key
            "plugin.road.generate_preview_ready",
            "plugin.road.generate_preview_blocked",
            "plugin.road.generate_partial_failure");
    }

    /** 建造工作区应展示的方案生成状态（含边统计等格式化后缀）。 */
    public static boolean isGeneratePanelMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return containsAny(
            message,
            "方案已生成，可以落地建造",
            "方案已生成，但存在校验问题",
            "Plan generated. You can build",
            "Plan generated, but validation",
            "条边成功",
            "edges succeeded",
            "plugin.road.generate_preview_ready",
            "plugin.road.generate_preview_blocked",
            "plugin.road.generate_partial_failure");
    }

    private static boolean containsAny(String message, String... fragments) {
        String lower = message.toLowerCase();
        for (String fragment : fragments) {
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            if (message.contains(fragment) || lower.contains(fragment.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 用 i18n key 精确清顶栏（无格式参数的短句）。 */
    public static void clearIfExactKey(RoadProjectStatus status, String messageKey) {
        if (status == null || messageKey == null) {
            return;
        }
        String expected = PlotI18n.tr(messageKey);
        if (expected != null && expected.equals(status.get())) {
            status.clear();
        }
    }
}
