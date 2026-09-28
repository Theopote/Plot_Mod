package com.plot.plugin.road.ui;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatElevationCandidate;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.FlatElevationRecommendation;
import com.plot.plugin.road.vertical.FlatElevationRecommendationSignature;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.world.World;

import java.util.function.Supplier;

/** Caches derived flat elevation analysis; invalidated when the road path changes. */
final class FlatElevationRecommendationSession {
    private String cachedRoadId = "";
    private int cachedContextSignature = Integer.MIN_VALUE;
    private int cachedTerrainFingerprint = Integer.MIN_VALUE;
    private FlatElevationRecommendation recommendation = FlatElevationRecommendation.empty();
    private boolean stale = true;

    FlatElevationRecommendation recommendation() {
        return recommendation;
    }

    boolean isStale(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            Supplier<TerrainSampler> terrainSupplier) {
        if (road == null || config == null) {
            return true;
        }
        if (!road.getId().equals(cachedRoadId)) {
            return true;
        }
        if (stale) {
            return true;
        }
        if (cachedContextSignature != FlatElevationRecommendationSignature.contextSignature(
                network, road, config)) {
            return true;
        }
        if (terrainSupplier == null) {
            return false;
        }
        TerrainSampler terrain = terrainSupplier.get();
        if (terrain == null) {
            return false;
        }
        return cachedTerrainFingerprint != FlatElevationRecommendationSignature.terrainFingerprint(
            network, road, terrain, config);
    }

    FlatElevationRecommendation compute(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Supplier<TerrainSampler> terrainSupplier) {
        TerrainSampler terrain = terrainSupplier != null ? terrainSupplier.get() : null;
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (terrain == null || config == null || !RoadStationing.isStationable(network, road)) {
            recommendation = FlatElevationRecommendation.empty();
            stale = true;
            return recommendation;
        }
        recommendation = FlatVerticalIntentSupport.recommendOptimizedElevation(
            network, road, terrain, config);
        cachedRoadId = road.getId();
        cachedContextSignature = FlatElevationRecommendationSignature.contextSignature(
            network, road, config);
        cachedTerrainFingerprint = FlatElevationRecommendationSignature.terrainFingerprint(
            network, road, terrain, config);
        stale = false;
        return recommendation;
    }

    void markStale() {
        stale = true;
    }
}

/** Path / Generate controls for flat elevation optimization (v1.1). */
final class FlatElevationRecommendationUi {
    private final FlatElevationRecommendationSession session = new FlatElevationRecommendationSession();

    void renderPathControls(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Runnable onHistory,
            Supplier<TerrainSampler> terrainSupplier) {
        if (RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return;
        }
        renderStatus(ctx, network, road, terrainSupplier, false);
        if (ImGui.button(PlotI18n.tr("plugin.road.flat_elevation.compute_recommendation"),
                ImGui.getContentRegionAvailX(), 0)) {
            session.compute(ctx, network, road, terrainSupplier);
        }
        FlatElevationRecommendation recommendation = session.recommendation();
        if (!recommendation.hasRecommendation()) {
            return;
        }
        FlatElevationCandidate best = recommendation.best();
        ImGui.text(PlotI18n.tr(
            "plugin.road.flat_elevation.recommended_y",
            best.elevation(),
            best.estimatedEarthworkBlocks()));
        if (ImGui.button(PlotI18n.tr("plugin.road.flat_elevation.adopt_recommended"),
                ImGui.getContentRegionAvailX(), 0)) {
            applyElevation(ctx, network, road, best.elevation(), onHistory);
            session.markStale();
        }
    }

    void renderGenerateControls(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Runnable onHistory,
            Supplier<TerrainSampler> terrainSupplier) {
        if (RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return;
        }
        ImGui.spacing();
        RoadUiSections.group("plugin.road.flat_elevation.analysis");
        renderStatus(ctx, network, road, terrainSupplier, true);
        if (ImGui.button(PlotI18n.tr("plugin.road.flat_elevation.compute_recommendation"))) {
            session.compute(ctx, network, road, terrainSupplier);
        }
        FlatElevationRecommendation recommendation = session.recommendation();
        if (!recommendation.hasRecommendation()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.flat_elevation.no_recommendation_hint"));
            return;
        }
        FlatElevationCandidate best = recommendation.best();
        ImGui.text(PlotI18n.tr("plugin.road.flat_elevation.recommended_summary", best.elevation()));
        ImGui.text(PlotI18n.tr(
            "plugin.road.flat_elevation.estimated_cut_fill",
            best.estimatedCutVolume(),
            best.estimatedFillVolume()));
        ImGui.text(PlotI18n.tr(
            "plugin.road.flat_elevation.estimated_structures",
            best.estimatedBridgeLength(),
            best.estimatedTunnelLength(),
            best.estimatedEarthworkBlocks()));
        if (ImGui.button(PlotI18n.tr("plugin.road.flat_elevation.adopt_recommended"))) {
            applyElevation(ctx, network, road, best.elevation(), onHistory);
            session.markStale();
        }
        if (!recommendation.alternatives().isEmpty()) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.road.flat_elevation.alternatives"));
            for (FlatElevationCandidate candidate : recommendation.alternatives()) {
                ImGui.bulletText(PlotI18n.tr(
                    "plugin.road.flat_elevation.alternative_row",
                    candidate.elevation(),
                    candidate.estimatedEarthworkBlocks(),
                    candidate.estimatedBridgeLength(),
                    candidate.estimatedTunnelLength()));
                ImGui.sameLine();
                if (ImGui.smallButton(PlotI18n.tr("plugin.road.flat_elevation.adopt_y",
                        candidate.elevation()) + "##flat_alt_" + candidate.elevation())) {
                    applyElevation(ctx, network, road, candidate.elevation(), onHistory);
                    session.markStale();
                }
            }
        }
    }

    FlatElevationProfileOverlay profileOverlay(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        Integer suggested = suggestedElevationOverlay(network, road, config);
        if (suggested == null) {
            return FlatElevationProfileOverlay.EMPTY;
        }
        Integer current = null;
        var intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        if (intent != null) {
            current = (int) Math.round(intent.getBaseElevation());
        }
        return FlatElevationProfileOverlay.of(current, suggested);
    }

    Integer suggestedElevationOverlay(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (config == null
                || session.isStale(network, road, config, null)
                || !session.recommendation().hasRecommendation()) {
            return null;
        }
        return session.recommendation().best().elevation();
    }

    private void renderStatus(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Supplier<TerrainSampler> terrainSupplier,
            boolean verbose) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (session.isStale(network, road, config, terrainSupplier)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                verbose
                    ? PlotI18n.tr("plugin.road.flat_elevation.stale_hint")
                    : PlotI18n.tr("plugin.road.flat_elevation.not_computed_hint"));
        }
    }

    private static void applyElevation(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            double elevation,
            Runnable onHistory) {
        if (onHistory != null) {
            onHistory.run();
        }
        FlatVerticalIntentSupport.setBaseElevation(
            network,
            road,
            elevation,
            road.getEffectiveMaxSlope(ctx.networkManager().getConfig()));
        ctx.onGenerationConfigChanged();
    }

    static Supplier<TerrainSampler> terrainSupplier(RoadUiContext ctx) {
        return () -> {
            World world = RoadNetworkGenerator.getClientWorld();
            if (world == null) {
                ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
                return null;
            }
            return MinecraftTerrainSampler.of(world, ctx.host().coordinates());
        };
    }
}
