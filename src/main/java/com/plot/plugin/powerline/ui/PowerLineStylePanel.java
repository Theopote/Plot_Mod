package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
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

    public PowerLineStylePanel(
            PowerLineUiContext ctx,
            PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.styleControls = new PowerLineStyleControls(ctx);
        this.poleDesignerPanel = poleDesignerPanel;
        this.quickTunePanel = new PowerLineStyleQuickTunePanel(ctx, poleDesignerPanel);
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        PowerLineUiWidgets.renderLineSelector(ctx);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.style.section.choose"));
        renderStylePresetGrid(line, PowerLineStylePresetCatalog.decorativePresets());
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.section.industrial_mega"),
                ImGuiTreeNodeFlags.None)) {
            renderStylePresetGrid(line, PowerLineStylePresetCatalog.industrialMegaPresets());
        }
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.section.tower_engineering"),
                ImGuiTreeNodeFlags.None)) {
            renderStylePresetGrid(line, PowerLineStylePresetCatalog.engineeringPresets());
        }

        PowerLineStylePreset base = PowerLineStyleEditor.basePreset(line);
        if (base != null) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.powerline.style.section.quick_customize"));
            quickTunePanel.render(line, base);
        } else {
            quickTunePanel.renderCustomFallback(line);
        }

        renderAdvancedStyle(line);
    }

    private int computePresetColumns() {
        float cardW = PowerLineStyleCardRenderer.cardWidth();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float avail = ImGui.getContentRegionAvail().x;
        return Math.max(2, (int) ((avail + spacing) / (cardW + spacing)));
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
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.advanced_hint"));
        styleControls.renderPoleDesignControls(line, poleDesignerPanel, true);
        styleControls.renderTowerFamilyControls(line);
        styleControls.renderPoleHeightControls(line);
        styleControls.renderPoleRoleInspector(line);
        renderAdvancedSag(line);
    }

    private void renderAdvancedSag(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.style.sag_advanced"));
        float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                sagRatio,
                0f,
                (float) (PowerLineUiPresets.ADVANCED_SAG_MAX_RATIO * 100f),
                "%.0f%%")) {
            PowerLineUiPresets.applyAdvancedSag(line, sagRatio[0] / 100f);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
        }
        if (ImGui.isItemActivated()) {
            ctx.pushEditSnapshot();
        }
        renderMaxSagDepthControls(line);
    }

    private void renderMaxSagDepthControls(PowerLineFootprint line) {
        boolean unlimited = line.isMaxSagDepthUnlimited();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.max_sag_depth_unlimited"), unlimited)) {
            ctx.pushEditSnapshot();
            PowerLineUiPresets.applyMaxSagDepth(
                line,
                PowerLineUiPresets.displayMaxSagDepth(line),
                !unlimited);
            PowerLineStyleEditor.afterStyleEdit(line);
            ctx.invalidatePreview();
        }
        if (!line.isMaxSagDepthUnlimited()) {
            float[] maxDepth = {PowerLineUiPresets.displayMaxSagDepth(line)};
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.powerline.max_sag_depth", maxDepth[0]),
                    maxDepth,
                    1f,
                    PowerLineUiPresets.ADVANCED_MAX_SAG_DEPTH_MAX,
                    "%.0f")) {
                PowerLineUiPresets.applyMaxSagDepth(line, maxDepth[0], false);
                PowerLineStyleEditor.afterStyleEdit(line);
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.max_sag_depth_profile_hint"));
        }
    }
}
