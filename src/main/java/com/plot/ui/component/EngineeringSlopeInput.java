package com.plot.ui.component;

import com.plot.plugin.road.SlopeFormatUtils;
import com.plot.plugin.road.SlopeFormatUtils.DisplayFormat;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Map;

/**
 * 工程坡度参数输入控件：支持建筑行业标准格式（百分比 %、放坡比例 1:n）。
 */
public final class EngineeringSlopeInput {
    public enum ValueKind {
        /** 纵坡，内部存储为百分比 */
        GRADE,
        /** 边坡放坡比，内部存储为 1:n 中的 n（水平延伸 / 垂直 1 格） */
        BATTER
    }

    private static final Map<String, DisplayFormat> FORMAT_PREFS = new HashMap<>();
    private static final Map<String, ImString> TEXT_BUFFERS = new HashMap<>();

    private EngineeringSlopeInput() {
    }

    /**
     * @param id          ImGui 唯一 ID
     * @param label       左侧标签文案
     * @param value       长度 1 的数组，存放规范存储值（GRADE=%, BATTER=1:n 的 n）
     * @param kind        参数类型
     * @return 值是否发生变化
     */
    public static boolean render(String id, String label, float[] value, ValueKind kind) {
        ImGui.pushID(id);
        boolean changed = false;

        DisplayFormat format = FORMAT_PREFS.getOrDefault(id, DisplayFormat.PERCENT);
        if (kind == ValueKind.BATTER) {
            format = FORMAT_PREFS.getOrDefault(id, DisplayFormat.RATIO);
        }

        ImGui.text(label);
        ImGui.sameLine();

        String[] formatLabels = {
            PlotI18n.tr("plugin.road.slope_format_percent"),
            PlotI18n.tr("plugin.road.slope_format_ratio")
        };
        int[] formatIndex = {format == DisplayFormat.RATIO ? 1 : 0};
        ImGui.setNextItemWidth(72f);
        if (ImGui.combo("##format", formatIndex, formatLabels)) {
            format = formatIndex[0] == 1 ? DisplayFormat.RATIO : DisplayFormat.PERCENT;
            FORMAT_PREFS.put(id, format);
            syncTextBuffer(id, value[0], kind, format);
        }

        float sliderMin;
        float sliderMax;
        float sliderValue;
        String sliderFormat;
        if (kind == ValueKind.GRADE) {
            if (format == DisplayFormat.PERCENT) {
                sliderMin = SlopeFormatUtils.MIN_GRADE_PERCENT;
                sliderMax = SlopeFormatUtils.MAX_GRADE_PERCENT;
                sliderValue = value[0];
                sliderFormat = "%.1f%%";
            } else {
                sliderMin = SlopeFormatUtils.percentToHorizontalRatio(SlopeFormatUtils.MAX_GRADE_PERCENT);
                sliderMax = SlopeFormatUtils.percentToHorizontalRatio(0.5f);
                sliderValue = SlopeFormatUtils.percentToHorizontalRatio(value[0]);
                sliderFormat = "1:%.1f";
            }
        } else {
            if (format == DisplayFormat.RATIO) {
                sliderMin = SlopeFormatUtils.MIN_BATTER_RATIO;
                sliderMax = SlopeFormatUtils.MAX_BATTER_RATIO;
                sliderValue = value[0];
                sliderFormat = "1:%.1f";
            } else {
                sliderMin = SlopeFormatUtils.horizontalRatioToPercent(SlopeFormatUtils.MAX_BATTER_RATIO);
                sliderMax = SlopeFormatUtils.horizontalRatioToPercent(SlopeFormatUtils.MIN_BATTER_RATIO);
                sliderValue = SlopeFormatUtils.horizontalRatioToPercent(value[0]);
                sliderFormat = "%.1f%%";
            }
        }

        float[] slider = {sliderValue};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x - 88f);
        if (ImGui.sliderFloat("##slope_slider", slider, sliderMin, sliderMax, sliderFormat)) {
            float canonical = toCanonical(slider[0], kind, format);
            if (Math.abs(canonical - value[0]) > 1e-4f) {
                value[0] = canonical;
                syncTextBuffer(id, value[0], kind, format);
                changed = true;
            }
        }

        ImGui.sameLine();
        String equivalent = kind == ValueKind.GRADE
            ? (format == DisplayFormat.PERCENT
                ? SlopeFormatUtils.formatRatio(SlopeFormatUtils.percentToHorizontalRatio(value[0]))
                : SlopeFormatUtils.formatPercent(value[0]))
            : (format == DisplayFormat.RATIO
                ? SlopeFormatUtils.formatPercent(SlopeFormatUtils.horizontalRatioToPercent(value[0]))
                : SlopeFormatUtils.formatRatio(value[0]));
        ImGui.textDisabled("≈ " + equivalent);

        ImString buffer = TEXT_BUFFERS.computeIfAbsent(id, key -> new ImString(32));
        if (!ImGui.isAnyItemActive()) {
            syncTextBuffer(id, value[0], kind, format);
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        if (ImGui.inputText(
            "##slope_text",
            buffer,
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll
        )) {
            Float parsed = SlopeFormatUtils.parseInput(buffer.get(), format, kind == ValueKind.BATTER);
            if (parsed != null && Math.abs(parsed - value[0]) > 1e-4f) {
                value[0] = parsed;
                syncTextBuffer(id, value[0], kind, format);
                changed = true;
            }
        }
        if (ImGui.isItemDeactivatedAfterEdit() && !changed) {
            Float parsed = SlopeFormatUtils.parseInput(buffer.get(), format, kind == ValueKind.BATTER);
            if (parsed != null && Math.abs(parsed - value[0]) > 1e-4f) {
                value[0] = parsed;
                syncTextBuffer(id, value[0], kind, format);
                changed = true;
            }
        }

        ImGui.popID();
        return changed;
    }

    private static float toCanonical(float sliderValue, ValueKind kind, DisplayFormat format) {
        if (kind == ValueKind.GRADE) {
            if (format == DisplayFormat.PERCENT) {
                return SlopeFormatUtils.clampGradePercent(sliderValue);
            }
            return SlopeFormatUtils.clampGradePercent(
                SlopeFormatUtils.horizontalRatioToPercent(sliderValue)
            );
        }
        if (format == DisplayFormat.RATIO) {
            return SlopeFormatUtils.clampBatterRatio(sliderValue);
        }
        return SlopeFormatUtils.clampBatterRatio(
            SlopeFormatUtils.percentToHorizontalRatio(sliderValue)
        );
    }

    private static void syncTextBuffer(String id, float canonical, ValueKind kind, DisplayFormat format) {
        ImString buffer = TEXT_BUFFERS.computeIfAbsent(id, key -> new ImString(32));
        if (kind == ValueKind.GRADE) {
            buffer.set(format == DisplayFormat.PERCENT
                ? SlopeFormatUtils.formatPercent(canonical)
                : SlopeFormatUtils.formatRatio(SlopeFormatUtils.percentToHorizontalRatio(canonical)));
        } else {
            buffer.set(format == DisplayFormat.RATIO
                ? SlopeFormatUtils.formatRatio(canonical)
                : SlopeFormatUtils.formatPercent(SlopeFormatUtils.horizontalRatioToPercent(canonical)));
        }
    }
}
