package com.plot.plugin.road.ui.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileChartInteraction;
import com.plot.plugin.road.profile.ProfileChartRenderMode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.profile.ProfileRenderCache;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileChartRenderer;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.AdaptiveProfileControls;
import com.plot.plugin.road.ui.FlatProfileControls;
import com.plot.plugin.road.ui.ProfileEditorState;
import com.plot.plugin.road.ui.RoadLengthDisplay;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import imgui.ImGui;

import java.util.List;
import java.util.Objects;

/**
 * 纵断面编辑器交互主体：钉顶图表、播放头与主从详情编排。
 */
public final class VerticalProfileEditorBody {

    private static final float MIN_EDITOR_CHART_HEIGHT = 200f;
    private static final float MAX_DETAIL_BODY_HEIGHT = 220f;

    /** 供主体注入，避免与编辑器循环依赖。 */
    public interface Host {
        ProfileEditorWindowSession windowSession();

        ProfileEditSession profileEditSession();

        ProfileEditorState editorState();

        AdaptiveProfileControls adaptiveProfileControls();

        FlatProfileControls flatProfileControls();

        VerticalProfileChartCache chartCache();

        VerticalProfileVoxelPreview voxelPreview();

        VerticalProfileIntersectionPanel intersectionPanel();

        float playheadStation();

        void setPlayheadStation(float station);

        int selectedIntersectionIndex();

        void setSelectedIntersectionIndex(int index);

        int activeIntersectionDragIndex();

        void setActiveIntersectionDragIndex(int index);

        ProfileChartInteraction.IntersectionDragTarget activeIntersectionDragTarget();

        void setActiveIntersectionDragTarget(ProfileChartInteraction.IntersectionDragTarget target);

        void recordUndoPointBeforeEdit();

        void notifyWorkingProfileChanged();

        void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road);

        void ensureEditorRoadAlignment(
                RoadUiContext ctx,
                RoadNetwork network,
                Road road,
                RoadProfileChartData chartData);

        FlatElevationProfileOverlay resolveFlatOverlay(Road road);

        ProfileRenderCache resolveRenderCache(
                RoadUiContext ctx,
                RoadNetwork network,
                Road road,
                RoadProfileChartData chartData,
                FlatElevationProfileOverlay flatOverlay,
                List<RoadProfileIntersection> intersections,
                VoxelProfilePreviewSlot voxelSlot);

        RoadProfileChartData resolveChartData(RoadUiContext ctx, RoadNetwork network, Road road);

        List<RoadProfileIntersection> resolveIntersections(
                RoadUiContext ctx,
                RoadNetwork network,
                Road road,
                RoadSystemConfig config,
                RoadProfileChartData chart,
                boolean allowCache);

        RoadElevationBounds resolveElevationBounds(RoadUiContext ctx);

        RoadProfileChartData resolveEffectiveChartData(Road road, RoadProfileChartData base);

        boolean isVoxelBuildProfileStale(Road road);

        boolean isChartDerivedDataStale(Road road);

        double resolveBootstrapEndpointElevation(
                RoadNetwork network,
                Road road,
                RoadProfileChartData chartData,
                double station);
    }

    private final Host host;
    private final VerticalProfileDetailPane detailPane;
    private final VerticalProfileChartController chartController;

    /** 图例快照：详情区不再画图例，改在编辑区正下方单独一行渲染。 */
    private List<RoadProfileIntersection> footerLegendIntersections = List.of();
    private boolean footerLegendFlatMode;
    private boolean footerLegendShowBuild;
    private RoadProfileChartData footerLegendChart;
    private boolean footerLegendReady;

    public VerticalProfileEditorBody(Host host) {
        this.host = Objects.requireNonNull(host, "host");
        this.detailPane = new VerticalProfileDetailPane(new VerticalProfileDetailPane.Host() {
            @Override
            public ProfileEditorWindowSession windowSession() {
                return host.windowSession();
            }

            @Override
            public ProfileEditSession profileEditSession() {
                return host.profileEditSession();
            }

            @Override
            public ProfileEditorState editorState() {
                return host.editorState();
            }

            @Override
            public AdaptiveProfileControls adaptiveProfileControls() {
                return host.adaptiveProfileControls();
            }

            @Override
            public FlatProfileControls flatProfileControls() {
                return host.flatProfileControls();
            }

            @Override
            public VerticalProfileIntersectionPanel intersectionPanel() {
                return host.intersectionPanel();
            }

            @Override
            public float playheadStation() {
                return host.playheadStation();
            }

            @Override
            public void setPlayheadStation(float station) {
                host.setPlayheadStation(station);
            }

            @Override
            public int selectedIntersectionIndex() {
                return host.selectedIntersectionIndex();
            }

            @Override
            public void setSelectedIntersectionIndex(int index) {
                host.setSelectedIntersectionIndex(index);
            }

            @Override
            public void recordUndoPointBeforeEdit() {
                host.recordUndoPointBeforeEdit();
            }

            @Override
            public void notifyWorkingProfileChanged() {
                host.notifyWorkingProfileChanged();
            }

            @Override
            public void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road) {
                host.propagateJunctionGrades(ctx, network, road);
            }

            @Override
            public void deleteProfilePvi(
                    RoadUiContext ctx, RoadNetwork network, Road road, int pviIndex) {
                chartController.deleteProfilePvi(ctx, network, road, pviIndex);
            }

            @Override
            public boolean isChartDerivedDataStale(Road road) {
                return host.isChartDerivedDataStale(road);
            }

            @Override
            public void renderPlanSchematicPane(
                    RoadNetwork network,
                    Road road,
                    List<ProfileControlPoint> points,
                    List<RoadProfileIntersection> intersections,
                    float canvasLength,
                    boolean flatMode,
                    float previewHeight) {
                chartController.renderPlanSchematic(
                    network, road, points, intersections, canvasLength, flatMode, previewHeight);
            }
        });
        this.chartController = new VerticalProfileChartController(new VerticalProfileChartController.Host() {
            @Override
            public ProfileEditorWindowSession windowSession() {
                return host.windowSession();
            }

            @Override
            public ProfileEditSession profileEditSession() {
                return host.profileEditSession();
            }

            @Override
            public ProfileEditorState editorState() {
                return host.editorState();
            }

            @Override
            public float playheadStation() {
                return host.playheadStation();
            }

            @Override
            public void setPlayheadStation(float station) {
                host.setPlayheadStation(station);
            }

            @Override
            public int selectedIntersectionIndex() {
                return host.selectedIntersectionIndex();
            }

            @Override
            public void selectAtPlayhead(
                    List<ProfileControlPoint> points,
                    List<RoadProfileIntersection> intersections,
                    boolean flatMode) {
                detailPane.selectAtPlayhead(points, intersections, flatMode);
            }

            @Override
            public void recordUndoPointBeforeEdit() {
                host.recordUndoPointBeforeEdit();
            }

            @Override
            public void notifyWorkingProfileChanged() {
                host.notifyWorkingProfileChanged();
            }

            @Override
            public void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road) {
                host.propagateJunctionGrades(ctx, network, road);
            }

            @Override
            public RoadElevationBounds resolveElevationBounds(RoadUiContext ctx) {
                return host.resolveElevationBounds(ctx);
            }

            @Override
            public double resolveBootstrapEndpointElevation(
                    RoadNetwork network,
                    Road road,
                    RoadProfileChartData chartData,
                    double station) {
                return host.resolveBootstrapEndpointElevation(network, road, chartData, station);
            }

            @Override
            public boolean isChartDerivedDataStale(Road road) {
                return host.isChartDerivedDataStale(road);
            }
        });
    }

    public void renderMissingProfileActions(RoadUiContext ctx, RoadNetwork network, Road road) {
        footerLegendReady = false;
        chartController.renderMissingProfileActions(ctx, network, road);
    }

    /** 编辑区下方图例行：有交互剖面时占一行。 */
    public void renderFooterLegend(RoadUiContext ctx, Road road) {
        if (!footerLegendReady || road == null) {
            return;
        }
        chartController.renderEditorLegend(
            ctx,
            road,
            footerLegendIntersections,
            footerLegendFlatMode,
            footerLegendShowBuild,
            footerLegendChart);
    }

    public void clearFooterLegend() {
        footerLegendReady = false;
    }

    /**
     * 钉顶剖面高度：窗口变高时优先拉高图表；下半主从区限制在
     * {@link VerticalProfileDetailPane#MIN_DETAIL_BODY_HEIGHT}–{@link #MAX_DETAIL_BODY_HEIGHT}，
     * 避免按钮上方大块空白。
     */
    static float resolvePinnedChartHeight() {
        float remaining = ImGui.getContentRegionAvailY();
        float spacing = ImGui.getStyle().getItemSpacingY();
        float detail = resolveDetailBodyHeight(remaining);
        float chart = remaining - detail - spacing;
        return Math.max(MIN_EDITOR_CHART_HEIGHT, chart);
    }

    static float resolveDetailBodyHeight(float remainingAfterChartAnchor) {
        float spacing = ImGui.getStyle().getItemSpacingY();
        float maxForChart = Math.max(
            0f, remainingAfterChartAnchor - MIN_EDITOR_CHART_HEIGHT - spacing);
        float desired = Math.clamp(
            remainingAfterChartAnchor * 0.32f,
            VerticalProfileDetailPane.MIN_DETAIL_BODY_HEIGHT,
            MAX_DETAIL_BODY_HEIGHT);
        return Math.min(
            desired,
            Math.max(VerticalProfileDetailPane.MIN_DETAIL_BODY_HEIGHT, maxForChart));
    }

    public void renderInteractiveEditor(
            RoadUiContext ctx,
            RoadNetwork liveNetwork,
            RoadNetwork editingNetwork,
            Road road,
            RoadProfileChartData chartData,
            VerticalAlignmentProfileOverlay design,
            FlatElevationProfileOverlay flatOverlay) {
        ProfileEditSession profileEditSession = host.profileEditSession();
        ProfileEditorState editorState = host.editorState();
        ProfileEditorWindowSession windowSession = host.windowSession();
        VerticalProfileVoxelPreview voxelPreview = host.voxelPreview();
        footerLegendReady = false;

        RoadSystemConfig config = ctx.networkManager().getConfig();
        boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
        host.ensureEditorRoadAlignment(ctx, editingNetwork, road, chartData);
        RoadNetwork displayNetwork = profileEditSession.previewNetwork(editingNetwork, road, config);
        Road displayRoad = displayNetwork.getRoad(road.getId());
        RoadVerticalAlignment effectiveAlignment =
            profileEditSession.effectiveAlignment(displayNetwork, displayRoad);
        design = profileEditSession.effectiveDesignOverlay(displayNetwork, displayRoad).orElse(null);
        RoadProfileChartData effectiveChart = host.resolveEffectiveChartData(road, chartData);
        boolean buildProfileStale = host.isChartDerivedDataStale(road);
        // 与生成页缩略图一致：始终可显示预览拟合建造线；有体素结果时用台阶采样覆盖。
        boolean showBuildProfileLine = ProfileChartRenderMode.EDITOR.showBuildProfileLine();
        List<ProfileControlPoint> points =
            VerticalProfileControlPoints.forAlignment(displayNetwork, displayRoad, effectiveAlignment);
        float maxGrade = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : ctx.networkManager().getConfig().getMaxSlope();
        List<RoadProfileIntersection> baseIntersections = host.resolveIntersections(
            ctx, displayNetwork, displayRoad, config, effectiveChart,
            !profileEditSession.isActive() && host.activeIntersectionDragIndex() < 0);
        List<RoadProfileIntersection> intersections =
            profileEditSession.effectiveIntersections(displayNetwork, displayRoad, baseIntersections);
        ProfileRenderCache renderCache = host.resolveRenderCache(
            ctx, liveNetwork, road, effectiveChart, flatOverlay, intersections,
            windowSession != null ? windowSession.voxelPreview(road.getId()) : null);
        float canvasLength = (float) Math.max(
            0.001,
            RoadStationing.canonicalLength(editingNetwork, road));
        host.setPlayheadStation(Math.clamp(host.playheadStation(), 0f, canvasLength));
        // 滑动条留在剖面图上方；平面示意移到下方三栏右侧。
        chartController.renderPlayheadScrubBar(
            ctx, editingNetwork, road, points, intersections, canvasLength, flatMode);
        int chartSelectedPvi = flatMode
            ? VerticalProfileDetailPane.clampFlatEndpointPvi(editorState.selectedProfilePvi)
            : editorState.selectedProfilePvi;
        int chartActivePvi = flatMode
            ? VerticalProfileDetailPane.clampFlatEndpointPvi(editorState.activeProfilePvi)
            : editorState.activeProfilePvi;
        int curveHandlePvi = flatMode
            ? -1
            : VerticalProfileChartController.resolveCurveHandlePviIndex(
                road,
                effectiveAlignment,
                chartSelectedPvi,
                host.selectedIntersectionIndex(),
                intersections);
        List<ProfileChartInteraction.CurveHandle> curveHandles = flatMode
            ? List.of()
            : VerticalProfileChartController.buildRoadCurveHandles(
                editingNetwork, road, effectiveAlignment, points, curveHandlePvi);
        boolean hasSelection =
            host.selectedIntersectionIndex() >= 0
                || (!flatMode && editorState.selectedProfilePvi >= 0);
        if (!flatMode && !hasSelection) {
            chartController.renderProfileInteractionHint();
        }
        VerticalProfileDetailPane.renderManualEndpointConstraintWarning(effectiveChart);
        VerticalProfileDetailPane.renderWaterConstraintWarning(effectiveChart);
        RoadElevationBounds elevationBounds = host.resolveElevationBounds(ctx);
        profileEditSession.setElevationBounds(elevationBounds);
        // 提示文案之后再算高度，保证图表 + 详情贴满到页脚。
        float chartHeight = resolvePinnedChartHeight();
        var coordinates = ctx.host() != null ? ctx.host().coordinates() : null;
        double stationDisplayScale = RoadLengthDisplay.canvasToWorldScale(
            editingNetwork, road, coordinates);
        ProfileChartInteraction.ControlInteraction interaction =
            RoadProfileChartRenderer.renderInteractive(
                effectiveChart, design, points, chartSelectedPvi, chartActivePvi, maxGrade,
                intersections, host.selectedIntersectionIndex(), chartHeight,
                host.activeIntersectionDragIndex(), host.activeIntersectionDragTarget(), flatOverlay,
                curveHandles,
                editorState.pendingProfilePvi,
                editorState.pendingClickX,
                editorState.pendingClickY,
                editorState.activeCurveHandlePvi,
                editorState.activeCurveHandle,
                ProfileChartRenderMode.EDITOR,
                renderCache,
                road.getVerticalMode(),
                elevationBounds,
                effectiveAlignment,
                buildProfileStale,
                showBuildProfileLine,
                (double) host.playheadStation(),
                stationDisplayScale);
        if (interaction.intersectionDragStarted()) {
            voxelPreview.cancel();
            host.recordUndoPointBeforeEdit();
            if (flatMode) {
                profileEditSession.beginFlatWholeLineEdit(
                    editingNetwork, road, baseIntersections);
            } else {
                profileEditSession.beginIntersectionEdit(baseIntersections);
            }
        }
        if (interaction.curveHandleDragStarted()) {
            voxelPreview.cancel();
            host.recordUndoPointBeforeEdit();
            profileEditSession.beginCurveEdit(road, interaction.activeCurveHandlePvi());
        }
        if (interaction.dragStarted()) {
            voxelPreview.cancel();
            host.recordUndoPointBeforeEdit();
            if (flatMode) {
                profileEditSession.beginFlatWholeLineEdit(
                    editingNetwork, road, baseIntersections);
            } else {
                profileEditSession.beginPviEdit(road);
            }
        }
        if (flatMode) {
            if (interaction.selectedPviIndex() >= 0 && interaction.selectedPviIndex() <= 1) {
                editorState.selectedProfilePvi = interaction.selectedPviIndex();
                host.setSelectedIntersectionIndex(-1);
            }
            if (interaction.activePviIndex() >= 0 && interaction.activePviIndex() <= 1) {
                editorState.activeProfilePvi = interaction.activePviIndex();
            }
            editorState.pendingProfilePvi = interaction.pendingPviIndex();
            editorState.pendingClickX = interaction.pendingClickX();
            editorState.pendingClickY = interaction.pendingClickY();
            if (interaction.draggedElevation() != null
                    && interaction.activePviIndex() >= 0
                    && interaction.activePviIndex() <= 1) {
                profileEditSession.updateFlatRoadWholeLineDrag(
                    editingNetwork,
                    road,
                    config,
                    0,
                    RoadProfileIntersectionDragEditor.DragTarget.CURRENT,
                    interaction.draggedElevation(),
                    baseIntersections);
            }
        } else {
            if (interaction.selectedPviIndex() >= 0
                    && interaction.selectedPviIndex() != editorState.selectedProfilePvi) {
                points.stream()
                    .filter(point -> point.pviIndex() == interaction.selectedPviIndex())
                    .findFirst()
                    .ifPresent(point -> host.adaptiveProfileControls().onPviSelected(editorState, point));
            }
            editorState.selectedProfilePvi = interaction.selectedPviIndex();
            editorState.activeProfilePvi = interaction.activePviIndex();
            editorState.pendingProfilePvi = interaction.pendingPviIndex();
            editorState.pendingClickX = interaction.pendingClickX();
            editorState.pendingClickY = interaction.pendingClickY();
            if (interaction.contextMenuPviIndex() >= 0) {
                editorState.contextMenuPvi = interaction.contextMenuPviIndex();
            }
            chartController.applyProfilePointInsert(ctx, editingNetwork, road, chartData, interaction);
            chartController.updateProfileCurveHandleDraft(interaction);
            chartController.renderProfilePviContextMenu(ctx, editingNetwork, road);
        }
        if (interaction.activeIntersectionDragIndex() >= 0) {
            host.setActiveIntersectionDragIndex(interaction.activeIntersectionDragIndex());
            host.setActiveIntersectionDragTarget(interaction.activeIntersectionDragTarget());
        } else if (!ImGui.isMouseDown(0)) {
            host.setActiveIntersectionDragIndex(-1);
            host.setActiveIntersectionDragTarget(
                ProfileChartInteraction.IntersectionDragTarget.NONE);
        }
        if (interaction.intersectionDragFinished() || interaction.dragFinished()) {
            editorState.activeProfilePvi = -1;
            editorState.pendingProfilePvi = -1;
        }
        host.intersectionPanel().applyIntersectionDrag(
            ctx, editingNetwork, road, config, flatMode, baseIntersections, interaction);
        intersections = profileEditSession.effectiveIntersections(editingNetwork, road, baseIntersections);
        if (flatMode && profileEditSession.isActive()) {
            effectiveAlignment = profileEditSession.effectiveAlignment(editingNetwork, road);
            design = profileEditSession.effectiveDesignOverlay(editingNetwork, road).orElse(design);
        }
        if (!flatMode
                && interaction.draggedElevation() != null && interaction.draggedLocalDistance() != null
                && editorState.selectedProfilePvi >= 0
                && effectiveAlignment != null
                && editorState.selectedProfilePvi < effectiveAlignment.pviCount()) {
            editorState.selectedProfileElevation[0] = interaction.draggedElevation().floatValue();
            double currentStation = effectiveAlignment.getPvis()
                .get(editorState.selectedProfilePvi).getStation();
            double requestedStation = interaction.draggedLocalDistance();
            ProfileControlPoint draggedPoint = points.stream()
                .filter(point -> point.pviIndex() == editorState.selectedProfilePvi)
                .findFirst()
                .orElse(null);
            if (draggedPoint != null && draggedPoint.sharedJunction()) {
                requestedStation = currentStation;
            }
            if (draggedPoint != null && (draggedPoint.role() == ProfilePointRole.START_ENDPOINT
                    || draggedPoint.role() == ProfilePointRole.LOOP_SEAM_START)) {
                requestedStation = 0.0;
            } else if (draggedPoint != null && (draggedPoint.role() == ProfilePointRole.END_ENDPOINT
                    || draggedPoint.role() == ProfilePointRole.LOOP_SEAM_END)) {
                requestedStation = RoadStationing.canonicalLength(editingNetwork, road);
            }
            if (draggedPoint != null
                    && VerticalProfileControlPoints.isEditablePvi(editingNetwork, road, draggedPoint)) {
                profileEditSession.updatePviDrag(
                    editingNetwork,
                    road,
                    draggedPoint,
                    editorState.selectedProfilePvi,
                    requestedStation,
                    interaction.draggedElevation());
            }
        }
        if (interaction.dragFinished()) {
            profileEditSession.commitEdit(
                editingNetwork, road, config, windowSession,
                () -> host.propagateJunctionGrades(ctx, editingNetwork, road),
                host::notifyWorkingProfileChanged);
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                ProfileChartInteraction.CurveHandleSide.NONE;
        }
        if (interaction.curveHandleDragFinished()) {
            profileEditSession.commitEdit(
                editingNetwork, road, config, windowSession,
                () -> host.propagateJunctionGrades(ctx, editingNetwork, road),
                host::notifyWorkingProfileChanged);
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                ProfileChartInteraction.CurveHandleSide.NONE;
        }
        if (interaction.activeCurveHandlePvi() >= 0) {
            editorState.activeCurveHandlePvi = interaction.activeCurveHandlePvi();
            editorState.activeCurveHandle = interaction.activeCurveHandle();
        }
        if (interaction.intersectionDragFinished()) {
            List<RoadProfileIntersection> committedIntersections = List.copyOf(intersections);
            int finishedIntersectionDragIndex = host.activeIntersectionDragIndex();
            profileEditSession.commitEdit(
                editingNetwork,
                road,
                config,
                windowSession,
                flatMode ? () -> host.propagateJunctionGrades(ctx, editingNetwork, road) : null,
                () -> {
                    if (flatMode) {
                        for (RoadProfileIntersection intersection : committedIntersections) {
                            Road linked = editingNetwork.getRoad(intersection.otherRoadId());
                            if (linked != null) {
                                host.propagateJunctionGrades(ctx, editingNetwork, linked);
                            }
                        }
                    } else {
                        Road otherRoad = finishedIntersectionDragIndex >= 0
                                && finishedIntersectionDragIndex < committedIntersections.size()
                            ? editingNetwork.getRoad(
                                committedIntersections.get(finishedIntersectionDragIndex).otherRoadId())
                            : null;
                        if (otherRoad != null) {
                            host.propagateJunctionGrades(ctx, editingNetwork, otherRoad);
                        }
                    }
                    host.notifyWorkingProfileChanged();
                    ctx.requestOverlayRefresh();
                });
            host.setActiveIntersectionDragIndex(-1);
            host.setActiveIntersectionDragTarget(
                ProfileChartInteraction.IntersectionDragTarget.NONE);
        }
        // 图表命中已互斥；此处再收敛一次，避免定位下拉与图表状态短暂并存。
        int previousPvi = editorState.selectedProfilePvi;
        int previousIntersection = host.selectedIntersectionIndex();
        int chartIntersection = interaction.selectedIntersectionIndex();
        int chartPvi = flatMode
            ? VerticalProfileDetailPane.clampFlatEndpointPvi(interaction.selectedPviIndex())
            : interaction.selectedPviIndex();
        if (chartIntersection >= 0) {
            host.setSelectedIntersectionIndex(chartIntersection);
            editorState.selectedProfilePvi = -1;
            editorState.activeProfilePvi = -1;
        } else if (chartPvi >= 0) {
            host.setSelectedIntersectionIndex(-1);
            editorState.selectedProfilePvi = chartPvi;
        } else {
            host.setSelectedIntersectionIndex(-1);
            if (!flatMode) {
                editorState.selectedProfilePvi = -1;
                editorState.activeProfilePvi = -1;
            }
        }
        // 仅在图表改选 / 拖桩号时同步播放头，避免与 scrub 互相抢位置。
        if (host.selectedIntersectionIndex() >= 0
                && host.selectedIntersectionIndex() != previousIntersection
                && host.selectedIntersectionIndex() < intersections.size()) {
            host.setPlayheadStation(Math.clamp(
                (float) intersections.get(host.selectedIntersectionIndex()).roadStation(),
                0f,
                canvasLength));
        } else if (editorState.selectedProfilePvi >= 0
                && editorState.selectedProfilePvi != previousPvi) {
            for (ProfileControlPoint point : points) {
                if (point.pviIndex() == editorState.selectedProfilePvi) {
                    host.setPlayheadStation(
                        Math.clamp((float) point.roadStation(), 0f, canvasLength));
                    break;
                }
            }
        } else if (interaction.draggedLocalDistance() != null) {
            host.setPlayheadStation(Math.clamp(
                interaction.draggedLocalDistance().floatValue(), 0f, canvasLength));
        }
        detailPane.renderControlMasterDetail(
            ctx,
            editingNetwork,
            road,
            points,
            intersections,
            effectiveChart,
            config,
            flatMode,
            maxGrade,
            canvasLength);
        footerLegendIntersections = intersections != null ? List.copyOf(intersections) : List.of();
        footerLegendFlatMode = flatMode;
        footerLegendShowBuild = showBuildProfileLine;
        footerLegendChart = effectiveChart;
        footerLegendReady = true;
    }
}
