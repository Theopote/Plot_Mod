package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.ui.CrossSectionDraftEditor;
import com.plot.plugin.road.ui.CrossSectionDraftEditorOptions;
import com.plot.plugin.road.ui.CrossSectionDraftMutator;
import com.plot.plugin.road.ui.RoadCrossSectionEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;

/** 横断面 / 材质设计器浮动窗口。 */
public final class CrossSectionDesignerWindow extends AbstractRoadFloatingEditor {

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
        float previewWidth = totalWidth * 0.38f;
        float paramsWidth = totalWidth - previewWidth - ImGui.getStyle().getItemSpacingX();

        ImGui.beginChild("##xs_preview", previewWidth, 0, true);
        ImGui.text(PlotI18n.tr("plugin.road.floating_editor.cross_section.preview"));
        RoadCrossSectionEditor.renderPreview(road, config);
        ImGui.endChild();

        ImGui.sameLine();
        ImGui.beginChild(
            "##xs_params",
            paramsWidth,
            0,
            true,
            ImGuiWindowFlags.AlwaysVerticalScrollbar);
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forRoad(
            road,
            config,
            () -> { /* dirty tracked by session */ });

        if (ImGui.beginTabBar("##xs_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.structure"),
                ImGuiTabItemFlags.None)) {
                CrossSectionDraftEditor.renderCrossSection(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleFloatingStructure());
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.sidewalks"),
                ImGuiTabItemFlags.None)) {
                CrossSectionDraftEditor.renderCrossSection(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleFloatingStrips());
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.slope"),
                ImGuiTabItemFlags.None)) {
                CrossSectionDraftEditorOptions slopeOpts = new CrossSectionDraftEditorOptions(
                    "xs_slope", false, null, false, false, true, false, false);
                CrossSectionDraftEditor.renderCrossSection(ctx, mutator, slopeOpts);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.materials"),
                ImGuiTabItemFlags.None)) {
                CrossSectionDraftEditor.renderMaterials(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleBatch());
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(
                PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.furniture"),
                ImGuiTabItemFlags.None)) {
                CrossSectionDraftEditor.renderFurniture(
                    ctx,
                    mutator,
                    CrossSectionDraftEditorOptions.styleBatch());
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.endChild();
    }

    @Override
    protected void applySessionToLive(RoadUiContext ctx) {
        CrossSectionEditorApplier.applyToLive(
            ctx.networkManager().getNetwork(),
            session.workingNetwork(),
            roadId,
            ctx.networkManager().getConfig());
    }

}
