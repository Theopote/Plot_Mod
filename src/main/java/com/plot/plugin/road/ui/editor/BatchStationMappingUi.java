package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.Set;

/** 批量桩号映射模式选择与超界预警。 */
final class BatchStationMappingUi {
    private BatchStationMappingUi() {
    }

    static BatchStationMappingMode renderModeSelector(BatchStationMappingMode current, String idSuffix) {
        BatchStationMappingMode mode = current != null ? current : BatchStationMappingMode.PROPORTIONAL;
        String[] labels = {
            PlotI18n.tr("plugin.road.floating_editor.batch_mapping.proportional"),
            PlotI18n.tr("plugin.road.floating_editor.batch_mapping.absolute")
        };
        ImInt index = new ImInt(mode == BatchStationMappingMode.ABSOLUTE ? 1 : 0);
        if (ImGui.combo(
                PlotI18n.tr("plugin.road.floating_editor.batch_mapping.mode") + "##" + idSuffix,
                index,
                labels)) {
            mode = index.get() == 1
                ? BatchStationMappingMode.ABSOLUTE
                : BatchStationMappingMode.PROPORTIONAL;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.batch_station_mapping"));
        }
        return mode;
    }

    static void renderFacilitySideNote() {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.floating_editor.batch_mapping.side_note"));
    }

    static void renderPreviewReport(BatchApplyReport preview) {
        if (preview == null) {
            return;
        }
        if (preview.hasWarnings()) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr(
                    "plugin.road.floating_editor.batch_mapping.preview_stats",
                    preview.appliedTargets(),
                    preview.skippedTargets(),
                    preview.droppedItems(),
                    preview.clampedItems()));
        } else if (preview.appliedTargets() > 0) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.floating_editor.batch_mapping.preview_stats",
                    preview.appliedTargets(),
                    preview.skippedTargets(),
                    preview.droppedItems(),
                    preview.clampedItems()));
        }
    }

    static BatchApplyReport previewVariable(
            RoadNetwork network,
            Road sourceRoad,
            RoadVariableCrossSections source,
            Set<String> targetRoadIds,
            BatchStationMappingMode mode) {
        return VariableCrossSectionEditorApplier.preview(
            network, sourceRoad, source, targetRoadIds, mode);
    }

    static BatchApplyReport previewFacilities(
            RoadNetwork network,
            Road sourceRoad,
            RoadStationFacilities source,
            Set<String> targetRoadIds,
            BatchStationMappingMode mode) {
        return StationFacilityEditorApplier.preview(
            network, sourceRoad, source, targetRoadIds, mode);
    }

    static void reportApplyResult(RoadUiContext ctx, BatchApplyReport report) {
        if (ctx == null || report == null) {
            return;
        }
        String message = PlotI18n.tr(
            "plugin.road.floating_editor.batch_mapping.apply_stats",
            report.appliedTargets(),
            report.skippedTargets(),
            report.droppedItems(),
            report.clampedItems());
        if (report.hasWarnings()) {
            ctx.status().warning(message);
        } else if (report.appliedTargets() > 0) {
            ctx.status().info(message);
        }
    }

    static boolean renderLossyConfirmDialog(
            String popupId,
            boolean pending,
            Runnable clearPending,
            Runnable onProceed,
            Runnable onCancel) {
        if (!com.plot.plugin.road.ui.RoadUiWidgets.beginDeferredPopupModal(popupId, pending, clearPending)) {
            return false;
        }
        try {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.batch_mapping.lossy_confirm_message"));
            ImGui.separator();
            float buttonWidth = 140f;
            if (ImGui.button(
                    PlotI18n.tr("plugin.road.floating_editor.batch_mapping.lossy_confirm_proceed")
                        + "##lossy_ok",
                    buttonWidth,
                    0)) {
                onProceed.run();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(
                    PlotI18n.tr("plugin.road.floating_editor.continue_edit") + "##lossy_cancel",
                    buttonWidth,
                    0)) {
                onCancel.run();
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
        return true;
    }
}
