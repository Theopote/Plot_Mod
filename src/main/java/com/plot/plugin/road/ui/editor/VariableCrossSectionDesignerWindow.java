package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.VariableCrossSectionEditor;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 沿桩号可变横断面浮动编辑器。 */
public final class VariableCrossSectionDesignerWindow extends AbstractRoadFloatingEditor {

    private final VariableCrossSectionEditor editor = new VariableCrossSectionEditor();
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.VARIABLE_CROSS_SECTION;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.variable_cross_section.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_variable_cross_section_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_variable_cross_section_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.CROSS_SECTION;
    }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        var live = ctx.networkManager().getNetwork();
        var openSnapshot = live.snapshot();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> VariableCrossSectionEditorApplier.hasChanges(openSnapshot, working, roadId));
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
        ChainageDisplayContext chainageDisplay = null;
        if (RoadStationing.isStationable(session.workingNetwork(), road)) {
            chainageDisplay = new ChainageDisplayContext(
                RoadStationing.canonicalLength(session.workingNetwork(), road),
                chainageDisplayMode,
                RoadStationFormat.DISTANCE_METERS);
        }
        editor.render(
            ctx,
            session.workingNetwork(),
            road,
            chainageDisplay,
            () -> { /* dirty tracked by session */ });
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        VariableCrossSectionEditorApplier.applyToLive(
            ctx.networkManager().getNetwork(),
            session.workingNetwork(),
            roadId);
    }
}
