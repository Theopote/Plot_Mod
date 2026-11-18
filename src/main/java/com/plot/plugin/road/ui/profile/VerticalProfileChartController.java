package com.plot.plugin.road.ui.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileChartInteraction;
import com.plot.plugin.road.profile.ProfileChartLegend;
import com.plot.plugin.road.profile.ProfileChartRenderMode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.ProfileEditorState;
import com.plot.plugin.road.ui.RoadLengthDisplay;
import com.plot.plugin.road.ui.RoadStationPlanSchematicRenderer;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiFormat;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 纵断面图表交互：平面示意、播放头、图例、PVI 插入/删除与曲线手柄。
 */
public final class VerticalProfileChartController {

    /** 顶部旧布局用的示意高度；侧栏布局改按可用高度填充。 */
    private static final float PLAN_SCHEMATIC_HEIGHT = 96f;

    /** 供图表控制器注入，避免与编辑器循环依赖。 */
    public interface Host {
        ProfileEditorWindowSession windowSession();

        ProfileEditSession profileEditSession();

        ProfileEditorState editorState();

        float playheadStation();

        void setPlayheadStation(float station);

        int selectedIntersectionIndex();

        void selectAtPlayhead(
                List<ProfileControlPoint> points,
                List<RoadProfileIntersection> intersections,
                boolean flatMode);

        void recordUndoPointBeforeEdit();

        void notifyWorkingProfileChanged();

        void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road);

        RoadElevationBounds resolveElevationBounds(RoadUiContext ctx);

        double resolveBootstrapEndpointElevation(
                RoadNetwork network,
                Road road,
                RoadProfileChartData chartData,
                double station);

        boolean isChartDerivedDataStale(Road road);
    }

    private final Host host;
    private final imgui.type.ImFloat pviHeightInput = new imgui.type.ImFloat();

    public VerticalProfileChartController(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    /** 顶部仅保留桩号滑动条；平面示意见 {@link #renderPlanSchematic}. */
    public void renderPlayheadScrubBar(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<ProfileControlPoint> points,
            List<RoadProfileIntersection> intersections,
            float canvasLength,
            boolean flatMode) {
        renderPlayheadScrub(ctx, network, road, canvasLength, points, intersections, flatMode);
    }

    /**
     * 道路平面示意（供主从区右侧栏）。
     *
     * @param previewHeight 示意画布高度；≤0 时按内容区剩余高度估算
     */
    public void renderPlanSchematic(
            RoadNetwork network,
            Road road,
            List<ProfileControlPoint> points,
            List<RoadProfileIntersection> intersections,
            float canvasLength,
            boolean flatMode,
            float previewHeight) {
        ProfileEditorState editorState = host.editorState();
        List<RoadStationPlanSchematicRenderer.StationMarker> markers = new ArrayList<>();
        for (ProfileControlPoint point : points) {
            markers.add(new RoadStationPlanSchematicRenderer.StationMarker(
                (float) point.roadStation(),
                point.pviIndex() == editorState.selectedProfilePvi));
        }
        for (int i = 0; i < intersections.size(); i++) {
            markers.add(new RoadStationPlanSchematicRenderer.StationMarker(
                (float) intersections.get(i).roadStation(),
                i == host.selectedIntersectionIndex()));
        }
        float height = previewHeight > 1f
            ? previewHeight
            : Math.max(
                PLAN_SCHEMATIC_HEIGHT,
                ImGui.getContentRegionAvailY() - ImGui.getTextLineHeightWithSpacing() - 4f);
        Float clicked = RoadStationPlanSchematicRenderer.render(
            network,
            road,
            host.playheadStation(),
            canvasLength,
            markers,
            List.of(),
            height);
        if (clicked != null) {
            host.setPlayheadStation(Math.clamp(clicked, 0f, canvasLength));
            host.selectAtPlayhead(points, intersections, flatMode);
        }
    }

    void renderPlayheadScrub(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            float canvasLength,
            List<ProfileControlPoint> points,
            List<RoadProfileIntersection> intersections,
            boolean flatMode) {
        var coordinates = ctx.host() != null ? ctx.host().coordinates() : null;
        float worldMax = (float) Math.max(
            0.001,
            RoadLengthDisplay.canvasToBlocks(canvasLength, network, road, coordinates));
        float worldPlayhead = (float) RoadLengthDisplay.canvasToBlocks(
            host.playheadStation(), network, road, coordinates);
        worldPlayhead = Math.clamp(worldPlayhead, 0f, worldMax);
        float[] station = {worldPlayhead};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat("##profile_playhead", station, 0f, worldMax, RoadUiFormat.SLIDER_METERS);
        boolean sliderActive = ImGui.isItemActive();
        float playhead = (float) RoadLengthDisplay.blocksToCanvas(
            station[0], network, road, coordinates);
        host.setPlayheadStation(Math.clamp(playhead, 0f, canvasLength));
        if (sliderActive) {
            host.selectAtPlayhead(points, intersections, flatMode);
        }
        ImGui.spacing();
    }

    public void renderEditorLegend(
            RoadUiContext ctx,
            Road road,
            List<RoadProfileIntersection> intersections,
            boolean flatMode,
            boolean showBuildProfileLine,
            RoadProfileChartData chart) {
        ProfileChartLegend.renderCompact(
            ProfileChartRenderMode.EDITOR,
            road != null ? road.getVerticalMode() : null,
            flatMode,
            host.isChartDerivedDataStale(road) || ctx.previewManager().needsPreviewRecalc(),
            showBuildProfileLine,
            chart,
            intersections);
    }

    public void renderMissingProfileActions(RoadUiContext ctx, RoadNetwork network, Road road) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.terrain_profile.missing_hint"));
        if (road != null
                && ImGui.button(PlotI18n.tr("plugin.road.terrain_profile.read_update") + "##editor_missing_terrain")) {
            ctx.previewManager().refreshTerrainProfile(network, road.getId(), true);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_calculate_profile"))) {
            ctx.previewManager().calculateProfileSamplingOnly(network);
        }
    }

    public void updateProfileCurveHandleDraft(ProfileChartInteraction.ControlInteraction interaction) {
        if (interaction.draggedCurveLength() == null || interaction.activeCurveHandlePvi() < 0) {
            return;
        }
        host.profileEditSession().updateCurveLength(interaction.draggedCurveLength());
    }

    public void applyProfilePointInsert(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            ProfileChartInteraction.ControlInteraction interaction) {
        if (!interaction.addPointRequested()
                || interaction.addPointLocalDistance() == null
                || interaction.addPointElevation() == null) {
            return;
        }
        host.recordUndoPointBeforeEdit();
        double roadLength = RoadStationing.canonicalLength(network, road);
        double insertStation = interaction.addPointLocalDistance();
        double startElevation = host.resolveBootstrapEndpointElevation(network, road, chartData, 0.0);
        double endElevation = host.resolveBootstrapEndpointElevation(
            network, road, chartData, roadLength);
        RoadElevationBounds bounds = host.resolveElevationBounds(ctx);
        double insertElevation = bounds.clamp(interaction.addPointElevation());
        VerticalProfileControlPoints.InsertResult result = VerticalProfileControlPoints.tryBootstrapOrInsert(
            road.getVerticalAlignment(),
            roadLength,
            startElevation,
            endElevation,
            insertStation,
            insertElevation);
        if (!result.success()) {
            ctx.status().warning(insertFailureMessage(result.reason()));
            return;
        }
        road.setVerticalAlignment(result.alignment());
        com.plot.plugin.road.vertical.ProfileSourceEdits.markWorkingCopyEdited(road);
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (windowSession != null) {
            windowSession.markTouched(road);
        }
        host.editorState().selectedProfilePvi = findNearestPviIndex(result.alignment(), insertStation);
        host.propagateJunctionGrades(ctx, network, road);
        host.notifyWorkingProfileChanged();
    }

    public void renderProfileInteractionHint() {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_editor_interaction_hint_short"));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_editor_interaction_hint"));
        }
    }

    public void renderProfilePviContextMenu(RoadUiContext ctx, RoadNetwork network, Road road) {
        ProfileEditorState editorState = host.editorState();
        if (editorState.contextMenuPvi < 0) {
            return;
        }
        if (!ImGui.beginPopup("##road_profile_pvi_context")) {
            return;
        }
        int pviIndex = editorState.contextMenuPvi;
        var alignment = road.getVerticalAlignment();
        if (alignment == null || pviIndex >= alignment.pviCount()) {
            ImGui.endPopup();
            return;
        }
        var point = alignment.getPvis().get(pviIndex);
        var profilePoint = VerticalProfileControlPoints.forRoad(network, road).stream()
            .filter(control -> control.pviIndex() == pviIndex)
            .findFirst()
            .orElse(null);
        var bounds = host.resolveElevationBounds(ctx);
        // This popup edits the drawn PVI (tangent vertex), not a different point on its curve.
        if (ImGui.isWindowAppearing()) {
            pviHeightInput.set((float) point.getElevation());
        }
        boolean locked =
            point.getConstraint() == com.plot.plugin.road.vertical.VerticalControlPointConstraint.USER_LOCKED;
        boolean elevationEditable = profilePoint != null && profilePoint.elevationEditable();
        ImGui.beginDisabled(locked || !elevationEditable);
        ImGui.inputFloat(PlotI18n.tr("plugin.road.profile_pvi_elevation_input"), pviHeightInput, 0.1f, 1f, "%.2f");
        ImGui.textDisabled(PlotI18n.tr(
            "plugin.road.profile_pvi_elevation_range",
            RoadUiFormat.format(bounds.minY()),
            RoadUiFormat.format(bounds.maxY())));
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_apply_height"))
                && Float.isFinite(pviHeightInput.get())) {
            host.recordUndoPointBeforeEdit();
            ProfileEditSession profileEditSession = host.profileEditSession();
            profileEditSession.setElevationBounds(bounds);
            profileEditSession.beginNumericEdit(road);
            profileEditSession.updatePviElevation(network, road, pviIndex, bounds.clamp(pviHeightInput.get()));
            profileEditSession.commitEdit(
                network, road, ctx.networkManager().getConfig(), host.windowSession(), null,
                host::notifyWorkingProfileChanged);
            pviHeightInput.set((float) bounds.clamp(pviHeightInput.get()));
            ctx.requestOverlayRefresh();
        }
        ImGui.endDisabled();
        if (profilePoint != null && profilePoint.endpoint()) {
            ImGui.textWrapped(PlotI18n.tr("plugin.road.profile_pvi_endpoint_hint"));
        } else if (!elevationEditable && profilePoint != null) {
            String hintKey = profilePoint.sharedJunction()
                ? "plugin.road.profile_pvi_shared_junction_hint"
                : "plugin.road.profile_pvi_junction_fixed_hint";
            ImGui.textWrapped(PlotI18n.tr(hintKey));
        }
        ImGui.separator();
        boolean canDelete = canDeleteProfilePvi(network, road, pviIndex);
        if (profilePoint != null && profilePoint.endpoint()) {
            ImGui.textWrapped(PlotI18n.tr("plugin.road.profile_pvi_delete_endpoint"));
        } else if (!canDelete) {
            ImGui.textWrapped(PlotI18n.tr(
                VerticalProfileControlPoints.deleteDisabledMessageKey(network, road, pviIndex)));
        } else if (ImGui.menuItem(PlotI18n.tr("plugin.road.profile_pvi_delete"))) {
            deleteProfilePvi(ctx, network, road, pviIndex);
        }
        ImGui.endPopup();
    }

    static boolean canDeleteProfilePvi(RoadNetwork network, Road road, int pviIndex) {
        return VerticalProfileControlPoints.canDelete(network, road, pviIndex);
    }

    public void deleteProfilePvi(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            int pviIndex) {
        if (!canDeleteProfilePvi(network, road, pviIndex)) {
            return;
        }
        host.recordUndoPointBeforeEdit();
        road.setVerticalAlignment(VerticalProfileControlPoints.removeAt(
            road.getVerticalAlignment(), pviIndex));
        com.plot.plugin.road.vertical.ProfileSourceEdits.markWorkingCopyEdited(road);
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (windowSession != null) {
            windowSession.markTouched(road);
        }
        ProfileEditorState editorState = host.editorState();
        editorState.selectedProfilePvi = -1;
        editorState.activeProfilePvi = -1;
        editorState.contextMenuPvi = -1;
        host.propagateJunctionGrades(ctx, network, road);
        host.notifyWorkingProfileChanged();
    }

    static String insertFailureMessage(VerticalProfileControlPoints.InsertFailureReason reason) {
        if (reason == null) {
            return PlotI18n.tr("plugin.road.profile_pvi_insert_failed");
        }
        return switch (reason) {
            case INSUFFICIENT_SPACE -> PlotI18n.tr("plugin.road.profile_pvi_insert_insufficient_space");
            case DUPLICATE_STATION, TOO_CLOSE_TO_NEIGHBOR ->
                PlotI18n.tr("plugin.road.profile_pvi_insert_too_close");
            case INVALID_INPUT -> PlotI18n.tr("plugin.road.profile_pvi_insert_invalid");
            case ROAD_TOO_SHORT -> PlotI18n.tr("plugin.road.profile_pvi_insert_road_too_short");
        };
    }

    public static int resolveCurveHandlePviIndex(
            Road road,
            RoadVerticalAlignment alignment,
            int selectedPvi,
            int selectedIntersection,
            List<RoadProfileIntersection> intersections) {
        if (road == null || alignment == null) {
            return -1;
        }
        if (selectedPvi > 0 && selectedPvi < alignment.pviCount() - 1
                && VerticalProfileControlPoints.isCurveEditable(road, alignment, selectedPvi)) {
            return selectedPvi;
        }
        if (selectedIntersection >= 0 && intersections != null
                && selectedIntersection < intersections.size()) {
            int atStation = findPviIndexAtStation(
                alignment, intersections.get(selectedIntersection).roadStation());
            if (atStation > 0 && atStation < alignment.pviCount() - 1
                    && VerticalProfileControlPoints.isCurveEditable(road, alignment, atStation)) {
                return atStation;
            }
            double station = intersections.get(selectedIntersection).roadStation();
            for (int i = 1; i < alignment.pviCount() - 1; i++) {
                var pvi = alignment.getPvis().get(i);
                if (pvi.hasCurve() && Math.abs(station - pvi.getStation()) <= pvi.getCurveLength() / 2
                        && VerticalProfileControlPoints.isCurveEditable(road, alignment, i)) {
                    return i;
                }
            }
        }
        return selectedPvi;
    }

    public static int findPviIndexAtStation(RoadVerticalAlignment alignment, double station) {
        if (alignment == null) {
            return -1;
        }
        for (int i = 0; i < alignment.pviCount(); i++) {
            if (Math.abs(alignment.getPvis().get(i).getStation() - station) <= 0.26) {
                return i;
            }
        }
        return -1;
    }

    public static List<ProfileChartInteraction.CurveHandle> buildRoadCurveHandles(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment,
            List<ProfileControlPoint> points,
            int selectedPvi) {
        if (road == null || alignment == null || selectedPvi <= 0) {
            return List.of();
        }
        if (selectedPvi >= alignment.pviCount() - 1) {
            return List.of();
        }
        ProfileControlPoint selected = points.stream()
            .filter(point -> point.pviIndex() == selectedPvi)
            .findFirst()
            .orElse(null);
        if (selected == null || !VerticalProfileControlPoints.isCurveEditable(road, alignment, selectedPvi)) {
            return List.of();
        }
        PointOfVerticalIntersection pvi = alignment.getPvis().get(selectedPvi);
        double half = pvi.hasCurve() ? pvi.getCurveLength() * 0.5 : 4.0;
        double bvcStation = pvi.getStation() - half;
        double evcStation = pvi.getStation() + half;
        double bvcElevation = VerticalAlignmentGeometry
            .elevationAt(alignment, bvcStation)
            .orElse(pvi.getElevation());
        double evcElevation = VerticalAlignmentGeometry
            .elevationAt(alignment, evcStation)
            .orElse(pvi.getElevation());
        return List.of(
            new ProfileChartInteraction.CurveHandle(
                selectedPvi, bvcStation, bvcElevation, true),
            new ProfileChartInteraction.CurveHandle(
                selectedPvi, evcStation, evcElevation, false));
    }

    static int findNearestPviIndex(RoadVerticalAlignment alignment, double station) {
        if (alignment == null || alignment.pviCount() == 0) {
            return -1;
        }
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < alignment.pviCount(); i++) {
            double distance = Math.abs(alignment.getPvis().get(i).getStation() - station);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
    }
}
