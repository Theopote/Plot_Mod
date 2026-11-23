package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.ui.BridgeStructureControls;
import com.plot.plugin.road.ui.CrossSectionDraftEditor;
import com.plot.plugin.road.ui.CrossSectionDraftEditorOptions;
import com.plot.plugin.road.ui.CrossSectionDraftMutator;
import com.plot.plugin.road.ui.RoadCrossSectionEditor;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.ui.PluginSectionCard;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;

/**
 * 横断面设计器双栏面板：右参（分页签）+ 左实时预览。
 * <p>
 * 供道路级 {@link CrossSectionDesignerWindow} 与变截面详情复用。
 */
public final class CrossSectionDesignerPanel {

    private RoadCrossSectionPreviewRenderer.CrossSectionLayout cachedPreviewLayout;
    private long cachedPreviewFingerprint = Long.MIN_VALUE;

    /**
     * @param includeBridge 为 true 时渲染桥梁结构页（道路级）；桩号断面传 false
     * @param bridgeRoad    桥梁覆盖写回目标；{@code includeBridge=false} 时可 null
     */
    public void renderForRoad(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            boolean includeBridge,
            Road bridgeRoad) {
        if (mutator == null || road == null || config == null) {
            return;
        }
        renderDualColumn(
            ctx,
            mutator,
            includeBridge,
            bridgeRoad,
            () -> renderRoadPreview(network, road, config));
    }

    /**
     * 桩号断面详情：预览来自 draft（不含桥梁）。
     *
     * @param summaryPresetName 预览摘要用的预设名（可沿用道路 style）
     */
    public void renderForDraft(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            CrossSectionDraft draft,
            RoadSystemConfig config,
            String summaryPresetName) {
        if (mutator == null || draft == null || config == null) {
            return;
        }
        renderDualColumn(
            ctx,
            mutator,
            false,
            null,
            () -> renderDraftPreview(draft, config, summaryPresetName));
    }

    public void invalidate() {
        cachedPreviewLayout = null;
        cachedPreviewFingerprint = Long.MIN_VALUE;
    }

    private void renderDualColumn(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            boolean includeBridge,
            Road bridgeRoad,
            Runnable previewRenderer) {
        float totalWidth = ImGui.getContentRegionAvailX();
        float columnHeight = ImGui.getContentRegionAvailY();
        if (totalWidth < 80f || columnHeight < 40f) {
            return;
        }
        float spacing = ImGui.getStyle().getItemSpacingX();
        // 页签文案完整显示所需右栏宽；左右等宽 → 驱动外壳加宽。
        float tabsWidth = measureParamsTabsWidth(includeBridge);
        RoadFloatingEditorShell.noteContentMinWidth(tabsWidth * 2f + spacing);
        float columnWidth = Math.max(80f, (totalWidth - spacing) * 0.5f);
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();

        // 先右侧参数再左侧预览，保证同帧看到改参效果。
        ImGui.setCursorPos(startX + columnWidth + spacing, startY);
        ImGui.beginChild(
            "##xs_params",
            columnWidth,
            columnHeight,
            true,
            ImGuiWindowFlags.NoScrollbar);
        try {
            // 不 ResizeDown 挤压页签；宽度不够时由外壳加宽（触顶后再滚动）。
            if (ImGui.beginTabBar("##xs_tabs", ImGuiTabBarFlags.FittingPolicyScroll)) {
                try {
                    renderParamsTabs(ctx, mutator, includeBridge, bridgeRoad);
                } finally {
                    ImGui.endTabBar();
                }
            }
        } finally {
            ImGui.endChild();
        }

        ImGui.setCursorPos(startX, startY);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, PluginUiColors.PANEL_BG_DARK);
        try {
            ImGui.beginChild(
                "##xs_preview",
                columnWidth,
                columnHeight,
                true,
                ImGuiWindowFlags.NoScrollbar);
            try {
                previewRenderer.run();
            } finally {
                ImGui.endChild();
            }
        } finally {
            ImGui.popStyleColor();
        }
    }

    private static final String XS_SECTION = "plugin.road.floating_editor.cross_section.section.";

    private void renderParamsTabs(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            boolean includeBridge,
            Road bridgeRoad) {
        if (ImGui.beginTabItem(
            PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.structure"),
            ImGuiTabItemFlags.None)) {
            try {
                beginParamsTabBody("structure");
                try {
                    var opts = CrossSectionDraftEditorOptions.styleFloatingStructure();
                    sectionCard("##xs_sec_carriageway", "carriageway", true, () ->
                        CrossSectionDraftEditor.renderCarriagewayCore(ctx, mutator, opts));
                    // 单车道无可分隔对象：分车道宽与标线卡一并隐藏（车道数改回 1 时 mutator 已清标线）。
                    if (mutator.draft() != null && mutator.draft().laneCount() > 1) {
                        sectionCard("##xs_sec_lane_widths", "lane_widths", false, () ->
                            CrossSectionDraftEditor.renderLaneWidths(ctx, mutator, opts));
                        sectionCard("##xs_sec_markings", "markings", true, () ->
                            CrossSectionDraftEditor.renderMarkings(ctx, mutator, opts));
                    }
                } finally {
                    endParamsTabBody();
                }
            } finally {
                ImGui.endTabItem();
            }
        }
        if (ImGui.beginTabItem(
            PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.sidewalks"),
            ImGuiTabItemFlags.None)) {
            try {
                beginParamsTabBody("sidewalks");
                try {
                    var opts = CrossSectionDraftEditorOptions.styleFloatingStrips();
                    sectionCard("##xs_sec_strips", "strips", true, () ->
                        CrossSectionDraftEditor.renderAppearanceStrips(ctx, mutator, opts));
                } finally {
                    endParamsTabBody();
                }
            } finally {
                ImGui.endTabItem();
            }
        }
        if (ImGui.beginTabItem(
            PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.slope"),
            ImGuiTabItemFlags.None)) {
            try {
                beginParamsTabBody("slope");
                try {
                    var opts = CrossSectionDraftEditorOptions.styleFloatingSlope();
                    // 最大纵坡在纵断面工作区设置，横断面边坡页不再重复。
                    sectionCard("##xs_sec_edge", "edge", true, () ->
                        CrossSectionDraftEditor.renderRoadsideEdge(ctx, mutator, opts));
                    sectionCard("##xs_sec_batter", "batter", true, () ->
                        CrossSectionDraftEditor.renderSlopeRatios(ctx, mutator, opts));
                } finally {
                    endParamsTabBody();
                }
            } finally {
                ImGui.endTabItem();
            }
        }
        if (ImGui.beginTabItem(
            PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.materials"),
            ImGuiTabItemFlags.None)) {
            try {
                beginParamsTabBody("materials");
                try {
                    var opts = CrossSectionDraftEditorOptions.styleBatch();
                    sectionCard("##xs_sec_pavement", "pavement", true, () ->
                        CrossSectionDraftEditor.renderPavementMaterials(ctx, mutator, opts));
                    sectionCard("##xs_sec_slope_mats", "slope_mats", true, () ->
                        CrossSectionDraftEditor.renderSlopeMaterials(ctx, mutator, opts));
                    sectionCard("##xs_sec_marking_mat", "marking_mat", true, () ->
                        CrossSectionDraftEditor.renderMarkingMaterial(ctx, mutator, opts));
                } finally {
                    endParamsTabBody();
                }
            } finally {
                ImGui.endTabItem();
            }
        }
        if (ImGui.beginTabItem(
            PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.furniture"),
            ImGuiTabItemFlags.None)) {
            try {
                beginParamsTabBody("furniture");
                try {
                    var opts = CrossSectionDraftEditorOptions.styleBatch();
                    sectionCard("##xs_sec_median", "median", true, () ->
                        CrossSectionDraftEditor.renderMedian(ctx, mutator, opts));
                    sectionCard("##xs_sec_lighting", "lighting", true, () ->
                        CrossSectionDraftEditor.renderLighting(ctx, mutator, opts));
                } finally {
                    endParamsTabBody();
                }
            } finally {
                ImGui.endTabItem();
            }
        }
        if (includeBridge && bridgeRoad != null
                && ImGui.beginTabItem(
                    PlotI18n.tr("plugin.road.floating_editor.cross_section.tab.bridge"),
                    ImGuiTabItemFlags.None)) {
            try {
                beginParamsTabBody("bridge");
                try {
                    sectionCard("##xs_sec_piers", "piers", true, () ->
                        BridgeStructureControls.renderRoadOverridePiers(ctx, bridgeRoad, null));
                    sectionCard("##xs_sec_guardrail", "guardrail", true, () ->
                        BridgeStructureControls.renderRoadOverrideGuardrail(ctx, bridgeRoad, null));
                } finally {
                    endParamsTabBody();
                }
            } finally {
                ImGui.endTabItem();
            }
        }
    }

    private static void sectionCard(String id, String titleKey, boolean defaultOpen, Runnable body) {
        PluginSectionCard.render(
            id,
            PlotI18n.tr(XS_SECTION + titleKey),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            defaultOpen,
            body);
    }

    private void renderRoadPreview(RoadNetwork network, Road road, RoadSystemConfig config) {
        long fingerprint = RoadCrossSectionEditor.previewFingerprint(road, config);
        if (cachedPreviewLayout == null || cachedPreviewFingerprint != fingerprint) {
            cachedPreviewLayout = RoadCrossSectionEditor.previewLayoutForRoad(
                network, road, config, false);
            cachedPreviewFingerprint = fingerprint;
        }
        RoadCrossSectionEditor.renderPreviewWithLayout(
            road, config, cachedPreviewLayout, -1f);
    }

    private void renderDraftPreview(
            CrossSectionDraft draft,
            RoadSystemConfig config,
            String summaryPresetName) {
        long fingerprint = RoadCrossSectionEditor.previewFingerprint(draft);
        if (cachedPreviewLayout == null || cachedPreviewFingerprint != fingerprint) {
            cachedPreviewLayout = RoadCrossSectionEditor.previewLayoutForDraft(draft, config);
            cachedPreviewFingerprint = fingerprint;
        }
        RoadCrossSectionEditor.renderPreviewWithLayout(
            summaryPresetName,
            draft.width(),
            Math.max(1, draft.laneCount()),
            cachedPreviewLayout,
            -1f);
    }

    private static void beginParamsTabBody(String tabKey) {
        ImGui.beginChild("##xs_tab_body_" + tabKey, 0f, 0f, false);
    }

    private static void endParamsTabBody() {
        ImGui.endChild();
    }

    /** 全部页签标签自然宽度之和（含 Tab 内边距估计）。 */
    private static float measureParamsTabsWidth(boolean includeBridge) {
        float tabExtra = ImGui.getStyle().getFramePaddingX() * 2f
            + ImGui.getStyle().getItemInnerSpacingX()
            + 8f;
        float width = 0f;
        width += tabLabelWidth("plugin.road.floating_editor.cross_section.tab.structure", tabExtra);
        width += tabLabelWidth("plugin.road.floating_editor.cross_section.tab.sidewalks", tabExtra);
        width += tabLabelWidth("plugin.road.floating_editor.cross_section.tab.slope", tabExtra);
        width += tabLabelWidth("plugin.road.floating_editor.cross_section.tab.materials", tabExtra);
        width += tabLabelWidth("plugin.road.floating_editor.cross_section.tab.furniture", tabExtra);
        if (includeBridge) {
            width += tabLabelWidth("plugin.road.floating_editor.cross_section.tab.bridge", tabExtra);
        }
        return width;
    }

    private static float tabLabelWidth(String key, float tabExtra) {
        return ImGui.calcTextSize(PlotI18n.tr(key)).x + tabExtra;
    }
}
