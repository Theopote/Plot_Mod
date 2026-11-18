package com.plot.plugin.road.ui.profile;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewState;
import com.plot.plugin.road.ui.ProfileEditorState;
import com.plot.plugin.road.ui.RoadLengthDisplay;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;

import java.util.List;
import java.util.Objects;

/**
 * 纵断面浮动编辑器壳层 UI：顶栏、页脚、状态芯片、对话框与拓扑冲突条。
 */
public final class VerticalProfileEditorChrome {

    private static final float MIN_ROAD_COMBO_WIDTH = 48f;
    /** 页脚状态芯片底色（ABGR）。 */
    private static final int CHIP_BG_OK = ImColor.rgba(28, 56, 36, 220);
    private static final int CHIP_BG_WARN = ImColor.rgba(64, 44, 20, 220);
    private static final int CHIP_BG_MUTED = ImColor.rgba(40, 40, 44, 220);
    private static final int CHIP_BORDER = ImColor.rgba(72, 72, 72, 180);

    private static int statusChipSeq;

    /** 供壳层注入，避免与编辑器循环依赖。 */
    public interface Host {
        ProfileEditorWindowSession windowSession();

        String editorRoadId();

        ProfileEditorState editorState();

        ProfileEditSession profileEditSession();

        VerticalProfileVoxelPreview voxelPreview();

        int selectedIntersectionIndex();

        List<RoadProfileIntersection> cachedIntersections();

        boolean closeConfirmPending();

        void setCloseConfirmPending(boolean pending);

        ImBoolean editorWindowOpen();

        boolean externalTopologyConflict();

        float lastFooterHeight();

        void setLastFooterHeight(float height);

        void selectEditorRoad(RoadUiContext ctx, String roadId, boolean sync);

        void undoEditorSession(RoadUiContext ctx);

        void redoEditorSession(RoadUiContext ctx);

        void requestVoxelPreview(RoadUiContext ctx, Road road);

        void restoreWindowSession();

        void requestEditorClose(RoadUiContext ctx, RoadNetwork liveNetwork, boolean forceIfClean);

        void applyWindowSessionConfirm(RoadUiContext ctx, RoadNetwork liveNetwork);

        void discardWindowSession();

        void discardAndReloadWindowSession(RoadUiContext ctx, RoadNetwork liveNetwork);
    }

    private final Host host;

    public VerticalProfileEditorChrome(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public float resolveFooterReserve() {
        // 已有实测高度时直接用，避免首帧偏大的估算一直撑出空隙。
        if (host.lastFooterHeight() > 1f) {
            return host.lastFooterHeight();
        }
        return estimateMinFooterHeight();
    }

    /** 首帧最小预留：图例行 + separator + 状态行 + 动作行 + 间距。 */
    public static float estimateMinFooterHeight() {
        float row = ImGui.getFrameHeightWithSpacing();
        return row * 3.4f + ImGui.getStyle().getItemSpacingY();
    }

    public void renderExternalTopologyConflictBanner(RoadUiContext ctx, RoadNetwork liveNetwork) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING_LIGHT,
            PlotI18n.tr("plugin.road.profile_editor_topology_conflict"));
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_discard_reload")
                + "##profile_topology_reload")) {
            host.discardAndReloadWindowSession(ctx, liveNetwork);
        }
    }

    /**
     * 底部操作：
     * 1) 状态芯片单独一行
     * 2) 动作尽量一行：左侧紧凑工具 + 右侧恢复/取消/保存；过窄时工具与提交分两行
     * 图例在编辑区与本页脚之间由编辑器主体渲染。
     */
    public void renderEditorFooter(RoadUiContext ctx, RoadNetwork liveNetwork, Road road) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        ImGui.separator();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float rowWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        float pad = ImGui.getStyle().getFramePaddingX() * 2f;

        boolean dirty = windowSession != null && windowSession.isDirty();
        renderProfilePipelineChips(ctx, road, dirty);

        boolean canUndo = windowSession != null && windowSession.canUndo();
        boolean canRedo = windowSession != null && windowSession.canRedo();
        boolean computing = windowSession != null
            && road != null
            && windowSession.voxelPreview(road.getId()).state() == VoxelProfilePreviewState.COMPUTING;

        String undoLabel = PlotI18n.tr("plugin.road.profile_editor_undo");
        String redoLabel = PlotI18n.tr("plugin.road.profile_editor_redo");
        String voxelLabel = PlotI18n.tr("plugin.road.update_voxel_profile");
        String restoreLabel = PlotI18n.tr("plugin.road.profile_editor_restore");
        String cancelLabel = PlotI18n.tr("plugin.road.profile_editor_cancel");
        String confirmLabel = PlotI18n.tr("plugin.road.profile_editor_confirm");

        float undoW = ImGui.calcTextSize(undoLabel).x + pad;
        float redoW = ImGui.calcTextSize(redoLabel).x + pad;
        float voxelW = ImGui.calcTextSize(voxelLabel).x + pad;
        float restoreMin = ImGui.calcTextSize(restoreLabel).x + pad;
        float cancelMin = ImGui.calcTextSize(cancelLabel).x + pad;
        float confirmMin = ImGui.calcTextSize(confirmLabel).x + pad;
        float toolsWidth = undoW + redoW + voxelW + spacing * 2f;
        float commitMinTotal = restoreMin + cancelMin + confirmMin + spacing * 2f;
        // 一行：工具紧凑宽度 + 剩余均分给提交组；过窄则拆两行避免中文标签被挤没。
        boolean oneRow = rowWidth >= toolsWidth + spacing + commitMinTotal;

        if (!canUndo) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(undoLabel + "##profile_undo", undoW, 0)) {
            host.undoEditorSession(ctx);
        }
        if (!canUndo) {
            ImGui.endDisabled();
        }
        ImGui.sameLine(0f, spacing);
        if (!canRedo) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(redoLabel + "##profile_redo", redoW, 0)) {
            host.redoEditorSession(ctx);
        }
        if (!canRedo) {
            ImGui.endDisabled();
        }
        ImGui.sameLine(0f, spacing);
        if (computing || road == null || windowSession == null) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(voxelLabel + "##update_voxel_profile", voxelW, 0) && road != null) {
            host.requestVoxelPreview(ctx, road);
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.update_voxel_profile_hint"));
        }
        if (computing || road == null || windowSession == null) {
            ImGui.endDisabled();
        }

        float commitAvail = oneRow
            ? Math.max(1f, rowWidth - toolsWidth - spacing)
            : rowWidth;
        if (oneRow) {
            ImGui.sameLine(0f, spacing);
        }
        float commitWidth = Math.max(
            Math.max(restoreMin, Math.max(cancelMin, confirmMin)),
            (commitAvail - spacing * 2f) / 3f);

        if (!dirty) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(restoreLabel + "##profile_restore", commitWidth, 0)) {
            host.restoreWindowSession();
        }
        if (!dirty) {
            ImGui.endDisabled();
        }
        ImGui.sameLine(0f, spacing);
        if (ImGui.button(cancelLabel + "##profile_cancel", commitWidth, 0)) {
            host.requestEditorClose(ctx, liveNetwork, true);
        }
        ImGui.sameLine(0f, spacing);
        if (dirty) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, PluginUiColors.INFO_BLUE);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, PluginUiColors.STATUS_INFO);
        }
        boolean confirmDisabled = host.externalTopologyConflict();
        if (confirmDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(confirmLabel + "##profile_confirm", commitWidth, 0)) {
            host.applyWindowSessionConfirm(ctx, liveNetwork);
        }
        if (confirmDisabled && ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_editor_topology_conflict"));
        }
        if (confirmDisabled) {
            ImGui.endDisabled();
        }
        if (dirty) {
            ImGui.popStyleColor(3);
        }
    }

    /**
     * 页脚管道状态：紧凑芯片代替长串「已保存 · 设计线 · …」文案。
     * 设计 / 方块线 / 建造 三态一目了然。
     */
    private void renderProfilePipelineChips(RoadUiContext ctx, Road road, boolean dirty) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        ProfileEditSession profileEditSession = host.profileEditSession();
        VerticalProfileVoxelPreview voxelPreview = host.voxelPreview();
        statusChipSeq = 0;
        if (dirty) {
            String unsaved = PlotI18n.tr("plugin.road.profile_editor_unsaved_status");
            int affectCount = windowSession != null
                ? windowSession.estimateConfirmAffectRoadCount() : 0;
            if (affectCount > 0) {
                unsaved = unsaved + " (" + affectCount + ")";
            }
            renderStatusChip(
                unsaved,
                PluginUiColors.WARNING_LIGHT,
                CHIP_BG_WARN,
                affectCount > 0
                    ? PlotI18n.tr("plugin.road.profile_editor_confirm_affect_summary", affectCount)
                    : null);
            ImGui.sameLine(0f, 6f);
        } else {
            renderStatusChip(
                PlotI18n.tr("plugin.road.profile_editor_clean_status"),
                PluginUiColors.STATUS_OK,
                CHIP_BG_OK,
                null);
            ImGui.sameLine(0f, 6f);
        }

        if (windowSession == null || road == null) {
            return;
        }

        boolean designDirty = dirty || profileEditSession.isDirty();
        renderStatusChip(
            PlotI18n.tr(designDirty
                ? "plugin.road.profile_pipeline_chip_design_dirty"
                : "plugin.road.profile_pipeline_chip_design_ok"),
            designDirty ? PluginUiColors.WARNING_LIGHT : PluginUiColors.STATUS_OK,
            designDirty ? CHIP_BG_WARN : CHIP_BG_OK,
            PlotI18n.tr(designDirty
                ? "plugin.road.profile_pipeline_design_dirty_hint"
                : "plugin.road.profile_pipeline_design_ok"));
        ImGui.sameLine(0f, 6f);

        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(road.getId());
        int voxelColor;
        int voxelBg;
        String voxelLabel;
        String voxelTip;
        switch (slot.state()) {
            case READY -> {
                voxelColor = PluginUiColors.STATUS_OK;
                voxelBg = CHIP_BG_OK;
                voxelLabel = PlotI18n.tr("plugin.road.profile_pipeline_chip_voxel_ok");
                voxelTip = PlotI18n.tr("plugin.road.profile_pipeline_voxel_ok");
            }
            case COMPUTING -> {
                voxelColor = PluginUiColors.HINT_GRAY;
                voxelBg = CHIP_BG_MUTED;
                voxelLabel = PlotI18n.tr("plugin.road.profile_pipeline_chip_voxel_computing");
                voxelTip = PlotI18n.tr("plugin.road.voxel_profile_computing");
                var activeJob = voxelPreview.activeJob();
                var pending = voxelPreview.pendingRequest();
                if (activeJob != null
                        && activeJob.totalEdges() > 0
                        && pending != null
                        && road.getId().equals(pending.roadId())) {
                    voxelTip = PlotI18n.tr(
                        "plugin.road.voxel_profile_progress",
                        activeJob.completedEdges(),
                        activeJob.totalEdges());
                }
            }
            case FAILED -> {
                voxelColor = PluginUiColors.WARNING;
                voxelBg = CHIP_BG_WARN;
                voxelLabel = PlotI18n.tr("plugin.road.profile_pipeline_chip_voxel_failed");
                voxelTip = PlotI18n.tr("plugin.road.voxel_profile_failed");
            }
            default -> {
                voxelColor = PluginUiColors.WARNING_LIGHT;
                voxelBg = CHIP_BG_WARN;
                voxelLabel = PlotI18n.tr("plugin.road.profile_pipeline_chip_voxel_stale");
                voxelTip = PlotI18n.tr("plugin.road.profile_pipeline_voxel_stale");
            }
        }
        renderStatusChip(voxelLabel, voxelColor, voxelBg, voxelTip);
        ImGui.sameLine(0f, 6f);

        boolean buildStale = ctx.previewManager().needsPreviewRecalc()
            || windowSession.isDirty()
            || profileEditSession.isDirty();
        renderStatusChip(
            PlotI18n.tr(buildStale
                ? "plugin.road.profile_pipeline_chip_build_stale"
                : "plugin.road.profile_pipeline_chip_build_ok"),
            buildStale ? PluginUiColors.WARNING_LIGHT : PluginUiColors.STATUS_OK,
            buildStale ? CHIP_BG_WARN : CHIP_BG_OK,
            PlotI18n.tr(buildStale
                ? "plugin.road.profile_pipeline_build_stale"
                : "plugin.road.profile_pipeline_build_ok"));
    }

    private static void renderStatusChip(
            String label,
            int textColor,
            int backgroundColor,
            String tooltip) {
        if (label == null || label.isBlank()) {
            return;
        }
        ImVec2 textSize = ImGui.calcTextSize(label);
        float padX = 7f;
        float padY = 3f;
        float width = textSize.x + padX * 2f;
        float height = textSize.y + padY * 2f;
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x, origin.y, origin.x + width, origin.y + height, backgroundColor, 4f);
        drawList.addRect(
            origin.x, origin.y, origin.x + width, origin.y + height, CHIP_BORDER, 4f, 0, 1f);
        drawList.addText(origin.x + padX, origin.y + padY, textColor, label);
        ImGui.invisibleButton("##profile_status_chip_" + (statusChipSeq++), width, height);
        if (tooltip != null && !tooltip.isBlank() && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
    }

    public void renderEditorDialogs(RoadUiContext ctx, RoadNetwork liveNetwork) {
        renderCloseConfirmDialog(ctx, liveNetwork);
    }

    private void renderCloseConfirmDialog(RoadUiContext ctx, RoadNetwork liveNetwork) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        if (!RoadUiWidgets.beginDeferredPopupModal(
                "##profile_editor_close_confirm",
                host.closeConfirmPending(),
                () -> host.setCloseConfirmPending(false))) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.profile_editor_close_confirm_message"));
        if (windowSession != null && windowSession.isDirty()) {
            int affectCount = windowSession.estimateConfirmAffectRoadCount();
            if (affectCount > 0) {
                ImGui.textColored(
                    PluginUiColors.WARNING_LIGHT,
                    PlotI18n.tr("plugin.road.profile_editor_confirm_affect_summary", affectCount));
            }
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_confirm") + "##close_apply")) {
            host.applyWindowSessionConfirm(ctx, liveNetwork);
            host.setCloseConfirmPending(false);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_cancel") + "##close_discard")) {
            host.setCloseConfirmPending(false);
            host.editorWindowOpen().set(false);
            host.discardWindowSession();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_continue_edit") + "##close_continue")) {
            host.setCloseConfirmPending(false);
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    /**
     * 紧凑顶部：道路选择一行 +（有选中时）动态上下文一行。
     * 标题栏已含「纵断面编辑器」，此处不再重复道路名称卡片。
     */
    public void renderEditorHeader(RoadUiContext ctx, RoadNetwork network, Road road) {
        ProfileEditorWindowSession windowSession = host.windowSession();
        String editorRoadId = host.editorRoadId();
        ProfileEditorState editorState = host.editorState();
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        if (roads.isEmpty()) {
            return;
        }
        int currentIndex = RoadProfileRoadNavigator.indexOf(roads, editorRoadId);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        Road currentRoad = roads.get(currentIndex);

        float spacing = ImGui.getStyle().getItemSpacingX();
        float rowWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        float navWidth = RoadUiWidgets.roadNavButtonWidth();
        float prevNextWidth = navWidth * 2f + spacing;
        String indexText = PlotI18n.tr(
            "plugin.road.profile_overview_road_index",
            currentIndex + 1,
            roads.size());
        float indexWidth = ImGui.calcTextSize(indexText).x;
        boolean dirty = windowSession != null && windowSession.isDirty();
        String dirtyText = PlotI18n.tr("plugin.road.profile_editor_unsaved_badge");
        float dirtyWidth = dirty ? ImGui.calcTextSize(dirtyText).x + spacing : 0f;
        float metaWidth = indexWidth + dirtyWidth + spacing;
        float comboMaxSingleRow = rowWidth - prevNextWidth - metaWidth - spacing * 2f;
        boolean wrapMeta = comboMaxSingleRow < MIN_ROAD_COMBO_WIDTH;

        if (RoadUiWidgets.roadNavPrevButton("##profile_prev")) {
            host.selectEditorRoad(ctx, RoadProfileRoadNavigator.previousRoadId(roads, editorRoadId), true);
        }
        ImGui.sameLine(0f, spacing);
        float comboWidth = wrapMeta
            ? Math.max(MIN_ROAD_COMBO_WIDTH, rowWidth - prevNextWidth - spacing * 2f)
            : Math.max(MIN_ROAD_COMBO_WIDTH, comboMaxSingleRow);
        var coordinates = ctx.host().coordinates();
        String preview = RoadProfileRoadList.formatProfileRoadSummary(network, currentRoad, coordinates);
        float labelPad = ImGui.getStyle().getFramePaddingX() * 2f;
        String comboPreview = RoadUiWidgets.truncateToWidth(preview, Math.max(1f, comboWidth - labelPad));
        ImGui.setNextItemWidth(comboWidth);
        if (ImGui.beginCombo("##profile_editor_road_combo", comboPreview)) {
            for (Road candidate : roads) {
                String label = RoadProfileRoadList.formatProfileRoadSummary(network, candidate, coordinates);
                boolean selected = candidate.getId().equals(editorRoadId);
                if (ImGui.selectable(label, selected)) {
                    host.selectEditorRoad(ctx, candidate.getId(), true);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            if (!comboPreview.equals(preview)) {
                ImGui.setTooltip(preview);
            }
        }
        ImGui.sameLine(0f, spacing);
        if (RoadUiWidgets.roadNavNextButton("##profile_next")) {
            host.selectEditorRoad(ctx, RoadProfileRoadNavigator.nextRoadId(roads, editorRoadId), true);
        }
        if (!wrapMeta) {
            ImGui.sameLine();
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, indexText);
        if (dirty) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.WARNING, dirtyText);
        }

        boolean hasSelection =
            host.selectedIntersectionIndex() >= 0 || editorState.selectedProfilePvi >= 0;
        boolean affectsOthers = affectingAffectsOthers(network, road);
        if (hasSelection || affectsOthers) {
            String selection = resolveSelectionSummary(ctx, network, road);
            String affecting = resolveAffectingSummary(network, road);
            ImGui.textColored(
                affectsOthers ? PluginUiColors.WARNING_LIGHT : PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_context_compact", selection, affecting));
        }
    }

    private String resolveSelectionSummary(RoadUiContext ctx, RoadNetwork network, Road road) {
        int selectedIntersectionIndex = host.selectedIntersectionIndex();
        ProfileEditorState editorState = host.editorState();
        ProfileEditSession profileEditSession = host.profileEditSession();
        if (selectedIntersectionIndex >= 0) {
            return PlotI18n.tr(
                "plugin.road.profile_editor_selection_intersection",
                selectedIntersectionIndex + 1);
        }
        if (editorState.selectedProfilePvi >= 0) {
            RoadVerticalAlignment alignment = profileEditSession.effectiveAlignment(road);
            if (alignment != null
                    && editorState.selectedProfilePvi < alignment.pviCount()) {
                double station = alignment.getPvis().get(editorState.selectedProfilePvi).getStation();
                return PlotI18n.tr(
                    "plugin.road.profile_editor_selection_pvi",
                    editorState.selectedProfilePvi + 1,
                    RoadLengthDisplay.formatCanvasDistance(
                        station, network, road, ctx.host().coordinates()));
            }
            return PlotI18n.tr(
                "plugin.road.profile_editor_selection_pvi_index",
                editorState.selectedProfilePvi + 1);
        }
        return PlotI18n.tr("plugin.road.profile_editor_selection_none");
    }

    private boolean affectingAffectsOthers(RoadNetwork network, Road road) {
        int selectedIntersectionIndex = host.selectedIntersectionIndex();
        ProfileEditorState editorState = host.editorState();
        ProfileEditSession profileEditSession = host.profileEditSession();
        if (selectedIntersectionIndex >= 0) {
            return true;
        }
        if (editorState.selectedProfilePvi < 0 || network == null || road == null) {
            return false;
        }
        RoadVerticalAlignment alignment = profileEditSession.effectiveAlignment(road);
        if (alignment == null || editorState.selectedProfilePvi >= alignment.pviCount()) {
            return false;
        }
        List<ProfileControlPoint> points = VerticalProfileControlPoints.forAlignment(
            network, road, alignment);
        return points.stream()
            .anyMatch(point -> point.pviIndex() == editorState.selectedProfilePvi && point.sharedJunction());
    }

    private String resolveAffectingSummary(RoadNetwork network, Road road) {
        int selectedIntersectionIndex = host.selectedIntersectionIndex();
        if (selectedIntersectionIndex >= 0) {
            List<RoadProfileIntersection> intersections = host.cachedIntersections();
            if (selectedIntersectionIndex < intersections.size()) {
                RoadProfileIntersection intersection = intersections.get(selectedIntersectionIndex);
                if (intersection.gradeSeparated()) {
                    return PlotI18n.tr("plugin.road.profile_editor_affect_current_only");
                }
                return PlotI18n.tr(
                    "plugin.road.profile_editor_affect_intersection",
                    intersection.otherRoadLabel());
            }
            return PlotI18n.tr("plugin.road.profile_editor_affect_connected");
        }
        if (affectingAffectsOthers(network, road)) {
            return PlotI18n.tr("plugin.road.profile_editor_affect_junction_pvi");
        }
        return PlotI18n.tr("plugin.road.profile_editor_affect_current_only");
    }
}
