package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePackCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 样式 Tab：塔型主题、下垂、材质。 */
public final class PowerLineStylePanel {
    private static final int STYLE_PACK_COLUMNS = 4;

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
        renderStylePackGrid(line, PowerLineStylePackCatalog.decorativePacks());
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.style.section.tower_engineering"),
                ImGuiTreeNodeFlags.None)) {
            renderStylePackGrid(line, PowerLineStylePackCatalog.engineeringPacks());
        }
        renderStylePackStatus(line);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.style.section.wire"));
        renderSagPresets(line);
        styleControls.renderMaterialControls(line);
        styleControls.renderPoleDesignControls(line, poleDesignerPanel);
        renderAdvancedStyle(line);
    }

    private void renderStylePackGrid(PowerLineFootprint line, java.util.List<PowerLineStylePreset> packs) {
        PowerLineStylePreset active = PowerLineStylePackCatalog.detect(line);
        float spacing = ImGui.getStyle().getItemSpacingX();

        for (int i = 0; i < packs.size(); i++) {
            if (i > 0 && i % STYLE_PACK_COLUMNS != 0) {
                ImGui.sameLine(0f, spacing);
            }
            PowerLineStylePreset pack = packs.get(i);
            boolean selected = active != null && active.getId().equals(pack.getId());
            String label = PlotI18n.tr(pack.getLabelKey());
            if (PowerLineStyleCardRenderer.renderStyleCard(pack, label, selected)) {
                ctx.pushEditSnapshot();
                pack.apply(line);
                ctx.invalidatePreview();
            }
        }
        ImGui.newLine();
    }

    private void renderStylePackStatus(PowerLineFootprint line) {
        PowerLineStylePreset active = PowerLineStylePackCatalog.detect(line);
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
                ctx.invalidatePreview();
            }
            if (ImGui.isItemActivated()) {
                ctx.pushEditSnapshot();
            }
        }
    }

    private void renderAdvancedStyle(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.powerline.style.advanced"), ImGuiTreeNodeFlags.None)) {
            return;
        }
        styleControls.renderPoleHeightControls(line);
        styleControls.renderPoleRoleInspector(line);
    }
}
