package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.ui.BridgeStructureControls;
import com.plot.plugin.road.ui.CrossSectionDraftEditor;
import com.plot.plugin.road.ui.CrossSectionDraftEditorOptions;
import com.plot.plugin.road.ui.CrossSectionDraftMutator;
import com.plot.plugin.road.ui.RoadCrossSectionEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;

/** 横断面 / 材质设计器浮动窗口。 */
public final class CrossSectionDesignerWindow extends AbstractRoadFloatingEditor {

    /** 跨帧复用，避免拖滑条时每帧 fromRoad 分配与重解析。 */
    private CrossSectionDraftMutator cachedMutator;
    private String cachedMutatorRoadId = "";
    private RoadNetworkEditorSession cachedMutatorSession;

    private RoadCrossSectionPreviewRenderer.CrossSectionLayout cachedPreviewLayout;
    private long cachedPreviewFingerprint = Long.MIN_VALUE;

    @Override
    protected RoadFloatingEditorKind editorKind() {
        return RoadFloatingEditorKind.CROSS_SECTION;
    }

    @Override
    protected String windowTitleKey() {
        return "plugin.road.floating_editor.cross_section.title";
    }

    @Override
    protected String windowImGuiId() {
        return "###road_cross_section_designer";
    }

    @Override
    protected String closeConfirmPopupId() {
        return "##road_cross_section_close_confirm";
    }

    @Override
    protected RoadChangeKind confirmChangeKind() {
        return RoadChangeKind.CROSS_SECTION;
    }

    @Override
    protected RoadNetworkEditorSession createSession(RoadUiContext ctx, String roadId) {
        invalidateUiCaches();
        var live = ctx.networkManager().getNetwork();
        var config = ctx.networkManager().getConfig();
        var openSnapshot = live.snapshot();
        return RoadNetworkEditorSession.open(
            live,
            ctx.networkManager().getNetworkRevision(),
            working -> CrossSectionEditorApplier.hasChanges(
                openSnapshot, working, roadId, config));
    }

    @Override
    protected void restoreSession() {
        invalidateUiCaches();
        super.restoreSession();
    }

    @Override
    public void discard(RoadUiContext ctx) {
        invalidateUiCaches();
        super.discard(ctx);
    }

    @Override
    protected void renderWindowBody(RoadUiContext ctx) {
        if (session == null) {
            return;
        }
        Road road = session.workingNetwork().getRoad(roadId);
        if (road == null) {
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.road_missing"));
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        float totalWidth = ImGui.getContentRegionAvailX();
        float columnHeight = ImGui.getContentRegionAvailY();
        float spacing = ImGui.getStyle().getItemSpacingX();
        // 左右等宽：预览列宽与参数列宽相同（整体窗宽已按此观感收窄并固定）。
        float columnWidth = Math.max(80f, (totalWidth - spacing) * 0.5f);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();

        // 先渲染右侧参数（写回 working road），再画左侧预览，保证同帧看到改参效果。
        // 外层固定标签栏；仅页签内容区滚动，避免滚动把标签一起带走。
        ImGui.setCursorPos(startX + columnWidth + spacing, startY);
        ImGui.beginChild(
            "##xs_params",
            columnWidth,
            columnHeight,
            true,
            ImGuiWindowFlags.NoScrollbar);
        CrossSectionDraftMutator mutator = mutatorFor(road, config);

        if (ImGui.beginTabBar("##xs_tabs", ImGuiTabBarFlags.FittingPolicyResizeDown)) {
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.structure"),
                ImGuiTabItemFlags.None)) {
                beginParamsTabBody();
                CrossSectionDraftEditor.renderCrossSection(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleFloatingStructure());
                endParamsTabBody();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.sidewalks"),
                ImGuiTabItemFlags.None)) {
                beginParamsTabBody();
                CrossSectionDraftEditor.renderCrossSection(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleFloatingStrips());
                endParamsTabBody();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.slope"),
                ImGuiTabItemFlags.None)) {
                beginParamsTabBody();
                CrossSectionDraftEditorOptions slopeOpts = new CrossSectionDraftEditorOptions(
                    "xs_slope", false, null, false, false, true, false, false, false);
                CrossSectionDraftEditor.renderCrossSection(ctx, mutator, slopeOpts);
                endParamsTabBody();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.materials"),
                ImGuiTabItemFlags.None)) {
                beginParamsTabBody();
                CrossSectionDraftEditor.renderMaterials(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleBatch());
                endParamsTabBody();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.furniture"),
                ImGuiTabItemFlags.None)) {
                beginParamsTabBody();
                CrossSectionDraftEditor.renderFurniture(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleBatch());
                endParamsTabBody();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.bridge"),
                ImGuiTabItemFlags.None)) {
                beginParamsTabBody();
                BridgeStructureControls.renderRoadOverride(ctx, road, null);
                endParamsTabBody();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.endChild();

        ImGui.setCursorPos(startX, startY);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, PluginUiColors.PANEL_BG_DARK);
        ImGui.beginChild(
            "##xs_preview",
            columnWidth,
            columnHeight,
            true,
            ImGuiWindowFlags.NoScrollbar);
        // 滑条拖动时仍实时刷新；靠 mutator/布局/材质色缓存降低每帧成本。
        renderLivePreview(session.workingNetwork(), road, config);
        ImGui.endChild();
        ImGui.popStyleColor();
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        CrossSectionEditorApplier.applyToLive(
            ctx.networkManager().getNetwork(),
            session.workingNetwork(),
            roadId,
            ctx.networkManager().getConfig());
    }

    private CrossSectionDraftMutator mutatorFor(Road road, RoadSystemConfig config) {
        if (cachedMutator == null
                || cachedMutatorSession != session
                || !roadId.equals(cachedMutatorRoadId)) {
            cachedMutator = CrossSectionDraftMutator.forRoad(
                road,
                config,
                () -> { /* dirty tracked by session */ });
            cachedMutatorSession = session;
            cachedMutatorRoadId = roadId;
        }
        return cachedMutator;
    }

    private void renderLivePreview(
            com.plot.plugin.road.model.RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        long fingerprint = RoadCrossSectionEditor.previewFingerprint(road, config);
        if (cachedPreviewLayout == null || cachedPreviewFingerprint != fingerprint) {
            // 设计器预览不扫全网分段纵坡 override，拖参时更轻。
            cachedPreviewLayout = RoadCrossSectionEditor.previewLayoutForRoad(
                network, road, config, false);
            cachedPreviewFingerprint = fingerprint;
        }
        RoadCrossSectionEditor.renderPreviewWithLayout(
            road, config, cachedPreviewLayout, -1f);
    }

    private void invalidateUiCaches() {
        cachedMutator = null;
        cachedMutatorRoadId = "";
        cachedMutatorSession = null;
        cachedPreviewLayout = null;
        cachedPreviewFingerprint = Long.MIN_VALUE;
    }

    /** 页签内容独立滚动区：高度 0 表示吃满标签栏下方剩余空间。 */
    private static void beginParamsTabBody() {
        ImGui.beginChild("##xs_tab_body", 0f, 0f, false);
    }

    private static void endParamsTabBody() {
        ImGui.endChild();
    }

}
