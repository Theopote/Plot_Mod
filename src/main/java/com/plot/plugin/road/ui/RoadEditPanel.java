package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 编辑 Tab：道路类型、横断面、材质与高级道路设计（围绕当前选择）。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadEditWorkspace editWorkspace;
    private final RoadFloatingEditorHost floatingEditorHost;
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private final RoadIntersectionDetailPanel intersectionDetailPanel;
    private final RoadJunctionPanel junctionPanel;
    private String lastRenderedRoadScopeKey = "";

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadIntersectionDetailPanel intersectionDetailPanel,
            RoadJunctionPanel junctionPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.ctx = ctx;
        this.floatingEditorHost = floatingEditorHost;
        this.editWorkspace = new RoadEditWorkspace(
            ctx, defaultParamsPanel, floatingEditorHost);
        this.intersectionDetailPanel = intersectionDetailPanel;
        this.junctionPanel = junctionPanel;
    }

    public RoadFloatingEditorHost floatingEditorHost() {
        return floatingEditorHost;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }

        ctx.networkManager().ensureSelectionValid();
        renderFocusedIntersection(network);

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        String scopeKey = switch (mode) {
            case NONE -> "none";
            case SINGLE -> {
                var road = ctx.networkManager().getPrimarySelectedRoad();
                yield road != null ? "single:" + road.getId() : "single:none";
            }
            case MULTI -> "multi:" + String.join(",", ctx.networkManager().getSelectedRoadIds());
        };
        if (!lastRenderedRoadScopeKey.isEmpty() && !lastRenderedRoadScopeKey.equals(scopeKey)) {
            Set<String> selectedRoadIds = new LinkedHashSet<>(ctx.networkManager().getSelectedRoadIds());
            finishPendingEditsForScope(selectedRoadIds);
        }
        lastRenderedRoadScopeKey = scopeKey;

        switch (mode) {
            case NONE -> editWorkspace.renderNoSelectionDefaults();
            case SINGLE -> {
                var road = ctx.networkManager().getPrimarySelectedRoad();
                if (road != null) {
                    editWorkspace.renderSingle(network, road);
                }
            }
            case MULTI -> editWorkspace.renderMulti();
        }
    }

    public void finishPendingEdits() {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ctx);
        floatingEditorHost.finishOrPromptAll(ctx);
    }

    private void finishPendingEditsForScope(Set<String> selectedRoadIds) {
        segmentEditor.finishOrCancelSlopeOverrideEdit(ctx);
        floatingEditorHost.finishOrPromptOutsideScope(ctx, selectedRoadIds);
    }

    private void renderFocusedIntersection(RoadNetwork network) {
        if (intersectionDetailPanel == null
                || !RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager())) {
            return;
        }
        intersectionDetailPanel.renderFocusedSelection(
            network, RoadGradeSeparationControls.Layout.BLOCK);
        RoadIntersectionGeometrySection.render(ctx, network, junctionPanel);
        ImGui.separator();
        ImGui.spacing();
    }
}
