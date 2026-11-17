package com.plot.plugin.road.ui.profile;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileAlignmentViolationFormatter;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfileQualityStatus;
import com.plot.plugin.road.profile.ProfileQualitySummary;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.AdaptiveProfileControls;
import com.plot.plugin.road.ui.FlatElevationEditHooks;
import com.plot.plugin.road.ui.FlatElevationRecommendationUi;
import com.plot.plugin.road.ui.FlatProfileControls;
import com.plot.plugin.road.ui.ProfileEditorState;
import com.plot.plugin.road.ui.RoadLengthDisplay;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentViolation;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Objects;

/**
 * 纵断面编辑器主从详情区：控制点轨、质量告警与选中项详情。
 */
public final class VerticalProfileDetailPane {

    /** 左轨宽度占主从区比例。 */
    static final float CONTROL_RAIL_WIDTH_FRACTION = 0.28f;
    /** 下半主从区最小高度。 */
    static final float MIN_DETAIL_BODY_HEIGHT = 160f;

    /** 供详情面板注入，避免与编辑器循环依赖。 */
    public interface Host {
        ProfileEditorWindowSession windowSession();

        ProfileEditSession profileEditSession();

        ProfileEditorState editorState();

        AdaptiveProfileControls adaptiveProfileControls();

        FlatProfileControls flatProfileControls();

        VerticalProfileIntersectionPanel intersectionPanel();

        float playheadStation();

        void setPlayheadStation(float station);

        int selectedIntersectionIndex();

        void setSelectedIntersectionIndex(int index);

        void recordUndoPointBeforeEdit();

        void notifyWorkingProfileChanged();

        void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road);

        void deleteProfilePvi(RoadUiContext ctx, RoadNetwork network, Road road, int pviIndex);

        boolean isChartDerivedDataStale(Road road);

        void renderEditorLegend(
                RoadUiContext ctx,
                Road road,
                List<RoadProfileIntersection> intersections,
                boolean flatMode,
                boolean showBuildProfileLine,
                RoadProfileChartData chart);
    }

    private final Host host;

    public VerticalProfileDetailPane(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void renderControlMasterDetail(
            RoadUiContext ctx,
            RoadNetwork editingNetwork,
            Road road,
            List<ProfileControlPoint> points,
            List<RoadProfileIntersection> intersections,
            RoadProfileChartData effectiveChart,
            RoadSystemConfig config,
            boolean flatMode,
            boolean showBuildProfileLine,
            float maxGrade,
            float canvasLength) {
        ProfileEditSession profileEditSession = host.profileEditSession();
        ProfileEditorState editorState = host.editorState();
        float totalWidth = ImGui.getContentRegionAvailX();
        // 剖面图已按「剩余 − 详情高度」计算，这里吃掉全部剩余，贴住页脚。
        float bodyHeight = Math.max(MIN_DETAIL_BODY_HEIGHT, ImGui.getContentRegionAvailY());
        float spacing = ImGui.getStyle().getItemSpacingX();
        float railWidth = Math.max(120f, (totalWidth - spacing) * CONTROL_RAIL_WIDTH_FRACTION);
        float detailWidth = Math.max(160f, totalWidth - spacing - railWidth);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();

        ImGui.setCursorPos(startX, startY);
        ImGui.beginChild("##profile_control_rail", railWidth, bodyHeight, true, ImGuiWindowFlags.None);
        try {
            renderControlPointRail(ctx, editingNetwork, road, points, intersections, canvasLength, flatMode);
        } finally {
            ImGui.endChild();
        }

        ImGui.setCursorPos(startX + railWidth + spacing, startY);
        ImGui.beginChild(
            "##profile_detail_scroll",
            detailWidth,
            bodyHeight,
            true,
            ImGuiWindowFlags.AlwaysVerticalScrollbar);
        try {
            host.renderEditorLegend(
                ctx, road, intersections, flatMode, showBuildProfileLine, effectiveChart);
            renderQualityWarningsOnly(
                effectiveChart,
                road,
                config,
                host.isChartDerivedDataStale(road) || ctx.previewManager().needsPreviewRecalc());
            renderAlignmentViolations(editingNetwork, road, flatMode);
            renderFlatRoadConstraintWarnings(editingNetwork, road, intersections, flatMode);
            if (profileEditSession.isActive()
                    && !profileEditSession.constraintResult().unresolvedConstraints().isEmpty()) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.road.vertical_alignment_network_unresolved",
                    profileEditSession.constraintResult().unresolvedConstraints().size()));
            }
            if (flatMode) {
                // 水平路：内嵌 Base Y / 推荐 Y，无需退回工作区顶部。
                renderFlatModeEditorDetail(ctx, editingNetwork, road);
            }
            if (host.selectedIntersectionIndex() >= 0) {
                host.intersectionPanel().renderIntersectionDetail(
                    ctx, editingNetwork, road, intersections, config, flatMode);
            } else if (!flatMode && editorState.selectedProfilePvi >= 0) {
                host.adaptiveProfileControls().render(
                    ctx,
                    editingNetwork,
                    road,
                    config,
                    profileEditSession,
                    host.windowSession(),
                    points,
                    maxGrade,
                    editorState,
                    host::recordUndoPointBeforeEdit,
                    () -> host.propagateJunctionGrades(ctx, editingNetwork, road),
                    host::notifyWorkingProfileChanged,
                    () -> host.deleteProfilePvi(
                        ctx, editingNetwork, road, editorState.selectedProfilePvi),
                    () -> host.setSelectedIntersectionIndex(-1));
            } else if (!flatMode) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.profile_editor_detail_empty"));
            }
        } finally {
            ImGui.endChild();
        }
    }

    void renderControlPointRail(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<ProfileControlPoint> points,
            List<RoadProfileIntersection> intersections,
            float canvasLength,
            boolean flatMode) {
        ProfileEditorState editorState = host.editorState();
        var coordinates = ctx.host() != null ? ctx.host().coordinates() : null;
        ChainageDisplayContext chainage = ChainageDisplayContext.fromStart(canvasLength);

        ImGui.text(PlotI18n.tr("plugin.road.profile_editor_rail_pvi"));
        if (points.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_rail_pvi_empty"));
        } else {
            for (ProfileControlPoint point : points) {
                int pvi = point.pviIndex();
                if (flatMode && pvi > 1) {
                    continue;
                }
                ImGui.pushID("pvi_" + pvi);
                boolean selected =
                    host.selectedIntersectionIndex() < 0 && editorState.selectedProfilePvi == pvi;
                String label = formatPviRailLabel(point, chainage, network, road, coordinates);
                int stylePushes = 0;
                if (selected) {
                    ImGui.pushStyleColor(ImGuiCol.Header, PluginUiColors.ACCENT_BLUE);
                    ImGui.pushStyleColor(ImGuiCol.HeaderHovered, PluginUiColors.ACCENT_BLUE);
                    ImGui.pushStyleColor(ImGuiCol.HeaderActive, PluginUiColors.ACCENT_BLUE);
                    stylePushes = 3;
                }
                if (ImGui.selectable(label, selected, ImGuiSelectableFlags.None)) {
                    host.setSelectedIntersectionIndex(-1);
                    editorState.selectedProfilePvi = pvi;
                    host.setPlayheadStation(
                        Math.clamp((float) point.roadStation(), 0f, canvasLength));
                    host.adaptiveProfileControls().onPviSelected(editorState, point);
                }
                if (stylePushes > 0) {
                    ImGui.popStyleColor(stylePushes);
                }
                ImGui.popID();
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.profile_editor_rail_intersection"));
        if (intersections.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_rail_intersection_empty"));
            return;
        }
        for (int i = 0; i < intersections.size(); i++) {
            RoadProfileIntersection intersection = intersections.get(i);
            ImGui.pushID("xing_" + i);
            boolean selected = i == host.selectedIntersectionIndex();
            String label = formatIntersectionRailLabel(
                intersection, chainage, network, road, coordinates);
            int stylePushes = 0;
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Header, PluginUiColors.ACCENT_BLUE);
                ImGui.pushStyleColor(ImGuiCol.HeaderHovered, PluginUiColors.ACCENT_BLUE);
                ImGui.pushStyleColor(ImGuiCol.HeaderActive, PluginUiColors.ACCENT_BLUE);
                stylePushes = 3;
            }
            if (ImGui.selectable(label, selected, ImGuiSelectableFlags.None)) {
                host.setSelectedIntersectionIndex(i);
                editorState.selectedProfilePvi = -1;
                editorState.activeProfilePvi = -1;
                host.setPlayheadStation(
                    Math.clamp((float) intersection.roadStation(), 0f, canvasLength));
                host.intersectionPanel().resetHeightInputState();
            }
            if (stylePushes > 0) {
                ImGui.popStyleColor(stylePushes);
            }
            ImGui.popID();
        }
    }

    static String formatPviRailLabel(
            ProfileControlPoint point,
            ChainageDisplayContext chainage,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        String station = RoadLengthDisplay.formatChainage(
            chainage, point.roadStation(), network, road, coordinates);
        return station + "  Y=" + String.format("%.1f", point.elevation());
    }

    static String formatIntersectionRailLabel(
            RoadProfileIntersection intersection,
            ChainageDisplayContext chainage,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        String station = RoadLengthDisplay.formatChainage(
            chainage, intersection.roadStation(), network, road, coordinates);
        return intersection.otherRoadLabel() + " @ " + station;
    }

    public void selectAtPlayhead(
            List<ProfileControlPoint> points,
            List<RoadProfileIntersection> intersections,
            boolean flatMode) {
        ProfileEditorState editorState = host.editorState();
        float playheadStation = host.playheadStation();
        int bestIntersection = -1;
        double bestIntersectionDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < intersections.size(); i++) {
            double d = Math.abs(intersections.get(i).roadStation() - playheadStation);
            if (d < bestIntersectionDist) {
                bestIntersectionDist = d;
                bestIntersection = i;
            }
        }
        int bestPvi = -1;
        double bestPviDist = Double.POSITIVE_INFINITY;
        ProfileControlPoint bestPoint = null;
        for (ProfileControlPoint point : points) {
            if (flatMode && point.pviIndex() > 1) {
                continue;
            }
            double d = Math.abs(point.roadStation() - playheadStation);
            if (d < bestPviDist) {
                bestPviDist = d;
                bestPvi = point.pviIndex();
                bestPoint = point;
            }
        }
        // 优先贴交点；否则选最近 PVI（阈值约 2 格内更像“命中”）。
        if (bestIntersection >= 0 && bestIntersectionDist <= Math.max(2.0, bestPviDist)) {
            host.setSelectedIntersectionIndex(bestIntersection);
            editorState.selectedProfilePvi = -1;
            editorState.activeProfilePvi = -1;
            return;
        }
        if (bestPvi >= 0) {
            host.setSelectedIntersectionIndex(-1);
            editorState.selectedProfilePvi = bestPvi;
            if (bestPoint != null) {
                host.adaptiveProfileControls().onPviSelected(editorState, bestPoint);
            }
        }
    }

    void renderFlatModeEditorDetail(RoadUiContext ctx, RoadNetwork network, Road road) {
        host.flatProfileControls().render(
            ctx,
            network,
            road,
            host.windowSession(),
            FlatElevationEditHooks.forProfileEditor(
                host::recordUndoPointBeforeEdit,
                host::notifyWorkingProfileChanged),
            FlatElevationRecommendationUi.terrainSupplier(ctx));
    }

    void renderQualityWarningsOnly(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config,
            boolean previewStale) {
        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(chart, road, config);
        if (summary.dimensions().isEmpty() || !summary.hasWarnings()) {
            return;
        }
        // 默认折叠，有告警时标题带数量，避免详情区被状态行占满。
        String title = PlotI18n.tr(
            "plugin.road.profile_quality_collapsed",
            summary.dimensions().stream().filter(d -> d.level() == ProfileQualityStatus.Level.WARNING).count())
            + "##profile_quality_fold";
        if (ImGui.collapsingHeader(title)) {
            ProfileQualitySummary.render(chart, road, config, previewStale);
        }
    }

    void renderFlatRoadConstraintWarnings(
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> intersections,
            boolean flatMode) {
        if (!flatMode || road == null || network == null) {
            return;
        }
        if (FlatVerticalIntentSupport.hasLocalJunctionBends(road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.profile_flat_local_bend_warning"));
        }
        if (intersections == null || intersections.size() < 2) {
            return;
        }
        double reference = intersections.getFirst().currentRoadElevation();
        for (RoadProfileIntersection intersection : intersections) {
            if (Math.abs(intersection.currentRoadElevation() - reference) > 0.05) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.profile_flat_multi_junction_conflict"));
                return;
            }
        }
    }

    void renderAlignmentViolations(RoadNetwork network, Road road, boolean flatMode) {
        if (flatMode || road == null || network == null) {
            return;
        }
        RoadVerticalAlignment alignment = host.profileEditSession().effectiveAlignment(road);
        if (alignment == null || alignment.isEmpty()) {
            return;
        }
        double roadLength = RoadStationing.canonicalLength(network, road);
        List<VerticalAlignmentViolation> violations =
            VerticalAlignmentValidator.validate(alignment, roadLength);
        if (violations.isEmpty()) {
            return;
        }
        ImGui.spacing();
        // 默认折叠：打开编辑器时优先保证剖面图与选中项参数可见。
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.profile_alignment_issues", violations.size())
                    + "##profile_alignment_issues")) {
            for (VerticalAlignmentViolation violation : violations) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    ProfileAlignmentViolationFormatter.format(violation));
            }
        }
    }

    public static void renderWaterConstraintWarning(RoadProfileChartData chartData) {
        if (chartData == null || chartData.waterConstraintFeasible()) {
            return;
        }
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.profile_water_constraint_infeasible"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_water_constraint_infeasible_hint"));
    }

    public static void renderManualEndpointConstraintWarning(RoadProfileChartData chartData) {
        if (chartData == null || chartData.manualEndpointConstraintFeasible()) {
            return;
        }
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.profile_endpoint_constraint_infeasible"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_endpoint_constraint_infeasible_hint"));
    }

    public static int clampFlatEndpointPvi(int pviIndex) {
        return pviIndex >= 0 && pviIndex <= 1 ? pviIndex : -1;
    }
}
