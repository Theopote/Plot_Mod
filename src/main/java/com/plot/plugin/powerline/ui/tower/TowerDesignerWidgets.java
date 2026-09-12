package com.plot.plugin.powerline.ui.tower;

import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Shared ImGui form helpers for tower designer panels. */
public final class TowerDesignerWidgets {
    private TowerDesignerWidgets() {
    }

    public static boolean formRowSliderFloat(
            String labelKey,
            String fieldId,
            float[] value,
            float min,
            float max,
            String format) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr(labelKey));
        return ImGui.sliderFloat(fieldId, value, min, max, format);
    }

    public static void pushUndoIfActivated(Runnable pushDraftSnapshot) {
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }
    }
}
