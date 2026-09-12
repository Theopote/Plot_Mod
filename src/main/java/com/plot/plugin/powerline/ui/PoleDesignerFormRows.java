package com.plot.plugin.powerline.ui;

import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 杆塔设计器表单行控件。 */
final class PoleDesignerFormRows {
    private PoleDesignerFormRows() {
    }

    static boolean sliderInt(String labelKey, String fieldId, int[] value, int min, int max) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr(labelKey));
        return ImGui.sliderInt(fieldId, value, min, max);
    }

    static boolean sliderFloat(
            String labelKey,
            String fieldId,
            float[] value,
            float min,
            float max,
            String format) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr(labelKey));
        return ImGui.sliderFloat(fieldId, value, min, max, format);
    }
}
