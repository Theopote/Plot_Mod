package com.plot.plugin.road.ui;

import com.plot.plugin.road.SlopeFormatUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * Compact batter angle presets (30° / 45° / 60° / custom) mapped to 1:n slope ratios.
 */
public final class SlopeAnglePresetControl {
    private static final float PRESET_30 = 30.0f;
    private static final float PRESET_45 = 45.0f;
    private static final float PRESET_60 = 60.0f;

    private SlopeAnglePresetControl() {
    }

    public record AngleSelection(float fillRatio, float cutRatio, boolean linked) {
    }

    /**
     * @return true when ratios changed
     */
    public static boolean render(
            String id,
            float fillRatio,
            float cutRatio,
            AngleSelectionConsumer onChanged) {
        ImGui.pushID(id);
        boolean changed = false;
        float fillAngle = SlopeFormatUtils.batterRatioToAngleDegrees(fillRatio);
        float cutAngle = SlopeFormatUtils.batterRatioToAngleDegrees(cutRatio);
        boolean linked = Math.abs(fillAngle - cutAngle) < 0.6f;

        ImGui.text(PlotI18n.tr("plugin.road.slope_angle_section"));
        ImBoolean linkedRef = new ImBoolean(linked);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.slope_angle_link_fill_cut") + "##link", linkedRef)) {
            linked = linkedRef.get();
            if (linked) {
                float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(fillAngle);
                onChanged.accept(ratio, ratio, true);
                changed = true;
            }
        }

        float activeAngle = linked ? fillAngle : fillAngle;
        if (renderPresetButton(id + "_30", PRESET_30, activeAngle, linked, fillRatio, cutRatio, onChanged)) {
            changed = true;
        }
        ImGui.sameLine();
        if (renderPresetButton(id + "_45", PRESET_45, activeAngle, linked, fillRatio, cutRatio, onChanged)) {
            changed = true;
        }
        ImGui.sameLine();
        if (renderPresetButton(id + "_60", PRESET_60, activeAngle, linked, fillRatio, cutRatio, onChanged)) {
            changed = true;
        }

        float[] customAngle = {linked ? fillAngle : fillAngle};
        ImGui.setNextItemWidth(120f);
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.slope_angle_custom") + "##custom",
                customAngle,
                SlopeFormatUtils.MIN_BATTER_ANGLE_DEGREES,
                SlopeFormatUtils.MAX_BATTER_ANGLE_DEGREES,
                "%.0f°")) {
            float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(customAngle[0]);
            if (linked) {
                onChanged.accept(ratio, ratio, true);
            } else {
                onChanged.accept(ratio, cutRatio, false);
            }
            changed = true;
        }

        float displayAngle = linked ? fillAngle : fillAngle;
        float displayRatio = linked ? fillRatio : fillRatio;
        ImGui.text(String.format(
            PlotI18n.tr("plugin.road.slope_angle_run_hint"),
            SlopeFormatUtils.formatBatterAngle(displayAngle),
            SlopeFormatUtils.formatHorizontalRunPerBlock(displayRatio)));

        if (!linked) {
            float[] cutCustom = {cutAngle};
            ImGui.setNextItemWidth(120f);
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.road.cut_slope_ratio_label") + "##cut_angle",
                    cutCustom,
                    SlopeFormatUtils.MIN_BATTER_ANGLE_DEGREES,
                    SlopeFormatUtils.MAX_BATTER_ANGLE_DEGREES,
                    "%.0f°")) {
                float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(cutCustom[0]);
                onChanged.accept(fillRatio, ratio, false);
                changed = true;
            }
        }

        ImGui.popID();
        return changed;
    }

    private static boolean renderPresetButton(
            String id,
            float presetAngle,
            float activeAngle,
            boolean linked,
            float fillRatio,
            float cutRatio,
            AngleSelectionConsumer onChanged) {
        boolean selected = Math.abs(activeAngle - presetAngle) < 0.6f;
        if (selected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0xFF4488CC);
        }
        boolean pressed = ImGui.button(presetAngle + "°##" + id);
        if (selected) {
            ImGui.popStyleColor();
        }
        if (pressed) {
            float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(presetAngle);
            if (linked) {
                onChanged.accept(ratio, ratio, true);
            } else {
                onChanged.accept(ratio, cutRatio, false);
            }
            return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface AngleSelectionConsumer {
        void accept(float fillRatio, float cutRatio, boolean linked);
    }
}
