package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 样式 Tab：塔型主题、下垂、材质。 */
public final class PowerLineStylePanel {
    private static final int STYLE_PRESET_COLUMNS = 4;

    private final PowerLineUiContext ctx;
    private final PowerLineStyleControls styleControls;
    private final PoleDesignerPanel poleDesignerPanel;

    public PowerLineStylePanel(
            PowerLineUiContext ctx,
            PoleDesignerPanel poleDesignerPanel) {
        this.ctx = ctx;
        this.styleControls = new PowerLineStyleControls(ctx);
        this.poleDesignerPanel = poleDesignerPanel;
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
        ImGui.text(PlotI18n.tr("plugin.powerline.style.section.tower"));
        renderStylePresetGrid(line, PowerLineStylePresetCatalog.decorativePresets());
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.section.tower_engineering"),
                ImGuiTreeNodeFlags.None)) {
            renderStylePresetGrid(line, PowerLineStylePresetCatalog.engineeringPresets());
        }
        renderStylePresetStatus(line);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.style.section.wire"));
        renderSagPresets(line);
        styleControls.renderMaterialControls(line);
        renderAdvancedStyle(line);
    }

    private void renderStylePresetGrid(PowerLineFootprint line, java.util.List<PowerLineStylePreset> presets) {
        PowerLineStylePreset active = PowerLineStylePresetCatalog.activePreset(line);
        float spacing = ImGui.getStyle().getItemSpacingX();

        for (int i = 0; i < presets.size(); i++) {
            if (i > 0 && i % STYLE_PRESET_COLUMNS != 0) {
                ImGui.sameLine(0f, spacing);
            }
            PowerLineStylePreset preset = presets.get(i);
            boolean selected = active != null && active.getId().equals(preset.getId());
            String label = PlotI18n.tr(preset.getLabelKey());
            if (PowerLineStyleCardRenderer.renderStyleCard(preset, label, selected)) {
                ctx.pushEditSnapshot();
                preset.apply(line);
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();
    }

    private void renderStylePresetStatus(PowerLineFootprint line) {
        PowerLineStylePreset active = PowerLineStylePresetCatalog.activePreset(line);
        if (active != null) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.style.selected_pack",
                    PlotI18n.tr(active.getLabelKey())));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.style.custom"));
        }
    }

    private void renderSagPresets(PowerLineFootprint line) {
        ImGui.text(PlotI18n.tr("plugin.powerline.style.sag"));
        PowerLineUiPresets.WireSag current = PowerLineUiPresets.detectSag(line);

        float spacing = ImGui.getStyle().getItemSpacingX();
        float totalWidth = PowerLineSagCardRenderer.CARD_WIDTH * PowerLineUiPresets.WireSag.values().length
            + spacing * (PowerLineUiPresets.WireSag.values().length - 1);
        float startX = ImGui.getCursorPosX();
        if (totalWidth < ImGui.getContentRegionAvail().x) {
            ImGui.setCursorPosX(startX + (ImGui.getContentRegionAvail().x - totalWidth) * 0.5f);
        }

        for (PowerLineUiPresets.WireSag sag : PowerLineUiPresets.WireSag.values()) {
            if (sag != PowerLineUiPresets.WireSag.values()[0]) {
                ImGui.sameLine(0f, spacing);
            }
            boolean selected = sag == current;
            String label = PlotI18n.tr("plugin.powerline.style.sag." + sag.name().toLowerCase());
            if (PowerLineSagCardRenderer.renderSagCard(sag, label, selected)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySag(line, sag);
                PowerLineStylePresetCatalog.clearStylePresetIfDrifted(line);
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();

        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.powerline.style.sag_advanced"), ImGuiTreeNodeFlags.None)) {
            float[] sagRatio = {(float) (line.getSagRatio() * 100f)};
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.powerline.sag_ratio", sagRatio[0]),
                    sagRatio,
                    0f,
                    (float) (PowerLineUiPresets.ADVANCED_SAG_MAX_RATIO * 100f),
                    "%.0f%%")) {
                PowerLineUiPresets.applyAdvancedSag(line, sagRatio[0] / 100f);
                PowerLineStylePresetCatalog.clearStylePresetIfDrifted(line);
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
            renderMaxSagDepthControls(line);
        }
    }

    private void renderMaxSagDepthControls(PowerLineFootprint line) {
        boolean unlimited = line.isMaxSagDepthUnlimited();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.max_sag_depth_unlimited"), unlimited)) {
            ctx.pushEditSnapshot();
            PowerLineUiPresets.applyMaxSagDepth(
                line,
                PowerLineUiPresets.displayMaxSagDepth(line),
                !unlimited);
            PowerLineStylePresetCatalog.clearStylePresetIfDrifted(line);
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
                PowerLineStylePresetCatalog.clearStylePresetIfDrifted(line);
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

    private void renderAdvancedStyle(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.powerline.style.advanced"), ImGuiTreeNodeFlags.None)) {
            return;
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.style.advanced_hint"));
        styleControls.renderPoleDesignControls(line, poleDesignerPanel, true);
        styleControls.renderTowerFamilyControls(line);
        styleControls.renderPoleHeightControls(line);
        styleControls.renderPoleRoleInspector(line);
    }
}
