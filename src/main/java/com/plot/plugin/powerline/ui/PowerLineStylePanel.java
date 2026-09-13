package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.StyleCategory;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 样式 Tab：当前样式 + 快速微调 + 预设画廊 + 高级设计器。 */
public final class PowerLineStylePanel {
    private final PowerLineUiContext ctx;
    private final PowerLineStyleControls styleControls;
    private final PoleDesignerPanel poleDesignerPanel;
    private final PowerLineStyleQuickTunePanel quickTunePanel;

    public PowerLineStylePanel(PowerLineUiContext ctx, PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.styleControls = new PowerLineStyleControls(ctx);
        this.poleDesignerPanel = poleDesignerPanel;
        this.quickTunePanel = new PowerLineStyleQuickTunePanel(ctx, poleDesignerPanel);
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint selectedLine = ctx.selection().primary(ctx.project());
        StyleEditTarget editTarget = ctx.state().getStyleEditTarget();

        ImGui.separator();
        renderStyleTargetSelector(editTarget, selectedLine);

        boolean editingStandalone = editTarget == StyleEditTarget.STANDALONE_TOWER;
        PowerLineFootprint styleTarget;
        if (editingStandalone) {
            styleTarget = ctx.state().getSingleTowerStyle();
        } else if (selectedLine != null) {
            styleTarget = selectedLine;
        } else {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.style.select_line_for_style"));
            return;
        }

        PowerLineStylePreset base = PowerLineStyleEditor.basePreset(styleTarget);
        if (base != null) {
            ImGui.separator();
            quickTunePanel.renderCurrentStyleSection(styleTarget, base, !editingStandalone);
            ImGui.spacing();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.quick_customize"));
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.style.quick_tune_hint"));
            if (editingStandalone) {
                quickTunePanel.renderStandaloneQuickTune(styleTarget, base);
            } else {
                quickTunePanel.renderLineQuickTune(styleTarget, base);
            }
        } else if (editingStandalone) {
            quickTunePanel.renderStandaloneCustomFallback(styleTarget);
        } else {
            quickTunePanel.renderCustomFallback(styleTarget);
        }

        renderChangePresetGallery(styleTarget, editingStandalone, base);

        if (!editingStandalone) {
            renderAdvancedStyle(styleTarget);
        }
    }

    private void renderStyleTargetSelector(StyleEditTarget editTarget, PowerLineFootprint selectedLine) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.edit_target"));
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.powerline.style.target.line"),
                editTarget == StyleEditTarget.LINE)) {
            ctx.state().setStyleEditTarget(StyleEditTarget.LINE);
        }
        ImGui.sameLine();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.powerline.style.target.standalone"),
                editTarget == StyleEditTarget.STANDALONE_TOWER)) {
            ctx.state().setStyleEditTarget(StyleEditTarget.STANDALONE_TOWER);
        }

        if (editTarget == StyleEditTarget.LINE) {
            ImGui.spacing();
            float width = ImGui.getContentRegionAvail().x;
            if (width > 0f) {
                ImGui.setNextItemWidth(width);
            }
            if (!PowerLineUiWidgets.renderLineSelector(ctx, false)) {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.powerline.route.current_line_empty"));
            } else if (selectedLine != null) {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.powerline.style.editing_line", selectedLine.getName()));
            }
        }
    }

    private void renderChangePresetGallery(
            PowerLineFootprint styleTarget,
            boolean standaloneStyle,
            PowerLineStylePreset base) {
        ImGui.separator();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.section.change_preset"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.gallery_hint"));
        renderStyleGallery(styleTarget, standaloneStyle, base);
    }

    private void renderStyleGallery(
            PowerLineFootprint styleTarget,
            boolean standaloneStyle,
            PowerLineStylePreset base) {
        StyleCategory activeCategory = base != null ? base.getCategory() : null;
        StyleCategory forceOpen = ctx.state().getStyleGalleryOpenCategory();
        for (StyleCategory category : PowerLineStylePresetCatalog.galleryCategories()) {
            if (category == forceOpen) {
                ImGui.setNextItemOpen(true, ImGuiCond.Always);
            } else if (forceOpen == null && category == activeCategory) {
                ImGui.setNextItemOpen(true, ImGuiCond.FirstUseEver);
            } else {
                ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
            }
            if (ImGui.collapsingHeader(
                    PlotI18n.tr(category.sectionKey()),
                    ImGuiTreeNodeFlags.None)) {
                renderStylePresetGrid(
                    styleTarget,
                    standaloneStyle,
                    PowerLineStylePresetCatalog.presetsByCategory(category));
            }
        }
        if (forceOpen != null) {
            ctx.state().clearStyleGalleryOpenCategory();
        }
    }

    private int computePresetColumns() {
        float cardW = PowerLineStyleCardRenderer.cardWidth();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float avail = ImGui.getContentRegionAvail().x;
        return Math.max(1, (int) ((avail + spacing) / (cardW + spacing)));
    }

    private void renderStylePresetGrid(
            PowerLineFootprint styleTarget,
            boolean standaloneStyle,
            java.util.List<PowerLineStylePreset> presets) {
        PowerLineStylePreset base = PowerLineStyleEditor.basePreset(styleTarget);
        int columns = computePresetColumns();
        float spacing = ImGui.getStyle().getItemSpacingX();

        for (int i = 0; i < presets.size(); i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine(0f, spacing);
            }
            PowerLineStylePreset preset = presets.get(i);
            boolean selected = base != null && base.getId().equals(preset.getId());
            String label = PlotI18n.tr(preset.getLabelKey());
            if (PowerLineStyleCardRenderer.renderStyleCard(preset, label, selected, styleTarget)) {
                if (!standaloneStyle) {
                    ctx.pushEditSnapshot();
                }
                PowerLineStyleEditor.selectPreset(styleTarget, preset);
                ctx.state().notifyStyleGalleryCategory(preset.getCategory());
                ctx.invalidatePreview();
                ctx.singleTowerPlacement().refreshGhostPreview();
            }
        }
        ImGui.newLine();
    }

    private void renderAdvancedStyle(PowerLineFootprint styleTarget) {
        ImGui.separator();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.edit_design"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.advanced_hint"));
        styleControls.renderEngineeringOverrides(styleTarget, poleDesignerPanel);
    }
}
