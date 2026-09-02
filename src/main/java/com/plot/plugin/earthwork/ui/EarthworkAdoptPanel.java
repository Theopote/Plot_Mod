package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.material.MaterialConversionModel;
import com.plot.core.model.Shape;
import com.plot.core.plugin.PluginManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.BuildingPlugin;
import com.plot.plugin.RoadSystemPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.*;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.model.*;
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


/** 土方认领 Tab：从画布选区认领 grading 区域。 */
public final class EarthworkAdoptPanel {
    private final EarthworkUiContext ctx;

    public EarthworkAdoptPanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.adopt_hint"));
                ImGui.spacing();

                if (ctx.pickSession().isActive()) {
                    int count = ctx.pickSession().getAccumulatedCount();
                    if (count > 0) {
                        ImGui.text(String.format(
                            PlotI18n.tr("plugin.earthwork.regions_selected"),
                            count));
                    }
                } else {
                    updateSelectedRegions();
                }

                if (!ctx.selectedRegions().isEmpty()) {
                    ImGui.text(String.format(
                        PlotI18n.tr("plugin.earthwork.regions_selected"),
                        ctx.selectedRegions().size()));
                } else {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.draw_region_hint"));
                }

                ImGui.spacing();
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.pick_region"), 0, 0)) {
                    startPickSession();
                }
                ImGui.sameLine();
                boolean adoptDisabled = ctx.selectedRegions().isEmpty();
                if (adoptDisabled) {
                    ImGui.beginDisabled();
                }
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.adopt_region"), 0, 0)) {
                    adoptSelectedRegions();
                }
                if (adoptDisabled) {
                    ImGui.endDisabled();
                }
    }

    private void updateSelectedRegions() {
        ctx.selectedRegions().clear();
        ctx.selectedRegions().addAll(
            EarthworkGeometryUtils.findAdoptableRegions(ctx.host().appState().getSelectedShapes()));
    }

    private void startPickSession() {
        ctx.threePointPickSession().cancel();
        ToolManager toolManager = ctx.host().tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        ctx.selectedRegions().clear();
        ctx.pickSession().begin();
        toolManager.setActiveTool(selectTool);
        ctx.host().appState().setCurrentTool(baseTool);
        ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_started"));
    }

    public void tickPickSession() {
        EarthworkRegionPickSession.Outcome outcome = ctx.pickSession().tick(ctx.host().appState());
        switch (outcome.getResult()) {
            case SUCCESS -> {
                ctx.selectedRegions().clear();
                ctx.selectedRegions().addAll(outcome.getRegions());
                adoptSelectedRegions();
            }
            case NEED_SELECTION -> ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_need_selection"));
            case NO_VALID -> ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_no_valid"));
            case CANCELLED -> ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.pick_cancelled"));
            default -> {
                List<Shape> selected = ctx.host().appState().getSelectedShapes();
                ctx.setProjectStatus(PlotI18n.tr(ctx.pickSession().hintKeyForCurrentSelection(selected)));
            }
        }
    }

    private void adoptSelectedRegions() {
        if (ctx.selectedRegions().isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
            return;
        }

        // 先收集有效轮廓，避免 0 认领仍 push 历史
        List<List<Vec2d>> validOutlines = new ArrayList<>();
        for (Shape shape : ctx.selectedRegions()) {
            List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(shape);
            if (points.size() >= 3) {
                validOutlines.add(points);
            }
        }
        if (validOutlines.isEmpty()) {
            ctx.setProjectStatus(PlotI18n.tr("plugin.earthwork.adopt_no_selection"));
            ctx.selectedRegions().clear();
            return;
        }

        ctx.projectHistory().push(ctx.project());
        int adopted = 0;
        for (List<Vec2d> points : validOutlines) {
            GradingRegion region = new GradingRegion(points);
            region.setName(PlotI18n.tr("plugin.earthwork.default_name", adopted + 1));
            region.setAutoBalance(ctx.config().isAutoBalance());
            region.setMaterialProperties(MaterialConversionModel.DEFAULT);
            region.setPreviewGridSize(ctx.config().getPreviewGridSize());
            if (!ctx.config().isAutoBalance()) {
                region.setManualTargetElevation(Math.round(ctx.config().getTargetElevation()));
            }
            ctx.project().addRegion(region);
            ctx.setSelectedRegionId(region.getId());
            adopted++;
        }

        ctx.selectedRegions().clear();
        ctx.clearPreview();
        ctx.setProjectStatus(adopted > 1
            ? PlotI18n.tr("plugin.earthwork.adopt_success_batch", adopted)
            : PlotI18n.tr("plugin.earthwork.adopt_success"));
    }
}
