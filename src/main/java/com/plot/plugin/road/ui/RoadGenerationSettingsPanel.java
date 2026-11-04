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
import com.plot.plugin.road.profile.RoadProfileRoadList;
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

    /** 单选水平道路时隐藏地形适应与全局最大坡度预设。 */
    public static boolean showsTerrainAdaptiveControls(RoadUiContext ctx) {
        if (NetworkElevationAuthority.isUniformFlat(ctx.networkManager().getNetwork())) {
            return false;
        }
        if (ctx.networkManager().getSelectedRoadIds().size() != 1) {
            return true;
        }
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return true;
        }
        return RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT;
    }

    /** 单选地形适应道路时，生成设置展示道路级坡度/地形/纵断面来源。 */
    public static boolean showsPerRoadTerrainControls(RoadUiContext ctx, Road road) {
        if (NetworkElevationAuthority.isUniformFlat(ctx.networkManager().getNetwork())) {
            return false;
        }
        return road != null
            && RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.TERRAIN_ADAPTIVE
            && showsTerrainAdaptiveControls(ctx);
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
        renderUniformFlatModeControls(ctx, network);
        // 复选框可能刚改写了 mode：同帧重读，避免仍渲染上一模式控件。
        boolean uniformFlat = NetworkElevationAuthority.isUniformFlat(network);
        ImGui.spacing();

        if (!network.getEdges().isEmpty()) {
            RoadGenerateTargetBar.render(ctx, network);
            ImGui.spacing();
            if (!uniformFlat && pathPropertyPanel != null) {
                pathPropertyPanel.renderGenerationSettings(ctx);
                ImGui.spacing();
            }
        }

        if (!uniformFlat) {
            Road activeRoad = RoadProfileRoadList.resolveActiveRoad(network, ctx);
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
            } else if (showsTerrainAdaptiveControls(ctx)) {
                RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);
                RoadStyleProductControls.renderTerrainStylePresets(ctx);
            } else {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.generate.flat_terrain_controls_hidden"));
            }
        }
    }

    /** @deprecated 使用 {@link #renderVerticalDesign}；保留别名以免旧调用方编译失败。 */
    @Deprecated
    public static void renderPrimary(
            RoadUiContext ctx,
            RoadPathVerticalPropertyPanel pathPropertyPanel,
            Runnable openProfileEditorForSelectedRoad) {
        renderVerticalDesign(ctx, pathPropertyPanel, openProfileEditorForSelectedRoad);
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
