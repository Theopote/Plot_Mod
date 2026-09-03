package com.plot.plugin.earthwork.ui;

import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.volume.EarthworkLearnLesson;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 学习模式：材料换算与「土可以去哪」的讲解包装。
 */
public final class EarthworkLearnWidgets {
    private EarthworkLearnWidgets() {
    }

    public static void renderConversionLesson(EarthworkUiContext ctx, EarthworkVolumeReport volumes) {
        EarthworkLearnLesson.ConversionStory story = EarthworkLearnLesson.from(volumes);
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.earthwork.learn.story_header"));
        ImGui.textWrapped(PlotI18n.tr(
            "plugin.earthwork.learn.story_cut_fill",
            story.dug(),
            story.fillNeeded()));
        ImGui.textWrapped(PlotI18n.tr(
            "plugin.earthwork.learn.story_one_to_one",
            story.leftoverIfOneToOne(),
            story.missingIfOneToOne()));

        if (ImGui.treeNode(PlotI18n.tr("plugin.earthwork.learn.why_conversion"))) {
            ImGui.textWrapped(PlotI18n.tr("plugin.earthwork.learn.why_conversion_body"));
            ImGui.textWrapped(PlotI18n.tr(
                "plugin.earthwork.learn.reality_example",
                story.reusablePercent(),
                story.compactedPercent(),
                story.realityReusable(),
                story.realityUsableFill(),
                story.realityExport(),
                story.realityImport()));
            float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.learn.apply_reality"), half, 0)) {
                applyMaterialExample(ctx, MaterialConversionModel.LEARNING);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.learn.apply_minecraft"), half, 0)) {
                applyMaterialExample(ctx, MaterialConversionModel.MINECRAFT);
            }
            ImGui.treePop();
        }
    }

    public static void renderDirtFlowHeader() {
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.earthwork.learn.dirt_flow_header"));
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.earthwork.learn.dirt_flow_hint"));
        if (ImGui.treeNode(PlotI18n.tr("plugin.earthwork.learn.why_allocation"))) {
            ImGui.textWrapped(PlotI18n.tr("plugin.earthwork.learn.why_allocation_body"));
            ImGui.treePop();
        }
    }

    private static void applyMaterialExample(EarthworkUiContext ctx, MaterialConversionModel model) {
        EarthworkSite site = ctx.project().getActiveSite();
        if (site == null || model == null) {
            return;
        }
        ctx.projectHistory().push(ctx.project());
        site.setMaterialModel(model);
        ctx.config().setDefaultMaterialProperties(model);
        ctx.config().save();
        for (GradingRegion region : ctx.project().getRegions().values()) {
            region.setMaterialProperties(model);
        }
        ctx.recalculatePreview();
    }
}
