package com.plot.plugin.road.ui;

import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 分段中心线编辑工具（插入 PI、分割、圆角、合并等）。
 */
final class RoadCenterlineEditPanel {

    private float centerlineEditDistance = 10f;
    private float centerlineFilletRadius = 2f;
    private int centerlineVertexIndex = 1;
    private String lastCenterlineEditMessage = "";

    String lastMessage() {
        return lastCenterlineEditMessage;
    }

    void recordMessage(CenterlineEditResult result) {
        lastCenterlineEditMessage = CenterlineEditMessages.format(result);
    }

    void render(RoadUiContext ctx, RoadNetwork network, Road road, RoadEdge edge) {
        ImGui.spacing();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.centerline_edit_section"))) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.centerline_edit_hint"));

        if (!lastCenterlineEditMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, lastCenterlineEditMessage);
        }

        float edgeLength = (float) edge.getLength();
        float[] distance = {centerlineEditDistance};
        ImGui.setNextItemWidth(Math.min(220f, ImGui.getContentRegionAvailX()));
        if (ImGui.sliderFloat(PlotI18n.tr("plugin.road.centerline_distance") + "##cl_dist", distance, 0f, Math.max(edgeLength, 1f), "%.1fm")) {
            centerlineEditDistance = distance[0];
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_insert_pi") + "##cl_pi")) {
            recordMessage(ctx.networkManager().insertPiAtLocalDistance(edge.getId(), centerlineEditDistance));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_split") + "##cl_split")) {
            recordMessage(ctx.networkManager().splitEdgeAtLocalDistance(edge.getId(), centerlineEditDistance));
        }

        List<com.plot.api.geometry.Vec2d> points = edge.getCenterlinePoints();
        int interiorCount = Math.max(0, points.size() - 2);
        if (interiorCount > 0) {
            centerlineVertexIndex = Math.min(centerlineVertexIndex, interiorCount);
            centerlineVertexIndex = Math.max(1, centerlineVertexIndex);
            ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX()));
            int[] vertexValue = {centerlineVertexIndex};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.centerline_vertex") + "##cl_vtx", vertexValue, 1, interiorCount)) {
                centerlineVertexIndex = vertexValue[0];
            }
            float[] radius = {centerlineFilletRadius};
            ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX()));
            if (ImGui.sliderFloat(PlotI18n.tr("plugin.road.centerline_fillet_radius") + "##cl_r", radius, 0.5f, 20f, "%.1fm")) {
                centerlineFilletRadius = radius[0];
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.centerline_fillet") + "##cl_fillet")) {
                recordMessage(ctx.networkManager().filletCenterlineVertex(
                    edge.getId(),
                    centerlineVertexIndex,
                    centerlineFilletRadius
                ));
            }
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_reverse_segment") + "##cl_rev_seg")) {
            recordMessage(ctx.networkManager().reverseEdge(edge.getId()));
        }

        renderMergeButtons(ctx, network, edge);
    }

    private void renderMergeButtons(RoadUiContext ctx, RoadNetwork network, RoadEdge edge) {
        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        boolean canMergeStart = RoadCenterlineEditor.canMergeAtNode(network, startNodeId);
        boolean canMergeEnd = RoadCenterlineEditor.canMergeAtNode(network, endNodeId);
        if (!canMergeStart && !canMergeEnd) {
            return;
        }
        if (canMergeStart && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_start") + "##cl_m_s")) {
            recordMessage(ctx.networkManager().mergeSegmentsAtNode(startNodeId));
        }
        if (canMergeStart && canMergeEnd) {
            ImGui.sameLine();
        }
        if (canMergeEnd && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_end") + "##cl_m_e")) {
            recordMessage(ctx.networkManager().mergeSegmentsAtNode(endNodeId));
        }
    }
}
