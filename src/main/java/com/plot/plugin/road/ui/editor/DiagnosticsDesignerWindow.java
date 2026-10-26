package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.ui.RoadEditDiagnosticsBanner;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;

/** 道路诊断只读浮动窗口。 */
public final class DiagnosticsDesignerWindow extends AbstractRoadFloatingEditor {

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
        Road road = session.workingNetwork().getRoad(roadId);
        if (road == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            return;
        }
        int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx, session.workingNetwork(), road);
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
        ImVec2 origin = ImGui.getCursorScreenPos();
        float width = ImGui.getContentRegionAvail().x;
        float startY = ImGui.getCursorPosY();
        ImGui.indent(6f);
        RoadEditDiagnosticsBanner.renderDetailedIssues(ctx, session.workingNetwork(), road);
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
    protected void applySessionToLive(RoadUiContext ctx) {
        // read-only
    }
}
