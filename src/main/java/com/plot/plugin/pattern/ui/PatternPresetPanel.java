package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.pattern.model.PatternPresetLibrary;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;

/** 图案预设面板：仅展示与当前图案来源匹配的预设。 */
public final class PatternPresetPanel {
    private final PatternUiContext ctx;
    private final ImString newPresetName = new ImString(64);
    private int selectedPresetIndex = -1;
    private boolean showUserPresets = false;
    private boolean savePresetPopupPending = false;
    private PatternSource lastRenderedSource;

    public PatternPresetPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderSection(PatternSource source) {
        PatternPresetLibrary library = ctx.presetLibrary();
        if (library == null) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, PlotI18n.tr("plugin.pattern.preset_library_unavailable"));
            return;
        }

        PatternSource resolvedSource = source != null ? source : PatternSource.PROCEDURAL;
        if (lastRenderedSource != resolvedSource) {
            selectedPresetIndex = -1;
            lastRenderedSource = resolvedSource;
        }

        PatternUiWidgets.textColoredWrapped(
            PluginUiColors.HINT_GRAY,
            resolvedSource == PatternSource.IMAGE
                ? PlotI18n.tr("plugin.pattern.preset_section_image_hint")
                : PlotI18n.tr("plugin.pattern.preset_section_procedural_hint"));

        ImBoolean showUser = new ImBoolean(showUserPresets);
        if (PatternUiWidgets.renderPresetScopeRadio(showUser)) {
            showUserPresets = showUser.get();
            selectedPresetIndex = -1;
        }

        ImGui.spacing();

        if (showUserPresets) {
            ImGui.text(PlotI18n.tr("plugin.pattern.preset_user_list_title"));
        }

        List<PatternPreset> presets = showUserPresets
            ? library.getUserPresets(resolvedSource)
            : library.getBuiltInPresets(resolvedSource);
        if (presets.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, emptyPresetsMessage(resolvedSource, showUserPresets));
            if (!showUserPresets) {
                return;
            }
            ImGui.spacing();
        } else {
            ImGui.beginChild("preset_list", 0, 150, true);
            for (int i = 0; i < presets.size(); i++) {
                PatternPreset preset = presets.get(i);
                ImGui.pushID(i);

                boolean selected = i == selectedPresetIndex;
                if (ImGui.selectable(preset.getDisplayName() + "##preset", selected)) {
                    selectedPresetIndex = i;
                }

                if (ImGui.isItemHovered()) {
                    String tooltip = preset.getDisplayDescription();
                    if (!tooltip.isBlank()) {
                        ImGui.setTooltip(tooltip);
                    }
                }

                ImGui.popID();
            }
            ImGui.endChild();

            ImGui.spacing();
        }

        if (!showUserPresets) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.preset_builtin_hint"));
        } else if (ImGui.button(PlotI18n.tr("plugin.pattern.save_current_as_preset"), 0, 0)) {
            savePresetPopupPending = true;
        }

        ImGui.spacing();

        if (selectedPresetIndex >= 0 && selectedPresetIndex < presets.size()) {
            PatternPreset selectedPreset = presets.get(selectedPresetIndex);

            if (ImGui.button(PlotI18n.tr("plugin.pattern.apply_preset"), 0, 0)) {
                applySelectedPreset(selectedPreset);
            }

            ImGui.sameLine();

            if (showUserPresets && !selectedPreset.isBuiltIn()) {
                if (ImGui.button(PlotI18n.tr("plugin.pattern.delete_preset"), 0, 0)) {
                    library.deletePreset(selectedPreset.getId());
                    selectedPresetIndex = -1;
                    ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_deleted", selectedPreset.getDisplayName()));
                }
            }
        }
    }

    private static String emptyPresetsMessage(PatternSource source, boolean userPresets) {
        if (userPresets) {
            return source == PatternSource.IMAGE
                ? PlotI18n.tr("plugin.pattern.no_user_image_presets")
                : PlotI18n.tr("plugin.pattern.no_user_procedural_presets");
        }
        return PlotI18n.tr("plugin.pattern.no_builtin_presets");
    }

    public void renderSavePresetPopup() {
        if (savePresetPopupPending) {
            ImGui.openPopup("##pattern_save_preset");
            savePresetPopupPending = false;
        }

        if (ImGui.beginPopupModal("##pattern_save_preset", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr("plugin.pattern.preset_name_label"));
            ImGui.setNextItemWidth(240f);
            ImGui.inputText("##pattern_new_preset_name", newPresetName);

            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.preset_save"), 96, 0)) {
                if (saveCurrentAsPreset()) {
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 96, 0)) {
                newPresetName.set("");
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private boolean saveCurrentAsPreset() {
        String name = newPresetName.get().trim();
        if (name.isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.preset_name_empty"));
            return false;
        }

        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        if (footprint == null) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.pattern.no_footprint_selected"));
            return false;
        }

        ctx.actions().saveAsPreset(footprint, name);
        newPresetName.set("");
        return true;
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
        lastRenderedSource = null;
    }
}
