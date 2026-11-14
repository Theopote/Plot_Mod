package com.plot.plugin.road.ui;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.RoadUniformElevationUtils;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.NetworkElevationAuthority;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import net.minecraft.world.World;

/**
 * 全局生成参数（产品化预设与桥梁选项）。
 */
public final class RoadGenerationSettingsPanel {
    private RoadGenerationSettingsPanel() {
    }

    /**
     * 未选道路时展示全局地形/坡度默认；单选非水平道路时同理。
     * 多选不得落到此路径（避免无提示改写主选道路或误用全局控件冒充批量）。
     */
    public static boolean showsTerrainAdaptiveControls(RoadUiContext ctx) {
        if (NetworkElevationAuthority.isUniformFlat(ctx.networkManager().getNetwork())) {
            return false;
        }
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.MULTI) {
            return false;
        }
        if (mode == RoadSelectionHeader.Mode.NONE) {
            return true;
        }
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return true;
        }
        return RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT;
    }

    /** 仅单选且为地形适应时展示逐路坡度/地形/纵断面来源。 */
    public static boolean showsPerRoadTerrainControls(RoadUiContext ctx, Road road) {
        if (NetworkElevationAuthority.isUniformFlat(ctx.networkManager().getNetwork())) {
            return false;
        }
        if (RoadSelectionHeader.resolveMode(ctx) != RoadSelectionHeader.Mode.SINGLE) {
            return false;
        }
        return road != null
            && RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.TERRAIN_ADAPTIVE;
    }

    /**
     * 纵断面工作区：统一标高模式、目标道路、路径属性、坡度/地形/纵断面来源。
     * 桥梁结构选项已迁至横断面工作区。
     */
    public static void renderVerticalDesign(
            RoadUiContext ctx,
            RoadPathVerticalPropertyPanel pathPropertyPanel,
            Runnable openProfileEditorForSelectedRoad) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        if (!network.getEdges().isEmpty()) {
            RoadGenerateTargetBar.render(ctx, network);
            ImGui.spacing();
        }

        renderUniformFlatModeControls(ctx, network);
        // 复选框可能刚改写了 mode：同帧重读，避免仍渲染上一模式控件。
        boolean uniformFlat = NetworkElevationAuthority.isUniformFlat(network);
        ImGui.spacing();

        if (!uniformFlat && !network.getEdges().isEmpty() && pathPropertyPanel != null) {
            pathPropertyPanel.renderGenerationSettings(ctx);
            ImGui.spacing();
        }

        if (uniformFlat) {
            return;
        }

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case MULTI -> {
                // 批量纵向已由 pathPropertyPanel 渲染；禁止再显示主选道路的逐路 ProfileSource。
            }
            case SINGLE -> {
                Road activeRoad = ctx.networkManager().getPrimarySelectedRoad();
                if (showsPerRoadTerrainControls(ctx, activeRoad)) {
                    RoadStyleProductControls.renderRoadMaxSlopePresets(
                        ctx, activeRoad, ctx.networkManager()::pushHistory);
                    RoadStyleProductControls.renderRoadTerrainStylePresets(
                        ctx, activeRoad, ctx.networkManager()::pushHistory);
                    ProfileSourceControls.render(
                        ctx,
                        network,
                        activeRoad,
                        openProfileEditorForSelectedRoad);
                } else {
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.generate.flat_terrain_controls_hidden"));
                }
            }
            case NONE -> {
                if (showsTerrainAdaptiveControls(ctx)) {
                    RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);
                    RoadStyleProductControls.renderTerrainStylePresets(ctx);
                }
            }
        }
    }

    private static void renderUniformFlatModeControls(RoadUiContext ctx, RoadNetwork network) {
        boolean wasUniformFlat = NetworkElevationAuthority.isUniformFlat(network);
        ImBoolean enabled = new ImBoolean(wasUniformFlat);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.uniform_flat_elevation"), enabled)) {
            ctx.networkManager().setNetworkElevationMode(
                enabled.get() ? NetworkElevationMode.UNIFORM_FLAT : NetworkElevationMode.PER_ROAD);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_flat_elevation_hint"));
        }

        if (!NetworkElevationAuthority.isUniformFlat(network)) {
            return;
        }

        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.uniform_flat_elevation_hint"));

        int conflicts = NetworkElevationAuthority.countGradeSeparationConflicts(network);
        if (conflicts > 0) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.uniform_flat_grade_sep_conflict", conflicts));
            if (ImGui.button(PlotI18n.tr("plugin.road.generate.goto_intersections"))) {
                ctx.requestTab(RoadUiTab.ROUTE);
            }
        }

        int[] draftY = {network.getUniformElevationY()};
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.uniform_elevation_custom_y") + "##uniform_flat_y",
                draftY,
                RoadParameterLimits.ELEVATION_MIN,
                RoadParameterLimits.ELEVATION_MAX,
                "Y=%d")) {
            if (!ctx.networkManager().updateUniformElevationYDuringDrag(draftY[0])) {
                draftY[0] = network.getUniformElevationY();
            }
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            ctx.networkManager().commitUniformElevationYDrag();
        } else if (ImGui.isItemDeactivated() && ctx.networkManager().hasUniformElevationYDrag()) {
            ctx.networkManager().cancelUniformElevationYDrag();
        }

        ImGui.sameLine();
        boolean noEdges = network.getEdges().isEmpty();
        if (noEdges) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_sample"))) {
            recommendUniformElevation(ctx, network);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_sample_hint"));
        }
        if (noEdges) {
            ImGui.endDisabled();
        }
    }

    private static void recommendUniformElevation(RoadUiContext ctx, RoadNetwork network) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, ctx.host().coordinates());
        RoadUniformElevationUtils.ElevationRecommendation recommendation =
            ctx.networkManager().previewUniformElevation(terrain);
        if (recommendation != null) {
            ctx.networkManager().setUniformElevationY(recommendation.elevation());
        }
    }

    private static void markChanged(RoadUiContext ctx) {
        ctx.networkManager().getConfig().markCustom();
        ctx.onGenerationConfigChanged();
    }
}
