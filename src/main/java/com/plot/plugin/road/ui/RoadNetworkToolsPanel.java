package com.plot.plugin.road.ui;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.world.World;

/**
 * 编辑 Tab 路网级工具（全网统一标高等）。
 */
final class RoadNetworkToolsPanel {

    private final RoadUiContext ctx;
    private final int[] uniformElevationDraft = {64};
    private String lastRecommendationSummary = "";
    private boolean uniformElevationConfirmPending = false;
    private boolean uniformElevationConfirmAuto = true;

    RoadNetworkToolsPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    void render(RoadNetwork network) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.uniform_flat_elevation"))) {
            return;
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.uniform_flat_elevation_warning"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.uniform_flat_elevation_hint"));

        boolean disabled = network.getEdges().isEmpty();
        if (disabled) {
            ImGui.beginDisabled();
        }

        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_auto_apply"), half, 0)) {
            uniformElevationConfirmAuto = true;
            uniformElevationConfirmPending = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_auto_apply_hint"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_sample"), half, 0)) {
            sampleUniformElevationSuggestion();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_sample_hint"));
        }

        if (!lastRecommendationSummary.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.STATUS_INFO, lastRecommendationSummary);
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
        ImGui.sliderInt(
            PlotI18n.tr("plugin.road.uniform_elevation_custom_y") + "##uniform_y",
            uniformElevationDraft,
            RoadParameterLimits.ELEVATION_MIN,
            RoadParameterLimits.ELEVATION_MAX,
            "Y=%d"
        );
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.node_elevation"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_custom_apply"), 0, 0)) {
            uniformElevationConfirmAuto = false;
            uniformElevationConfirmPending = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_custom_apply_hint"));
        }

        if (disabled) {
            ImGui.endDisabled();
        }
        ImGui.spacing();
    }

    void renderConfirmPopup() {
        if (uniformElevationConfirmPending) {
            ImGui.openPopup("##road_uniform_elevation_confirm");
            uniformElevationConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##road_uniform_elevation_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (uniformElevationConfirmAuto) {
                ImGui.textWrapped(PlotI18n.tr("plugin.road.uniform_elevation_confirm_auto"));
            } else {
                ImGui.textWrapped(PlotI18n.tr(
                    "plugin.road.uniform_elevation_confirm_custom",
                    uniformElevationDraft[0]));
            }
            ImGui.separator();
            RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.uniform_elevation_confirm_side_effect"));

            if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_confirm_ok"), 120, 0)) {
                if (uniformElevationConfirmAuto) {
                    applyUniformFlatElevationAuto();
                } else {
                    ctx.networkManager().applyCustomUniformFlatElevation(uniformElevationDraft[0]);
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private TerrainSampler requireTerrainOrNull() {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        return MinecraftTerrainSampler.of(world, ctx.host().coordinates());
    }

    private void applyUniformFlatElevationAuto() {
        TerrainSampler terrain = requireTerrainOrNull();
        if (terrain == null) {
            return;
        }
        var recommendation = ctx.networkManager().applyUniformFlatElevation(terrain);
        if (recommendation != null) {
            uniformElevationDraft[0] = recommendation.elevation();
            lastRecommendationSummary = formatRecommendation(recommendation);
        }
    }

    private void sampleUniformElevationSuggestion() {
        TerrainSampler terrain = requireTerrainOrNull();
        if (terrain == null) {
            return;
        }
        var recommendation = ctx.networkManager().previewUniformElevation(terrain);
        if (recommendation != null) {
            uniformElevationDraft[0] = recommendation.elevation();
            lastRecommendationSummary = formatRecommendation(recommendation);
        }
    }

    private static String formatRecommendation(
            com.plot.plugin.road.RoadUniformElevationUtils.ElevationRecommendation recommendation) {
        String strategy = recommendation.usedMode()
            ? PlotI18n.tr("plugin.road.uniform_elevation_strategy_mode")
            : PlotI18n.tr("plugin.road.uniform_elevation_strategy_average");
        return PlotI18n.tr(
            "plugin.road.uniform_elevation_preview",
            recommendation.elevation(),
            strategy,
            recommendation.sampleCount(),
            String.format("%.1f", recommendation.average()));
    }
}
