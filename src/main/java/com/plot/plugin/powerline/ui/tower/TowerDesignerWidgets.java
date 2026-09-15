package com.plot.plugin.powerline.ui.tower;

import com.plot.ui.dialog.DialogLayoutHelper;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.function.Consumer;

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

    /**
     * Slider edit with undo captured on activation, before the first value change is applied.
     */
    public static boolean formRowSliderTransaction(
            Runnable pushDraftSnapshot,
            String labelKey,
            String fieldId,
            float[] value,
            float min,
            float max,
            String format,
            Consumer<Float> onChanged) {
        return formRowSliderTransaction(
            pushDraftSnapshot,
            labelKey,
            fieldId,
            value,
            min,
            max,
            format,
            onChanged,
            null);
    }

    /**
     * Live {@code onChanged} while dragging; optional {@code onCommit} when the slider is released
     * (e.g. sync footprint / invalidate line preview once per edit).
     */
    public static boolean formRowSliderTransaction(
            Runnable pushDraftSnapshot,
            String labelKey,
            String fieldId,
            float[] value,
            float min,
            float max,
            String format,
            Consumer<Float> onChanged,
            Runnable onCommit) {
        DialogLayoutHelper.formRowLabel(PlotI18n.tr(labelKey));
        boolean changed = ImGui.sliderFloat(fieldId, value, min, max, format);
        if (ImGui.isItemActivated()) {
            pushDraftSnapshot.run();
        }
        if (changed) {
            onChanged.accept(value[0]);
        }
        if (onCommit != null && ImGui.isItemDeactivatedAfterEdit()) {
            onCommit.run();
        }
        return changed;
    }
}
