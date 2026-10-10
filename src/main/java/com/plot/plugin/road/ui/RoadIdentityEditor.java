package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.utils.ImStringUtf8;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.Objects;

/**
 * 逻辑道路标识编辑（名称；后续可扩展等级、标签等元数据）。
 */
public final class RoadIdentityEditor {
    private String syncedRoadId = "";
    private final ImString nameBuffer = createNameBuffer();

    private static ImString createNameBuffer() {
        ImString buffer = new ImString(256);
        buffer.inputData.isResizable = true;
        buffer.inputData.resizeFactor = 256;
        return buffer;
    }

    public void render(RoadNetwork network, Road road, RoadNetworkManager networkManager, Runnable onHistory) {
        if (road == null || network == null) {
            return;
        }
        syncBuffer(road);

        ImGui.text(PlotI18n.tr("plugin.road.road_identity_section"));
        String autoLabel = RoadEdgeListHelper.formatAutoRoadLabel(network, road);
        float clearWidth = ImGui.calcTextSize(PlotI18n.tr("plugin.road.road_name_clear")).x
            + ImGui.getStyle().getFramePaddingX() * 2.0f + 8.0f;
        float nameWidth = Math.max(120f, ImGui.getContentRegionAvail().x - clearWidth - ImGui.getStyle().getItemSpacingX());
        ImGui.setNextItemWidth(nameWidth);
        ImGui.inputTextWithHint(
            "##road_name",
            PlotI18n.tr("plugin.road.road_name_hint", autoLabel),
            nameBuffer,
            ImGuiInputTextFlags.None);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_name"));
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            commitName(road, onHistory);
        }
        ImGui.sameLine();
        boolean hasCustomName = road.getName() != null && !road.getName().isBlank();
        if (!hasCustomName) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.road_name_clear") + "##road_name_clear")) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setName(null);
            nameBuffer.set("");
        }
        if (!hasCustomName) {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered() && hasCustomName) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_name_clear"));
        }

        if (hasCustomName) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.road_name_auto_label", autoLabel));
        }

        renderTopologyMode(road, onHistory);
        if (networkManager != null) {
            renderLoopSeamControls(networkManager, road);
        }
    }

    private void renderTopologyMode(Road road, Runnable onHistory) {
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.topology_mode"));
        String[] labels = {
            PlotI18n.tr("plugin.road.topology_mode.linear"),
            PlotI18n.tr("plugin.road.topology_mode.loop")
        };
        ImInt index = new ImInt(road.getTopologyMode() == RoadTopologyMode.LOOP ? 1 : 0);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##road_topology_mode", index, labels)) {
            RoadTopologyMode selected = index.get() == 1 ? RoadTopologyMode.LOOP : RoadTopologyMode.LINEAR;
            if (selected != road.getTopologyMode()) {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setTopologyMode(selected);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.topology_mode"));
        }
        if (road.getTopologyMode() == RoadTopologyMode.LOOP) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.topology_mode.loop_hint"));
        }
    }

    private void renderLoopSeamControls(RoadNetworkManager networkManager, Road road) {
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            return;
        }
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.loop_seam_section"));
        if (ImGui.button(PlotI18n.tr("plugin.road.loop_seam_set_on_canvas"))) {
            networkManager.beginLoopSeamCanvasPick(road.getId());
        }
        if (networkManager.getLoopSeamPickSession().matchesRoad(road.getId())) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.INFO_BLUE,
                PlotI18n.tr("plugin.road.loop_seam_pick_active"));
            if (ImGui.button(PlotI18n.tr("plugin.road.loop_seam_pick_cancel"))) {
                networkManager.cancelLoopSeamCanvasPick();
            }
        }
        if (networkManager.isLoopSeamRemapConfirmPending()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.loop_seam_remap_confirm"));
            if (ImGui.button(PlotI18n.tr("plugin.road.loop_seam_remap_apply"))) {
                networkManager.confirmLoopSeamRemap();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.loop_seam_remap_cancel"))) {
                networkManager.declineLoopSeamRemap();
            }
        }
    }

    private void syncBuffer(Road road) {
        if (Objects.equals(syncedRoadId, road.getId())) {
            return;
        }
        syncedRoadId = road.getId();
        nameBuffer.set(road.getName() != null ? road.getName() : "");
    }

    private void commitName(Road road, Runnable onHistory) {
        String committed = normalizeDraftName(ImStringUtf8.read(nameBuffer));
        String current = road.getName();
        if (Objects.equals(current, committed)) {
            return;
        }
        if (onHistory != null) {
            onHistory.run();
        }
        road.setName(committed);
        nameBuffer.set(committed != null ? committed : "");
    }

    private static String normalizeDraftName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
