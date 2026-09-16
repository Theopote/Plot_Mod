package com.plot.plugin.pattern.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 图案认领 Tab。 */
public final class PatternAdoptPanel {
    private final PatternUiContext ctx;

    public PatternAdoptPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickPickSession() {
        ctx.handlePickSessionTick();
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.adopt_hint"));
        ImGui.spacing();

        if (ctx.pickSession().isActive()) {
            int count = ctx.pickSession().getAccumulatedCount();
            if (count > 0) {
                ImGui.text(String.format(PlotI18n.tr("plugin.pattern.regions_selected"), count));
            }
        } else {
            ctx.updateSelectedRegions();
        }

        if (!ctx.selectedRegions().isEmpty()) {
            ImGui.text(PlotI18n.tr(
                "plugin.pattern.regions_selected_detail",
                ctx.selectedRegions().size(),
                String.format("%.1f", ctx.computeSelectedRegionArea())));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.draw_region_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.pattern.select_all_closed"), 0, 0)) {
            ctx.selectAllClosedShapesOnCanvas();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.pattern.pick_region"), 0, 0)) {
            ctx.startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = ctx.selectedRegions().isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        String adoptLabel = ctx.selectedRegions().size() > 1
            ? PlotI18n.tr("plugin.pattern.adopt_region_batch", ctx.selectedRegions().size())
            : PlotI18n.tr("plugin.pattern.adopt_region");
        if (ImGui.button(adoptLabel, 0, 0)) {
            ctx.adoptSelectedRegions();
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }
}
