package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.RoadEditDiagnosticsBanner;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiTreeNodeFlags;

/** Road diagnostics detail; opens by default when issues exist. */
public final class RoadDiagnosticsSection {
    private boolean forceOpen;

    public void requestOpen() {
        forceOpen = true;
    }

    public void render(RoadDesignSectionContext ctx) {
        int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx.ui, ctx.network, ctx.road);
        boolean openByDefault = forceOpen || issueCount > 0;
        int flags = openByDefault ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (forceOpen) {
            forceOpen = false;
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.diagnostics"), flags)) {
            return;
        }
        if (issueCount > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.edit.diagnostics_pinned", issueCount));
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        float width = ImGui.getContentRegionAvail().x;
        float startY = ImGui.getCursorPosY();
        ImGui.indent(6f);
        RoadEditDiagnosticsBanner.renderDetailedIssues(ctx.ui, ctx.network, ctx.road);
        ImGui.unindent(6f);
        float endY = ImGui.getCursorPosY();
        float height = Math.max(ImGui.getFrameHeight(), endY - startY);
        ImGui.getWindowDrawList().addRect(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + height,
            PluginUiColors.WARNING & 0x00FFFFFF | 0x80000000);
    }
}
