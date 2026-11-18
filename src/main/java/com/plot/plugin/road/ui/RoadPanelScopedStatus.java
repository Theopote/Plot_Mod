package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadStatus;
import com.plot.utils.PlotI18n;

/**
 * 应出现在对应工作区面板、而非标签上方顶栏的状态文案识别与清理。
 */
public final class RoadPanelScopedStatus {

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
        // 固定前缀 / 特征片段（含中英），避免把建造/认领等全局反馈误伤。
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
            "edited vertical profile");
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
