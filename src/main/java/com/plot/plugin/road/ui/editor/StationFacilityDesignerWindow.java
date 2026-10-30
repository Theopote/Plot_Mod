package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.StationFacilityEditor;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.LinkedHashSet;
import java.util.Set;

/** 沿桩号附属设施浮动编辑器（多选时确认写入全部目标道路）。 */
public final class StationFacilityDesignerWindow extends AbstractRoadFloatingEditor {

    private final StationFacilityEditor editor = new StationFacilityEditor();
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;
    private java.util.List<String> targetRoadIds = java.util.List.of();
    private BatchStationMappingMode batchMappingMode = BatchStationMappingMode.PROPORTIONAL;

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.STATION_FACILITIES;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.station_facilities.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_station_facilities_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_station_facilities_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.CROSS_SECTION;
    }

    @Override
    protected boolean hasInvalidDrafts() { return editor.hasInvalidDrafts(); }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        editor.reset();
        LinkedHashSet<String> selected = new LinkedHashSet<>(ctx.networkManager().getSelectedRoadIds());
        if (selected.isEmpty() && roadId != null && !roadId.isBlank()) {
            selected.add(roadId);
        }
        targetRoadIds = java.util.List.copyOf(selected);
        var live = ctx.networkManager().getNetwork();
        var openSnapshot = live.snapshot();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> StationFacilityEditorApplier.hasChanges(openSnapshot, working, roadId));
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
        if (targetRoadIds.size() > 1) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.floating_editor.station_facilities.batch_scope",
                    targetRoadIds.size()));
            BatchStationMappingMode previous = batchMappingMode;
            batchMappingMode = BatchStationMappingUi.renderModeSelector(
                batchMappingMode, "station_facilities");
            if (previous != batchMappingMode) {
                lossyBatchAcknowledged = false;
            }
            BatchStationMappingUi.renderFacilitySideNote();
            BatchApplyReport preview = BatchStationMappingUi.previewFacilities(
                session.workingNetwork(),
                road,
                road.getStationFacilities(),
                new LinkedHashSet<>(targetRoadIds),
                batchMappingMode);
            BatchStationMappingUi.renderPreviewReport(preview);
            ImGui.spacing();
        }
        ChainageDisplayContext chainageDisplay = null;
        if (RoadStationing.isStationable(session.workingNetwork(), road)) {
            chainageDisplay = new ChainageDisplayContext(
                RoadStationing.canonicalLength(session.workingNetwork(), road),
                chainageDisplayMode,
                RoadStationFormat.DISTANCE_METERS);
        }
        editor.render(
            session.workingNetwork(),
            road,
            chainageDisplay,
            () -> { /* dirty tracked by session */ });
    }

    @Override
    protected boolean isBatchMappingLossy() {
        if (session == null || targetRoadIds.size() <= 1) {
            return false;
        }
        Road road = session.workingNetwork().getRoad(roadId);
        if (road == null || road.getStationFacilities() == null) {
            return false;
        }
        return BatchStationMappingUi.previewFacilities(
            session.workingNetwork(),
            road,
            road.getStationFacilities(),
            new LinkedHashSet<>(targetRoadIds),
            batchMappingMode).isLossy();
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        BatchApplyReport report = StationFacilityEditorApplier.applyToLive(
            ctx.networkManager().getNetwork(),
            session.workingNetwork(),
            roadId,
            new LinkedHashSet<>(targetRoadIds),
            batchMappingMode);
        BatchStationMappingUi.reportApplyResult(ctx, report);
    }

    @Override
    public boolean matchesScope(Set<String> selectedRoadIds) {
        return new LinkedHashSet<>(targetRoadIds).equals(selectedRoadIds);
    }

    @Override
    protected void restoreSession() {
        super.restoreSession();
        editor.reset();
        lossyBatchAcknowledged = false;
    }
}
