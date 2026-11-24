package com.plot.plugin.road.ui;


import com.plot.ui.component.UIUtils;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginSectionCard;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 分段中心线编辑工具（插入 PI、分割、圆角、合并等）。
 * <p>
 * 面板距离/半径以世界米（1 m = 1 格）展示；写入编辑器时换回画布弧长。
 * 参数区分区卡片对齐路灯设计器 {@link PluginSectionCard} 布局。
 */
public final class RoadCenterlineEditPanel {

    /** 分段内距离（世界 blocks / m）。 */
    private float centerlineEditDistanceBlocks = 10f;
    /** 圆角半径（世界 blocks / m）。 */
    private float centerlineFilletRadiusBlocks = 2f;
    private int centerlineVertexIndex = 1;
    private String lastCenterlineEditMessage = "";
    /** 圆角卡片是否展开（供钉顶预览画示意；默认开）。 */
    private boolean filletCardOpen = true;

    public void recordMessage(CenterlineEditResult result) {
        lastCenterlineEditMessage = CenterlineEditMessages.format(result);
    }

    public void clearMessages() {
        lastCenterlineEditMessage = "";
    }

    public void render(RoadUiContext ctx, RoadNetwork network, Road road, RoadEdge edge) {
        renderControls(
            ctx,
            network,
            road,
            edge,
            true,
            (targetNetwork, edgeId, distance) ->
                ctx.networkManager().insertPiAtLocalDistance(edgeId, distance),
            (targetNetwork, edgeId, distance) ->
                ctx.networkManager().splitEdgeAtLocalDistance(edgeId, distance),
            (targetNetwork, edgeId, vertexIndex, radius) ->
                ctx.networkManager().filletCenterlineVertex(edgeId, vertexIndex, radius),
            (targetNetwork, edgeId) -> ctx.networkManager().reverseEdge(edgeId),
            (targetNetwork, nodeId) -> ctx.networkManager().mergeSegmentsAtNode(nodeId));
    }

    /**
     * 钉在详情栏顶部的平面示意（不随下方工具区滚动）。
     */
    public void renderPinnedPreview(
            RoadUiContext ctx, RoadNetwork network, Road road, RoadEdge edge) {
        if (ctx == null || edge == null || ctx.host() == null) {
            return;
        }
        ICoordinateService coordinates = ctx.host().coordinates();
        float edgeBlocks = (float) RoadLengthDisplay.edgeBlocks(edge, coordinates);
        float maxDistance = Math.max(edgeBlocks, 0.01f);
        if (centerlineEditDistanceBlocks > maxDistance) {
            centerlineEditDistanceBlocks = maxDistance;
        }
        List<com.plot.api.geometry.Vec2d> points = edge.getCenterlinePoints();
        int interiorCount = Math.max(0, points.size() - 2);
        if (interiorCount > 0) {
            centerlineVertexIndex = Math.clamp(centerlineVertexIndex, 1, interiorCount);
        } else {
            centerlineVertexIndex = 1;
        }
        float maxRadius = Math.max(0.5f, maxDistance * 0.5f);
        centerlineFilletRadiusBlocks = Math.clamp(centerlineFilletRadiusBlocks, 0.5f, maxRadius);
        float filletRadiusCanvas = (float) RoadLengthDisplay.blocksToCanvas(
            centerlineFilletRadiusBlocks, network, road, coordinates);
        applyPreviewInteraction(
            CenterlineSegmentPreviewRenderer.render(
                edge,
                centerlineEditDistanceBlocks,
                edgeBlocks,
                interiorCount > 0 ? centerlineVertexIndex : 0,
                filletRadiusCanvas,
                filletCardOpen && interiorCount > 0),
            maxDistance);
    }

    /** 浮动编辑器工作副本：工具区（预览由 {@link #renderPinnedPreview} 单独钉顶）。 */
    public void renderWorkingNetwork(RoadUiContext ctx, RoadNetwork network, Road road, RoadEdge edge) {
        renderControls(
            ctx,
            network,
            road,
            edge,
            false,
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
            boolean includePreview,
            PiAction insertPi,
            PiAction splitEdge,
            FilletAction filletVertex,
            EdgeAction reverseEdge,
            NodeAction mergeAtNode) {
        if (!lastCenterlineEditMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.STATUS_INFO, lastCenterlineEditMessage);
            ImGui.spacing();
        }

        ICoordinateService coordinates = ctx.host().coordinates();
        float edgeBlocks = (float) RoadLengthDisplay.edgeBlocks(edge, coordinates);
        float maxDistance = Math.max(edgeBlocks, 0.01f);
        if (centerlineEditDistanceBlocks > maxDistance) {
            centerlineEditDistanceBlocks = maxDistance;
        }

        List<com.plot.api.geometry.Vec2d> points = edge.getCenterlinePoints();
        int interiorCount = Math.max(0, points.size() - 2);
        if (interiorCount > 0) {
            centerlineVertexIndex = Math.clamp(centerlineVertexIndex, 1, interiorCount);
        } else {
            centerlineVertexIndex = 1;
        }

        float maxRadius = Math.max(0.5f, maxDistance * 0.5f);
        centerlineFilletRadiusBlocks = Math.clamp(centerlineFilletRadiusBlocks, 0.5f, maxRadius);

        if (includePreview) {
            float filletRadiusCanvas = (float) RoadLengthDisplay.blocksToCanvas(
                centerlineFilletRadiusBlocks, network, road, coordinates);
            applyPreviewInteraction(
                CenterlineSegmentPreviewRenderer.render(
                    edge,
                    centerlineEditDistanceBlocks,
                    edgeBlocks,
                    interiorCount > 0 ? centerlineVertexIndex : 0,
                    filletRadiusCanvas,
                    filletCardOpen && interiorCount > 0),
                maxDistance);
            ImGui.spacing();
        }

        String locateSummary = PlotI18n.tr(
            "plugin.road.centerline_tool_locate_summary",
            RoadUiFormat.formatMeters(centerlineEditDistanceBlocks));
        PluginSectionCard.render(
            "##cl_sec_locate",
            PlotI18n.tr("plugin.road.centerline_tool_locate"),
            locateSummary,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> renderLocateSection(network, road, edge, coordinates, maxDistance, insertPi, splitEdge));

        String filletSummary = interiorCount > 0
            ? PlotI18n.tr(
                "plugin.road.centerline_tool_fillet_summary",
                centerlineVertexIndex,
                interiorCount,
                RoadUiFormat.formatMeters(centerlineFilletRadiusBlocks))
            : PlotI18n.tr("plugin.road.centerline_tool_fillet_summary_none");
        filletCardOpen = PluginSectionCard.render(
            "##cl_sec_fillet",
            PlotI18n.tr("plugin.road.centerline_tool_fillet"),
            filletSummary,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> renderFilletSection(
                network, road, edge, coordinates, maxDistance, interiorCount, filletVertex));

        String topologySummary = topologySummary(network, edge);
        PluginSectionCard.render(
            "##cl_sec_topology",
            PlotI18n.tr("plugin.road.centerline_tool_topology"),
            topologySummary,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> renderTopologySection(network, edge, reverseEdge, mergeAtNode));
    }

    private void applyPreviewInteraction(
            CenterlineSegmentPreviewRenderer.PreviewInteraction hit, float maxDistance) {
        if (hit == null) {
            return;
        }
        if (hit.hitPi()) {
            centerlineVertexIndex = hit.selectedPiIndex();
            return;
        }
        if (hit.hitDistance()) {
            centerlineEditDistanceBlocks = Math.clamp(hit.distanceBlocks(), 0f, maxDistance);
        }
    }

    private void renderLocateSection(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ICoordinateService coordinates,
            float maxDistance,
            PiAction insertPi,
            PiAction splitEdge) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.centerline_tool_locate_hint"));

        float[] distance = {centerlineEditDistanceBlocks};
        ImGui.setNextItemWidth(controlWidth());
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
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_insert_pi_tip"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_split") + "##cl_split")) {
            recordMessage(splitEdge.run(network, edge.getId(), canvasDistance));
        }
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_split_tip"));
        }
    }

    private void renderFilletSection(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ICoordinateService coordinates,
            float maxDistance,
            int interiorCount,
            FilletAction filletVertex) {
        if (interiorCount <= 0) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.centerline_tool_fillet_none"));
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.centerline_tool_fillet_hint"));

        ImGui.setNextItemWidth(controlWidth());
        int[] vertexValue = {centerlineVertexIndex};
        if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.centerline_vertex") + "##cl_vtx",
                vertexValue,
                1,
                interiorCount)) {
            centerlineVertexIndex = vertexValue[0];
        }
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_vertex_tip"));
        }

        float maxRadius = Math.max(0.5f, maxDistance * 0.5f);
        centerlineFilletRadiusBlocks = Math.clamp(centerlineFilletRadiusBlocks, 0.5f, maxRadius);
        float[] radius = {centerlineFilletRadiusBlocks};
        ImGui.setNextItemWidth(controlWidth());
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
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_fillet_tip"));
        }
    }

    private void renderTopologySection(
            RoadNetwork network,
            RoadEdge edge,
            EdgeAction reverseEdge,
            NodeAction mergeAtNode) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.centerline_tool_topology_hint"));

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_reverse_segment") + "##cl_rev_seg")) {
            recordMessage(reverseEdge.run(network, edge.getId()));
        }
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_reverse_tip"));
        }

        renderMergeButtons(network, edge, mergeAtNode);
    }

    private void renderMergeButtons(RoadNetwork network, RoadEdge edge, NodeAction mergeAtNode) {
        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        boolean canMergeStart = RoadCenterlineEditor.canMergeAtNode(network, startNodeId);
        boolean canMergeEnd = RoadCenterlineEditor.canMergeAtNode(network, endNodeId);
        if (!canMergeStart && !canMergeEnd) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.centerline_merge_unavailable"));
            return;
        }
        if (canMergeStart && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_start") + "##cl_m_s")) {
            recordMessage(mergeAtNode.run(network, startNodeId));
        }
        if (canMergeStart && ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_merge_start_tip"));
        }
        if (canMergeStart && canMergeEnd) {
            ImGui.sameLine();
        }
        if (canMergeEnd && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_end") + "##cl_m_e")) {
            recordMessage(mergeAtNode.run(network, endNodeId));
        }
        if (canMergeEnd && ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.centerline_merge_end_tip"));
        }
    }

    private static String topologySummary(RoadNetwork network, RoadEdge edge) {
        boolean canMergeStart = RoadCenterlineEditor.canMergeAtNode(network, edge.getStartNodeId());
        boolean canMergeEnd = RoadCenterlineEditor.canMergeAtNode(network, edge.getEndNodeId());
        if (canMergeStart && canMergeEnd) {
            return PlotI18n.tr("plugin.road.centerline_tool_topology_summary_both");
        }
        if (canMergeStart) {
            return PlotI18n.tr("plugin.road.centerline_tool_topology_summary_start");
        }
        if (canMergeEnd) {
            return PlotI18n.tr("plugin.road.centerline_tool_topology_summary_end");
        }
        return PlotI18n.tr("plugin.road.centerline_tool_topology_summary_none");
    }

    private static float controlWidth() {
        return Math.max(80f, 0.52f * ImGui.getContentRegionAvailX());
    }
}
