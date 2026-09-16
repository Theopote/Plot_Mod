package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.pattern.model.PatternPresetLibrary;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/** 图案预设管理面板。 */
public final class PatternPresetPanel {
    private final PatternUiContext ctx;
    private final ImString newPresetName = new ImString(64);
    private int selectedPresetIndex = -1;
    private boolean showUserPresets = false;

    public PatternPresetPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        PatternPresetLibrary library = ctx.presetLibrary();
        if (library == null) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, PlotI18n.tr("plugin.pattern.preset_library_unavailable"));
            return;
        }

        // 预设库选择开关
        if (ImGui.checkbox(PlotI18n.tr("plugin.pattern.show_user_presets"), showUserPresets)) {
            showUserPresets = !showUserPresets;
            selectedPresetIndex = -1;
        }

        ImGui.spacing();

        // 获取预设列表
        List<PatternPreset> presets = showUserPresets ? library.getUserPresets() : library.getBuiltInPresets();
        if (presets.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, 
                showUserPresets ? PlotI18n.tr("plugin.pattern.no_user_presets") : PlotI18n.tr("plugin.pattern.no_builtin_presets"));
            return;
        }

        // 预设列表
        ImGui.beginChild("preset_list", 0, 150, true);
        for (int i = 0; i < presets.size(); i++) {
            PatternPreset preset = presets.get(i);
            ImGui.pushID(i);
            
            boolean selected = (i == selectedPresetIndex);
            if (ImGui.selectable(preset.getName() + "##preset", selected)) {
                selectedPresetIndex = i;
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(preset.getDescription() != null ? preset.getDescription() : "");
            }
            
            ImGui.popID();
        }
        ImGui.endChild();

        ImGui.spacing();

        // 新建预设输入框
        if (!showUserPresets) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.preset_builtin_hint"));
        } else {
            ImGui.text(PlotI18n.tr("plugin.pattern.create_preset"));
            ImGui.sameLine();
            if (ImGui.inputText("##new_preset_name", newPresetName)) {
                // 输入框状态更新
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.save_as_preset"), 0, 0)) {
                saveCurrentAsPreset();
            }
        }

        ImGui.spacing();

        // 应用预设按钮
        if (selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
            PatternPreset selectedPreset = presets.get(selectedPresetIndex);
            
            if (ImGui.button(PlotI18n.tr("plugin.pattern.apply_preset"), 0, 0)) {
                applySelectedPreset(selectedPreset);
            }
            
            ImGui.sameLine();
            
            // 删除用户预设
            if (showUserPresets && !selectedPreset.isBuiltIn()) {
                if (ImGui.button(PlotI18n.tr("plugin.pattern.delete_preset"), 0, 0)) {
                    library.deletePreset(selectedPreset.getId());
                    selectedPresetIndex = -1;
                    ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_deleted", selectedPreset.getName()));
                }
            }
        }
    }

    private void saveCurrentAsPreset() {
        String name = newPresetName.get().trim();
        if (name.isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_name_empty"));
            return;
        }

        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        if (footprint == null) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.no_footprint_selected"));
            return;
        }

        ctx.actions().saveAsPreset(footprint, name);
        newPresetName.set("");
    }

    private void applySelectedPreset(PatternPreset preset) {
        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        if (footprint == null) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.no_footprint_selected"));
            return;
        }

        ctx.actions().loadPreset(preset, footprint);
    }

    public void resetSelection() {
        selectedPresetIndex = -1;
    }
}