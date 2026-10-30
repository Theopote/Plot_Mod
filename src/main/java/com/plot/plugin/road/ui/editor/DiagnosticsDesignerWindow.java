package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.RoadEditDiagnosticsBanner;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.LinkedHashSet;
import java.util.Set;

/** 道路诊断只读浮动窗口（多选时汇总全部目标道路）。 */
public final class DiagnosticsDesignerWindow extends AbstractRoadFloatingEditor {

    private Set<String> targetRoadIds = Set.of();

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.DIAGNOSTICS;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.diagnostics.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_diagnostics_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_diagnostics_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.GENERAL;
    }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        LinkedHashSet<String> selected = new LinkedHashSet<>(ctx.networkManager().getSelectedRoadIds());
        if (selected.isEmpty() && roadId != null && !roadId.isBlank()) {
            selected.add(roadId);
        }
        targetRoadIds = Set.copyOf(selected);
        var live = ctx.networkManager().getNetwork();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> false);
    }

    @Override
    protected void renderWindowBody(RoadUiContext ctx) {
        if (session == null) {
            return;
        }
        // Diagnostics actions operate on the live manager: do not pass them a stale snapshot Road.
        var network = ctx.networkManager().getNetwork();
        if (targetRoadIds.size() > 1) {
            renderBatchBody(ctx, network);
            return;
        }
        Road road = network.getRoad(roadId);
        if (road == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            return;
        }
        renderSingleRoadBody(ctx, network, road);
    }

    private void renderBatchBody(RoadUiContext ctx, RoadNetwork network) {
        int totalIssues = 0;
        for (String id : targetRoadIds) {
            Road road = network.getRoad(id);
            if (road != null) {
                totalIssues += RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
            }
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.floating_editor.diagnostics.batch_scope",
                targetRoadIds.size(),
                totalIssues));
        ImGui.spacing();
        for (String id : targetRoadIds) {
            Road road = network.getRoad(id);
            if (road == null) {
                continue;
            }
            int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
            String label = RoadEdgeListHelper.formatRoadLabel(network, road)
                + " · "
                + PlotI18n.tr("plugin.road.floating_editor.diagnostics.road_issue_count", issueCount);
            if (ImGui.collapsingHeader(label + "##diag_" + id)) {
                ImGui.indent(6f);
                if (issueCount <= 0) {
                    ImGui.textColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.floating_editor.diagnostics.no_issues"));
                } else {
                    renderIssuesFrame(ctx, network, road);
                }
                ImGui.unindent(6f);
            }
        }
    }

    private void renderSingleRoadBody(RoadUiContext ctx, RoadNetwork network, Road road) {
        int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
        if (issueCount > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.edit.diagnostics_pinned", issueCount));
            ImGui.spacing();
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.floating_editor.diagnostics.no_issues"));
            ImGui.spacing();
        }
        renderIssuesFrame(ctx, network, road);
    }

    private static void renderIssuesFrame(RoadUiContext ctx, RoadNetwork network, Road road) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        float width = ImGui.getContentRegionAvail().x;
        float startY = ImGui.getCursorPosY();
        ImGui.indent(6f);
        RoadEditDiagnosticsBanner.renderDetailedIssues(ctx, network, road);
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

    @Override
    protected boolean isReadOnlyShell() {
        return true;
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        // read-only
    }

    @Override
    public boolean matchesScope(Set<String> selectedRoadIds) {
        return targetRoadIds.equals(selectedRoadIds);
    }
}
