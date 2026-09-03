package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.core.plugin.PluginManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.BuildingPlugin;
import com.plot.plugin.RoadSystemPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.*;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


/** 土方插件顶部工具栏与落地进度控制。 */
public final class EarthworkToolbarPanel {
    private final EarthworkUiContext ctx;

    public EarthworkToolbarPanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderToolbar();
        renderActivePlacementControls();
    }

    private void renderToolbar() {
        float buttonWidth = Math.max(1f, (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f);

        boolean undoDisabled = !ctx.projectHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.undo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().undo(ctx.project()));
            EarthworkUiWidgets.syncSelectedRegionAfterHistory(ctx);
            ctx.setRegionNameEditingRegionId("");
            ctx.clearPreview();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !ctx.projectHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.redo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().redo(ctx.project()));
            EarthworkUiWidgets.syncSelectedRegionAfterHistory(ctx);
            ctx.setRegionNameEditingRegionId("");
            ctx.clearPreview();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, ctx.projectStatus());
        }
        ImGui.separator();
        renderWorkMode();
        ImGui.separator();
    }

    private void renderWorkMode() {
        EarthworkWorkMode current = ctx.config().getWorkMode();
        EarthworkWorkMode[] modes = EarthworkWorkMode.values();
        String[] labels = new String[modes.length];
        int selected = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr(modes[i].i18nKey());
            if (modes[i] == current) {
                selected = i;
            }
        }
        ImInt index = ctx.workModeIndex();
        index.set(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo(PlotI18n.tr("plugin.earthwork.work_mode"), index, labels)) {
            int picked = index.get();
            if (picked >= 0 && picked < modes.length && modes[picked] != current) {
                ctx.config().setWorkMode(modes[picked]);
                ctx.config().save();
            }
        }
    }

    private void renderActivePlacementControls() {
        com.plot.api.world.IBlockPlacementService scheduler = ctx.host().placement();
        if (!scheduler.isBusy()) {
            return;
        }

        com.plot.api.world.IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            ImGui.textColored(PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.earthwork.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.earthwork.build_in_progress_hint"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
        ImGui.separator();
    }
}
