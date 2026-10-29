package com.plot.plugin.road.ui;

import com.plot.api.world.ICoordinateService;
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
 * <p>
 * 面板距离/半径以世界米（1 m = 1 格）展示；写入编辑器时换回画布弧长。
 */
public final class RoadCenterlineEditPanel {

    /** 分段内距离（世界 blocks / m）。 */
    private float centerlineEditDistanceBlocks = 10f;
    /** 圆角半径（世界 blocks / m）。 */
    private float centerlineFilletRadiusBlocks = 2f;
    private int centerlineVertexIndex = 1;
    private String lastCenterlineEditMessage = "";

    String lastMessage() {
        return lastCenterlineEditMessage;
    }

    public void recordMessage(CenterlineEditResult result) {
        lastCenterlineEditMessage = CenterlineEditMessages.format(result);
    }

    public void render(RoadUiContext ctx, RoadNetwork network, Road road, RoadEdge edge) {
        renderControls(
            ctx,
            network,
            road,
            edge,
            (targetNetwork, edgeId, distance) ->
                ctx.networkManager().insertPiAtLocalDistance(edgeId, distance),
            (targetNetwork, edgeId, distance) ->
                ctx.networkManager().splitEdgeAtLocalDistance(edgeId, distance),
            (targetNetwork, edgeId, vertexIndex, radius) ->
                ctx.networkManager().filletCenterlineVertex(edgeId, vertexIndex, radius),
            (targetNetwork, edgeId) -> ctx.networkManager().reverseEdge(edgeId),
            (targetNetwork, nodeId) -> ctx.networkManager().mergeSegmentsAtNode(nodeId));
    }

    /** 浮动编辑器工作副本：直接写入 {@code network}，确认时再一次性写回 live。 */
    public void renderWorkingNetwork(RoadUiContext ctx, RoadNetwork network, Road road, RoadEdge edge) {
        renderControls(
            ctx,
            network,
            road,
            edge,
            RoadCenterlineEditor::insertPiAtLocalDistance,
            RoadCenterlineEditor::splitAtLocalDistance,
            RoadCenterlineEditor::filletVertex,
            RoadCenterlineEditor::reverseEdge,
            RoadCenterlineEditor::mergeThroughNode);
    }

    @FunctionalInterface
    private interface PiAction {
        CenterlineEditResult run(RoadNetwork network, String edgeId, double distance);
    }

    @FunctionalInterface
    private interface FilletAction {
        CenterlineEditResult run(RoadNetwork network, String edgeId, int vertexIndex, double radius);
    }

    @FunctionalInterface
    private interface EdgeAction {
        CenterlineEditResult run(RoadNetwork network, String edgeId);
    }

    @FunctionalInterface
    private interface NodeAction {
        CenterlineEditResult run(RoadNetwork network, String nodeId);
    }

    private void renderControls(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            PiAction insertPi,
            PiAction splitEdge,
            FilletAction filletVertex,
            EdgeAction reverseEdge,
            NodeAction mergeAtNode) {
        if (!lastCenterlineEditMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, lastCenterlineEditMessage);
        }

        ICoordinateService coordinates = ctx.host().coordinates();
        float edgeBlocks = (float) RoadLengthDisplay.edgeBlocks(edge, coordinates);
        float maxDistance = Math.max(edgeBlocks, 0.01f);
        if (centerlineEditDistanceBlocks > maxDistance) {
            centerlineEditDistanceBlocks = maxDistance;
        }

        float[] distance = {centerlineEditDistanceBlocks};
        ImGui.setNextItemWidth(Math.min(220f, ImGui.getContentRegionAvailX()));
        if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.centerline_distance") + "##cl_dist",
                distance,
                0f,
                maxDistance,
                RoadUiFormat.SLIDER_METERS)) {
            centerlineEditDistanceBlocks = distance[0];
        }

        double canvasDistance = RoadLengthDisplay.blocksToCanvas(
            centerlineEditDistanceBlocks, network, road, coordinates);

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_insert_pi") + "##cl_pi")) {
            recordMessage(insertPi.run(network, edge.getId(), canvasDistance));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_split") + "##cl_split")) {
            recordMessage(splitEdge.run(network, edge.getId(), canvasDistance));
        }

        List<com.plot.api.geometry.Vec2d> points = edge.getCenterlinePoints();
        int interiorCount = Math.max(0, points.size() - 2);
        if (interiorCount > 0) {
            centerlineVertexIndex = Math.min(centerlineVertexIndex, interiorCount);
            centerlineVertexIndex = Math.max(1, centerlineVertexIndex);
            ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX()));
            int[] vertexValue = {centerlineVertexIndex};
            if (ImGui.sliderInt(
                    PlotI18n.tr("plugin.road.centerline_vertex") + "##cl_vtx",
                    vertexValue,
                    1,
                    interiorCount)) {
                centerlineVertexIndex = vertexValue[0];
            }
            float maxRadius = Math.max(0.5f, maxDistance * 0.5f);
            centerlineFilletRadiusBlocks = Math.min(
                Math.max(0.5f, centerlineFilletRadiusBlocks),
                maxRadius);
            float[] radius = {centerlineFilletRadiusBlocks};
            ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX()));
            if (ImGui.sliderFloat(
                    PlotI18n.tr("plugin.road.centerline_fillet_radius") + "##cl_r",
                    radius,
                    0.5f,
                    maxRadius,
                    RoadUiFormat.SLIDER_METERS)) {
                centerlineFilletRadiusBlocks = radius[0];
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.centerline_fillet") + "##cl_fillet")) {
                double canvasRadius = RoadLengthDisplay.blocksToCanvas(
                    centerlineFilletRadiusBlocks, network, road, coordinates);
                recordMessage(filletVertex.run(
                    network,
                    edge.getId(),
                    centerlineVertexIndex,
                    canvasRadius));
            }
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_reverse_segment") + "##cl_rev_seg")) {
            recordMessage(reverseEdge.run(network, edge.getId()));
        }

        renderMergeButtons(network, edge, mergeAtNode);
    }

    private void renderMergeButtons(RoadNetwork network, RoadEdge edge, NodeAction mergeAtNode) {
        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        boolean canMergeStart = RoadCenterlineEditor.canMergeAtNode(network, startNodeId);
        boolean canMergeEnd = RoadCenterlineEditor.canMergeAtNode(network, endNodeId);
        if (!canMergeStart && !canMergeEnd) {
            return;
        }
        if (canMergeStart && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_start") + "##cl_m_s")) {
            recordMessage(mergeAtNode.run(network, startNodeId));
        }
        if (canMergeStart && canMergeEnd) {
            ImGui.sameLine();
        }
        if (canMergeEnd && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_end") + "##cl_m_e")) {
            recordMessage(mergeAtNode.run(network, endNodeId));
        }
    }
}
