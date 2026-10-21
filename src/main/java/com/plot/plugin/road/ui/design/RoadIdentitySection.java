package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.ui.RoadIdentityEditor;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Road identity and metadata. */
public final class RoadIdentitySection {
    private final RoadIdentityEditor identityEditor = new RoadIdentityEditor();

    public void render(RoadDesignSectionContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.identity"))) {
            return;
        }
        identityEditor.render(
            ctx.network, ctx.road, ctx.ui.networkManager(), ctx.ui.networkManager()::pushHistory);
        int segmentCount = ctx.road.getSegmentIds().size();
        double length = RoadEdgeListHelper.computeRoadLength(ctx.network, ctx.road);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.road_scope_summary", segmentCount, length));
        if (ctx.chainageDisplay() != null) {
            renderChainageDisplayToggle(ctx);
        }
    }

    private void renderChainageDisplayToggle(RoadDesignSectionContext ctx) {
        boolean fromEnd = ctx.chainageDisplayFromEnd();
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.chainage_display_from_end"), fromEnd)) {
            ctx.setChainageDisplayFromEnd(!fromEnd);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.chainage_display_mode"));
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.chainage_display_format_hint"));
    }
}
