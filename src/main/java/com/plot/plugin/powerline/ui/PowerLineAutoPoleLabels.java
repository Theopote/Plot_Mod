package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.utils.PlotI18n;

/** 将内部布局约束翻译为玩家可见文案。 */
public final class PowerLineAutoPoleLabels {
    private static final String LEGACY_TERRAIN = "terrain avoidance";
    private static final String REASON_TERRAIN = "plugin.powerline.route.auto_pole.reason.terrain";
    private static final String REASON_SPAN = "plugin.powerline.route.auto_pole.reason.span";
    private static final String REASON_GENERIC = "plugin.powerline.route.auto_pole.reason.generic";

    private PowerLineAutoPoleLabels() {
    }

    public static String friendlyReason(PoleLayoutConstraint constraint) {
        if (constraint == null) {
            return PlotI18n.tr(REASON_GENERIC);
        }
        String reason = constraint.getReason();
        if (reason == null || reason.isBlank()) {
            return PlotI18n.tr(REASON_GENERIC);
        }
        if (reason.startsWith("plugin.")) {
            if (REASON_TERRAIN.equals(reason)) {
                return PlotI18n.tr(REASON_TERRAIN);
            }
            if ("plugin.powerline.engineering.reason.insert_pole".equals(reason)) {
                return PlotI18n.tr(REASON_SPAN);
            }
            return PlotI18n.tr(reason, constraint.getRequiredStationing());
        }
        if (LEGACY_TERRAIN.equalsIgnoreCase(reason)) {
            return PlotI18n.tr(REASON_TERRAIN);
        }
        return PlotI18n.tr(REASON_GENERIC);
    }
}
