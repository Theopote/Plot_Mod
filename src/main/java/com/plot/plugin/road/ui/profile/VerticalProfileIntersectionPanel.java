package com.plot.plugin.road.ui.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.ProfileChartInteraction;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.ui.ProfileEditorState;
import com.plot.plugin.road.ui.RoadGradeSeparationControls;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiFormat;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;
import java.util.Objects;

/**
 * 纵断面编辑器交点详情、高程数值编辑与立体交叉控件。
 */
public final class VerticalProfileIntersectionPanel {

    /** 供交点面板注入，避免与编辑器循环依赖。 */
    public interface Host {
        ProfileEditorWindowSession windowSession();

        ProfileEditSession profileEditSession();

        ProfileEditorState editorState();

        int selectedIntersectionIndex();

        int activeIntersectionDragIndex();

        ProfileChartInteraction.IntersectionDragTarget activeIntersectionDragTarget();

        void recordUndoPointBeforeEdit();

        void notifyWorkingProfileChanged();

        void setProfileAutoFixMessage(String message);

        RoadElevationBounds resolveElevationBounds(RoadUiContext ctx);

        int findPviIndexAtStation(RoadVerticalAlignment alignment, double station);

        void selectEditorRoad(RoadUiContext ctx, String roadId, boolean sync);
    }

    private final Host host;
    private String intersectionInputKey = "";
    private final imgui.type.ImFloat intersectionHeightInput = new imgui.type.ImFloat();
    private boolean intersectionInputDirty;
    private int intersectionInputTarget;
    private RoadGradeSeparationControls gradeSeparationControls;
    private boolean profileDesignSyncedNotice;

    public VerticalProfileIntersectionPanel(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void clear() {
        resetHeightInputState();
        profileDesignSyncedNotice = false;
    }

    public void resetHeightInputState() {
        intersectionInputKey = "";
    }

    public void applyIntersectionDrag(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            boolean flatMode,
            List<RoadProfileIntersection> intersections,
            ProfileChartInteraction.ControlInteraction interaction) {
        ProfileEditSession profileEditSession = host.profileEditSession();
        int dragIndex = interaction.activeIntersectionDragIndex() >= 0
            ? interaction.activeIntersectionDragIndex()
            : host.activeIntersectionDragIndex();
        ProfileChartInteraction.IntersectionDragTarget dragTargetEnum =
            interaction.activeIntersectionDragTarget()
                    != ProfileChartInteraction.IntersectionDragTarget.NONE
                ? interaction.activeIntersectionDragTarget()
                : host.activeIntersectionDragTarget();
        if (interaction.draggedIntersectionElevation() == null
                || dragIndex < 0
                || dragIndex >= intersections.size()) {
            return;
        }
        RoadProfileIntersectionDragEditor.DragTarget dragTarget =
            mapIntersectionDragTarget(dragTargetEnum);
        if (dragTarget == null) {
            return;
        }
        if (flatMode
                && dragTarget != RoadProfileIntersectionDragEditor.DragTarget.OTHER) {
            profileEditSession.updateFlatRoadWholeLineDrag(
                network,
                road,
                config,
                dragIndex,
                dragTarget,
                interaction.draggedIntersectionElevation(),
                intersections);
        } else if (profileEditSession.shouldLinkFlatPartnerDrag(
                network, road, intersections, dragIndex, dragTarget)) {
            profileEditSession.updateFlatPartnerIntersectionDrag(
                network,
                road,
                config,
                dragIndex,
                dragTarget,
                interaction.draggedIntersectionElevation(),
                intersections);
        } else {
            profileEditSession.updateIntersectionDrag(
                dragIndex,
                dragTarget,
                interaction.draggedIntersectionElevation());
        }
    }

    public void renderIntersectionDetail(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> intersections,
            RoadSystemConfig config,
            boolean flatMode) {
        int selectedIntersectionIndex = host.selectedIntersectionIndex();
        if (selectedIntersectionIndex < 0 || selectedIntersectionIndex >= intersections.size()) {
            return;
        }
        RoadProfileIntersection intersection = intersections.get(selectedIntersectionIndex);
        // 详情区顶部不再画 separator：上方常无内容时会出现一条悬空分割线。
        renderIntersectionTitleRow(network, road, intersection);
        boolean crossingChanged = renderProfileGradeSeparationControls(
            ctx, network, road, intersection, config);
        renderIntersectionMetricsRow(intersection, network, config);
        renderIntersectionWarningsOnly(intersection, network, config);
        if (profileDesignSyncedNotice) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.profile_intersection_grade_sync_status"));
            profileDesignSyncedNotice = false;
        } else if (flatMode && !intersection.gradeSeparated()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_flat_intersection_edit_hint"));
        }
        if (!flatMode
                && ImGui.collapsingHeader(
                    PlotI18n.tr("plugin.road.profile_intersection_height_edit_section")
                        + "##profile_intersection_height_" + selectedIntersectionIndex)) {
            renderIntersectionHeightEdit(
                ctx, network, road, intersections, selectedIntersectionIndex, config, flatMode);
        }
        if (crossingChanged) {
            var result = com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.synchronize(
                network, road, road.getVerticalAlignment(), config, host.resolveElevationBounds(ctx));
            ProfileEditorWindowSession windowSession = host.windowSession();
            if (windowSession != null) {
                windowSession.markPropagationAdjustedRoads(network);
            }
            if (!result.unresolvedConstraints().isEmpty()) {
                host.setProfileAutoFixMessage(PlotI18n.tr(
                    "plugin.road.vertical_alignment_network_unresolved",
                    result.unresolvedConstraints().size()));
            }
            host.notifyWorkingProfileChanged();
            profileDesignSyncedNotice = true;
        }
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.profile_intersection_detail")
                    + "##profile_intersection_detail_" + selectedIntersectionIndex,
                imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            renderOtherRoadCrossSectionPreview(intersection);
        }
    }

    /** 相交道路横断面：一行车道/总宽 + 参考横断面工作区的预览图。 */
    private static void renderOtherRoadCrossSectionPreview(RoadProfileIntersection intersection) {
        var layout = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            intersection.otherCrossSection(), 0f);
        ImGui.text(PlotI18n.tr(
            "plugin.road.lane_count_summary",
            intersection.otherCrossSection().laneCount,
            Math.round(layout.totalWidthBlocks())));
        float previewWidth = Math.max(80f, ImGui.getContentRegionAvailX());
        float previewHeight = Math.min(148f, Math.max(110f, previewWidth * 0.48f));
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x,
            origin.y,
            origin.x + previewWidth,
            origin.y + previewHeight,
            PluginUiColors.PANEL_BG_DARK);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + previewWidth,
            origin.y + previewHeight,
            PluginUiColors.PANEL_BORDER);
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout,
            origin.x,
            origin.y,
            previewWidth,
            previewHeight,
            RoadCrossSectionPreviewRenderer.MiniRenderOptions.quickDesign());
        ImGui.dummy(previewWidth, previewHeight);
    }

    private static RoadProfileIntersectionDragEditor.DragTarget mapIntersectionDragTarget(
            ProfileChartInteraction.IntersectionDragTarget target) {
        if (target == null) {
            return null;
        }
        return switch (target) {
            case CURRENT -> RoadProfileIntersectionDragEditor.DragTarget.CURRENT;
            case OTHER -> RoadProfileIntersectionDragEditor.DragTarget.OTHER;
            case SHARED -> RoadProfileIntersectionDragEditor.DragTarget.SHARED;
            case NONE -> null;
        };
    }

    private void renderIntersectionHeightEdit(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> intersections,
            int index,
            RoadSystemConfig config,
            boolean flatMode) {
        ProfileEditSession profileEditSession = host.profileEditSession();
        ProfileEditorWindowSession windowSession = host.windowSession();
        ProfileEditorState editorState = host.editorState();
        var intersection = intersections.get(index);
        String key = road.getId() + ":" + intersection.nodeId() + ":" + intersection.roadStation();
        if (!key.equals(intersectionInputKey)) {
            intersectionInputKey = key;
            intersectionInputTarget = 0;
            intersectionInputDirty = false;
        }
        if (intersection.gradeSeparated() && ImGui.beginCombo(
                PlotI18n.tr("plugin.road.profile_intersection_edit_target"),
                PlotI18n.tr(intersectionInputTarget == 0 ? "plugin.road.profile_intersection_target_current"
                    : "plugin.road.profile_intersection_target_other"))) {
            for (int target = 0; target < 2; target++) {
                if (ImGui.selectable(PlotI18n.tr(target == 0 ? "plugin.road.profile_intersection_target_current"
                        : "plugin.road.profile_intersection_target_other"), intersectionInputTarget == target)) {
                    intersectionInputTarget = target;
                    intersectionInputDirty = false;
                }
            }
            ImGui.endCombo();
        }
        if (!intersectionInputDirty) intersectionHeightInput.set((float) (intersectionInputTarget == 1
            && intersection.gradeSeparated() ? intersection.otherRoadElevation() : intersection.currentRoadElevation()));
        if (ImGui.inputFloat(PlotI18n.tr("plugin.road.profile_intersection_height_edit"),
                intersectionHeightInput, 0.1f, 1f, "%.2f")) intersectionInputDirty = true;
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_apply_height"))
                && Float.isFinite(intersectionHeightInput.get())) {
            host.recordUndoPointBeforeEdit();
            profileEditSession.setElevationBounds(host.resolveElevationBounds(ctx));
            profileEditSession.beginIntersectionEdit(intersections);
            var target = !intersection.gradeSeparated() ? RoadProfileIntersectionDragEditor.DragTarget.SHARED
                : intersectionInputTarget == 0 ? RoadProfileIntersectionDragEditor.DragTarget.CURRENT
                : RoadProfileIntersectionDragEditor.DragTarget.OTHER;
            profileEditSession.updateIntersectionDrag(index, target, intersectionHeightInput.get());
            profileEditSession.commitEdit(
                network, road, config, windowSession, null, host::notifyWorkingProfileChanged);
            intersectionInputDirty = false;
            ctx.requestOverlayRefresh();
        }
        if (!flatMode && VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_smooth"))) {
                host.recordUndoPointBeforeEdit();
                var before = road.getVerticalAlignment();
                com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.setElevation(
                    road, intersection.roadStation(), intersection.currentRoadElevation());
                int pvi = host.findPviIndexAtStation(
                    road.getVerticalAlignment(), intersection.roadStation());
                var fitted = com.plot.plugin.road.vertical.VerticalProfileCurveFitter.fitAt(
                    road.getVerticalAlignment(), pvi);
                host.setProfileAutoFixMessage(PlotI18n.tr(fitted.hasSpace()
                    ? "plugin.road.vertical_alignment_auto_smooth_success"
                    : "plugin.road.vertical_alignment_auto_smooth_no_space"));
                road.setVerticalAlignment(fitted.alignment());
                com.plot.plugin.road.vertical.ProfileSourceEdits.markWorkingCopyEdited(road);
                com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.synchronize(
                    network, road, before, config, host.resolveElevationBounds(ctx));
                editorState.selectedProfilePvi = pvi;
                if (windowSession != null) windowSession.markPropagationAdjustedRoads(network);
                host.notifyWorkingProfileChanged();
                ctx.requestOverlayRefresh();
            }
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_intersection_numeric_hint"));
        if (!Objects.equals(road.getId(), intersection.otherRoadId())
                && ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_switch_road"))) {
            host.selectEditorRoad(ctx, intersection.otherRoadId(), true);
        }
    }

    private static void renderIntersectionTitleRow(
            RoadNetwork network,
            Road road,
            RoadProfileIntersection intersection) {
        String currentLabel = RoadEdgeListHelper.formatRoadLabel(network, road);
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_intersection_compact_paths",
            currentLabel,
            intersection.otherRoadLabel()));
        ImGui.sameLine();
        ImGui.textColored(
            intersection.gradeSeparated() ? PluginUiColors.ACCENT_BLUE : PluginUiColors.HINT_GRAY,
            PlotI18n.tr(intersection.gradeSeparated()
                ? "plugin.road.profile_intersection_badge_grade_sep"
                : "plugin.road.profile_intersection_badge_at_grade"));
    }

    private static void renderIntersectionMetricsRow(
            RoadProfileIntersection intersection,
            RoadNetwork network,
            RoadSystemConfig config) {
        float startX = ImGui.getCursorPosX();
        float colWidth = ImGui.getContentRegionAvailX() / 3f;
        renderIntersectionMetricColumn(
            PlotI18n.tr("plugin.road.profile_intersection_stat_current_y"),
            RoadUiFormat.format(intersection.currentRoadElevation()));
        ImGui.sameLine(startX + colWidth);
        renderIntersectionMetricColumn(
            PlotI18n.tr("plugin.road.profile_intersection_stat_other_y"),
            RoadUiFormat.format(intersection.otherRoadElevation()));
        ImGui.sameLine(startX + colWidth * 2f);
        if (intersection.gradeSeparated()) {
            renderIntersectionMetricColumn(
                PlotI18n.tr("plugin.road.profile_intersection_stat_clearance"),
                PlotI18n.tr(
                    "plugin.road.profile_intersection_stat_clearance_value",
                    Math.round(intersection.clearanceGap())));
        } else {
            renderIntersectionMetricColumn(
                PlotI18n.tr("plugin.road.profile_intersection_stat_shared_y_label"),
                RoadUiFormat.format(intersection.currentRoadElevation()));
        }
    }

    private static void renderIntersectionMetricColumn(String label, String value) {
        ImGui.beginGroup();
        ImGui.textColored(PluginUiColors.HINT_GRAY, label);
        ImGui.text(value);
        ImGui.endGroup();
    }

    private static void renderIntersectionWarningsOnly(
            RoadProfileIntersection intersection,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (!intersection.gradeSeparated()) {
            return;
        }
        double requiredClearance = requiredClearanceForIntersection(network, intersection, config);
        double actualClearance = intersection.clearanceGap();
        if (actualClearance + 1e-6 < requiredClearance) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.profile_intersection_clearance_insufficient"));
        }
        if (intersection.steepGradeWarning()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.crossing_warning_steep"));
        }
    }

    private boolean renderProfileGradeSeparationControls(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileIntersection intersection,
            RoadSystemConfig config) {
        if (gradeSeparationControls == null) {
            gradeSeparationControls = new RoadGradeSeparationControls(ctx);
        }
        ProfileEditorWindowSession windowSession = host.windowSession();
        String profileRoadId = road.getId();
        if (com.plot.plugin.road.crossing.RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
            com.plot.plugin.road.crossing.RoadCrossing crossing = network.getCrossing(
                com.plot.plugin.road.crossing.RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
            if (crossing != null) {
                return gradeSeparationControls.renderCrossing(
                    crossing,
                    network,
                    config,
                    RoadGradeSeparationControls.Layout.PROFILE,
                    windowSession,
                    profileRoadId);
            }
            return false;
        }
        if (com.plot.plugin.road.crossing.RoadStackCrossingRef.isStackCrossingRef(intersection.nodeId())) {
            com.plot.plugin.road.crossing.RoadStackCrossing stack = network.getStackCrossing(
                com.plot.plugin.road.crossing.RoadStackCrossingRef.stackCrossingIdFromRef(
                    intersection.nodeId()));
            if (stack != null) {
                return gradeSeparationControls.renderStackCrossing(
                    stack, network, config, RoadGradeSeparationControls.Layout.PROFILE, windowSession);
            }
            return false;
        }
        RoadNode node = network.getNode(intersection.nodeId());
        if (node != null) {
            return gradeSeparationControls.renderLegacyJunction(
                node,
                network,
                config,
                RoadGradeSeparationControls.Layout.PROFILE,
                windowSession,
                profileRoadId);
        }
        return false;
    }

    private static double requiredClearanceForIntersection(
            RoadNetwork network,
            RoadProfileIntersection intersection,
            RoadSystemConfig config) {
        if (com.plot.plugin.road.crossing.RoadStackCrossingRef.isStackCrossingRef(intersection.nodeId())) {
            var stack = network.getStackCrossing(
                com.plot.plugin.road.crossing.RoadStackCrossingRef.stackCrossingIdFromRef(
                    intersection.nodeId()));
            return com.plot.plugin.road.crossing.GradeSeparationClearance.resolve(
                stack != null ? stack.crossingClearance() : null,
                stack != null ? network.getRoad(stack.roadId()) : null,
                config);
        }
        if (com.plot.plugin.road.crossing.RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
            com.plot.plugin.road.crossing.RoadCrossing crossing = network.getCrossing(
                com.plot.plugin.road.crossing.RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
            return RoadProfileIntersectionDragEditor.requiredClearance(crossing, network, config);
        }
        return RoadProfileIntersectionDragEditor.requiredClearance(
            network.getNode(intersection.nodeId()), network, config);
    }
}
