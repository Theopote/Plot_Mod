package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.station.ChainageDisplayContext;
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
    private java.util.List<String> targetRoadIds = java.util.List.of();
    private BatchStationMappingMode batchMappingMode = BatchStationMappingMode.PROPORTIONAL;
    /** 多选映射预览：按工作副本设施引用 + 映射模式缓存，避免静止帧重算。 */
    private BatchApplyReport cachedBatchPreview;
    private RoadStationFacilities cachedPreviewFacilities;
    private BatchStationMappingMode cachedPreviewMode;

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
        invalidateBatchPreviewCache();
        LinkedHashSet<String> selected = new LinkedHashSet<>(ctx.networkManager().getSelectedRoadIds());
        if (selected.isEmpty() && roadId != null && !roadId.isBlank()) {
            selected.add(roadId);
        }
        targetRoadIds = java.util.List.copyOf(selected);
        var live = ctx.networkManager().getNetwork();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            (open, working) -> StationFacilityEditorApplier.hasChanges(open, working, roadId));
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
            BatchApplyReport preview = cachedOrComputeBatchPreview(road);
            BatchStationMappingUi.renderPreviewReport(preview);
            ImGui.spacing();
        }
        // 与变截面/中心线设计器一致：只读展示桩号，固定自起点递增。
        ChainageDisplayContext chainageDisplay = RoadStationing.isStationable(session.workingNetwork(), road)
            ? ChainageDisplayContext.fromStart(RoadStationing.canonicalLength(session.workingNetwork(), road))
            : null;
        editor.render(
            ctx,
            session.workingNetwork(),
            road,
            chainageDisplay,
            session::pushUndoCheckpoint);
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
        return cachedOrComputeBatchPreview(road).isLossy();
    }

    private BatchApplyReport cachedOrComputeBatchPreview(Road road) {
        RoadStationFacilities facilities = road.getStationFacilities();
        if (cachedBatchPreview != null
                && cachedPreviewFacilities == facilities
                && cachedPreviewMode == batchMappingMode) {
            return cachedBatchPreview;
        }
        cachedBatchPreview = BatchStationMappingUi.previewFacilities(
            session.workingNetwork(),
            road,
            facilities,
            new LinkedHashSet<>(targetRoadIds),
            batchMappingMode);
        cachedPreviewFacilities = facilities;
        cachedPreviewMode = batchMappingMode;
        return cachedBatchPreview;
    }

    private void invalidateBatchPreviewCache() {
        cachedBatchPreview = null;
        cachedPreviewFacilities = null;
        cachedPreviewMode = null;
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
        invalidateBatchPreviewCache();
        lossyBatchAcknowledged = false;
    }

    @Override
    protected void onWorkingNetworkReplaced() {
        editor.reset();
        invalidateBatchPreviewCache();
        lossyBatchAcknowledged = false;
    }

    @Override
    public void discard(RoadUiContext ctx) {
        editor.reset();
        invalidateBatchPreviewCache();
        super.discard(ctx);
    }
}
