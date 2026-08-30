package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

import java.util.Objects;

/**
 * 逻辑道路标识编辑（名称；后续可扩展等级、标签等元数据）。
 */
public final class RoadIdentityEditor {
    private static final int MAX_NAME_LENGTH = 128;

    private String syncedRoadId = "";
    private final ImString nameBuffer = new ImString(MAX_NAME_LENGTH);

    public void render(RoadNetwork network, Road road, Runnable onHistory) {
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
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.road_metadata_coming_soon"));
    }

    private void syncBuffer(Road road) {
        if (Objects.equals(syncedRoadId, road.getId())) {
            return;
        }
        syncedRoadId = road.getId();
        nameBuffer.set(road.getName() != null ? road.getName() : "");
    }

    private void commitName(Road road, Runnable onHistory) {
        String committed = normalizeDraftName(nameBuffer.get());
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
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
