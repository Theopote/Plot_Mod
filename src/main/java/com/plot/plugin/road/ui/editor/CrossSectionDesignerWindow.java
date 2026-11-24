package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.ui.CrossSectionDraftMutator;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 横断面 / 材质设计器浮动窗口。 */
public final class CrossSectionDesignerWindow extends AbstractRoadFloatingEditor {

    private final CrossSectionDesignerPanel panel = new CrossSectionDesignerPanel();

    /** 跨帧复用，避免拖滑条时每帧 fromRoad 分配与重解析。 */
    private CrossSectionDraftMutator cachedMutator;
    private String cachedMutatorRoadId = "";
    private RoadNetworkEditorSession cachedMutatorSession;
    private com.plot.plugin.road.model.RoadNetwork cachedMutatorWorking;

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.CROSS_SECTION;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.cross_section.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_cross_section_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_cross_section_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.CROSS_SECTION;
    }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        invalidateUiCaches();
        var live = ctx.networkManager().getNetwork();
        var config = ctx.networkManager().getConfig();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            (open, working) -> CrossSectionEditorApplier.hasChanges(
                open, working, roadId, config));
    }

    @Override
    protected void restoreSession() {
        invalidateUiCaches();
        super.restoreSession();
    }

    @Override
    public void discard(RoadUiContext ctx) {
        invalidateUiCaches();
        super.discard(ctx);
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
        RoadSystemConfig config = ctx.networkManager().getConfig();
        CrossSectionDraftMutator mutator = mutatorFor(road, config);
        panel.renderForRoad(
            ctx,
            mutator,
            session.workingNetwork(),
            road,
            config,
            true,
            road);
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        CrossSectionEditorApplier.applyToLive(
            ctx.networkManager().getNetwork(),
            session.workingNetwork(),
            roadId,
            ctx.networkManager().getConfig());
    }

    private CrossSectionDraftMutator mutatorFor(Road road, RoadSystemConfig config) {
        if (cachedMutator == null
                || cachedMutatorSession != session
                || cachedMutatorWorking != session.workingNetwork()
                || !roadId.equals(cachedMutatorRoadId)) {
            cachedMutator = CrossSectionDraftMutator.forRoad(
                road,
                config,
                session::pushUndoCheckpoint);
            cachedMutatorSession = session;
            cachedMutatorRoadId = roadId;
            cachedMutatorWorking = session.workingNetwork();
        }
        return cachedMutator;
    }

    @Override
    protected void onWorkingNetworkReplaced() {
        invalidateUiCaches();
    }

    private void invalidateUiCaches() {
        cachedMutator = null;
        cachedMutatorRoadId = "";
        cachedMutatorSession = null;
        cachedMutatorWorking = null;
        panel.invalidate();
    }
}
