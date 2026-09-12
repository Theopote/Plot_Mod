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

/** 样式 Tab：风格画廊 + Quick Customize + 高级设计器。 */
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
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            ImGui.separator();
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        ImGui.separator();
        PowerLineUiWidgets.renderLineSelector(ctx);
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.choose"));
        renderStyleGallery(line);

        PowerLineStylePreset base = PowerLineStyleEditor.basePreset(line);
        if (base != null) {
            ImGui.spacing();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.style.section.quick_customize"));
            quickTunePanel.render(line, base);
        } else {
            quickTunePanel.renderCustomFallback(line);
        }

        renderAdvancedStyle(line);
    }

    private void renderStyleGallery(PowerLineFootprint line) {
        for (StyleCategory category : PowerLineStylePresetCatalog.galleryCategories()) {
            ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
            if (ImGui.collapsingHeader(
                    PlotI18n.tr(category.sectionKey()),
                    ImGuiTreeNodeFlags.None)) {
                renderStylePresetGrid(line, PowerLineStylePresetCatalog.presetsByCategory(category));
            }
        }
    }

    private int computePresetColumns() {
        float cardW = PowerLineStyleCardRenderer.cardWidth();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float avail = ImGui.getContentRegionAvail().x;
        return Math.max(1, (int) ((avail + spacing) / (cardW + spacing)));
    }

    private void renderStylePresetGrid(PowerLineFootprint line, java.util.List<PowerLineStylePreset> presets) {
        PowerLineStylePreset base = PowerLineStyleEditor.basePreset(line);
        int columns = computePresetColumns();
        float spacing = ImGui.getStyle().getItemSpacingX();

        for (int i = 0; i < presets.size(); i++) {
            if (i > 0 && i % columns != 0) {
                ImGui.sameLine(0f, spacing);
            }
            PowerLineStylePreset preset = presets.get(i);
            boolean selected = base != null && base.getId().equals(preset.getId());
            String label = PlotI18n.tr(preset.getLabelKey());
            if (PowerLineStyleCardRenderer.renderStyleCard(preset, label, selected, line)) {
                ctx.pushEditSnapshot();
                PowerLineStyleEditor.selectPreset(line, preset);
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();
    }

    private void renderAdvancedStyle(PowerLineFootprint line) {
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
        styleControls.renderEngineeringOverrides(line, poleDesignerPanel);
    }
}
