package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * 桥梁结构（桥墩 / 桥面护栏）网络默认与单路覆盖控件。
 */
public final class BridgeStructureControls {

    private BridgeStructureControls() {
    }

    public static void renderNetworkDefaults(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        ImGui.textWrapped(PlotI18n.tr("plugin.road.bridge_structure_defaults_hint"));
        ImGui.spacing();

        ImBoolean pillars = new ImBoolean(config.isGenerateBridgePillars());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.generate_bridge_pillars") + "##cfg_bridge_pillars", pillars)) {
            config.setGenerateBridgePillars(pillars.get());
            markCustom(ctx, config);
        }
        if (pillars.get()) {
            float[] spacing = {(float) config.getBridgePierSpacingBlocks()};
            if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.bridge_pier_spacing_label") + "##cfg_pier_spacing",
                spacing,
                (float) RoadParameterLimits.MIN_BRIDGE_PIER_SPACING_BLOCKS,
                (float) RoadParameterLimits.MAX_BRIDGE_PIER_SPACING_BLOCKS,
                "%.1f")) {
                config.setBridgePierSpacingBlocks(spacing[0]);
                markCustom(ctx, config);
            }
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##cfg_pier_material",
                PlotI18n.tr("plugin.road.bridge_pier_material"),
                config.getBridgePierMaterial(),
                material -> {
                    config.setBridgePierMaterial(material);
                    markCustom(ctx, config);
                },
                false);
        }

        ImBoolean guardrail = new ImBoolean(config.isIncludeBridgeGuardrail());
        if (ImGui.checkbox(
            PlotI18n.tr("plugin.road.include_bridge_guardrail") + "##cfg_bridge_guardrail",
            guardrail)) {
            config.setIncludeBridgeGuardrail(guardrail.get());
            markCustom(ctx, config);
        }
        ImGui.textDisabled(PlotI18n.tr("plugin.road.bridge_guardrail_vs_facility_hint"));
        if (guardrail.get()) {
            int[] height = {config.getBridgeGuardrailHeightBlocks()};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.bridge_guardrail_height_label") + "##cfg_guardrail_height",
                height,
                RoadParameterLimits.MIN_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
                RoadParameterLimits.MAX_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
                "%d")) {
                config.setBridgeGuardrailHeightBlocks(height[0]);
                markCustom(ctx, config);
            }
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##cfg_guardrail_material",
                PlotI18n.tr("plugin.road.bridge_guardrail_material"),
                config.getBridgeGuardrailMaterial(),
                material -> {
                    config.setBridgeGuardrailMaterial(material);
                    markCustom(ctx, config);
                },
                false);
        }
    }

    public static void renderRoadOverride(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ResolvedBridgeStructure resolved = ResolvedBridgeStructure.resolve(road, config);

        ImGui.textWrapped(PlotI18n.tr("plugin.road.bridge_structure_road_hint"));
        ImGui.spacing();

        renderBooleanOverride(
            ctx,
            road,
            onHistory,
            "generate_bridge_pillars",
            PlotI18n.tr("plugin.road.generate_bridge_pillars"),
            road.getGenerateBridgePillars(),
            resolved.generatePillars(),
            road::setGenerateBridgePillars);

        if (resolved.generatePillars()) {
            renderSpacingOverride(ctx, road, onHistory, config, resolved);
            renderMaterialOverride(
                ctx,
                road,
                onHistory,
                "pier_material",
                PlotI18n.tr("plugin.road.bridge_pier_material"),
                road.getBridgePierMaterial(),
                resolved.pierMaterial(),
                road::setBridgePierMaterial);
        }

        renderBooleanOverride(
            ctx,
            road,
            onHistory,
            "include_bridge_guardrail",
            PlotI18n.tr("plugin.road.include_bridge_guardrail"),
            road.getIncludeBridgeGuardrail(),
            resolved.includeGuardrail(),
            road::setIncludeBridgeGuardrail);
        ImGui.textDisabled(PlotI18n.tr("plugin.road.bridge_guardrail_vs_facility_hint"));

        if (resolved.includeGuardrail()) {
            renderHeightOverride(ctx, road, onHistory, config, resolved);
            renderMaterialOverride(
                ctx,
                road,
                onHistory,
                "guardrail_material",
                PlotI18n.tr("plugin.road.bridge_guardrail_material"),
                road.getBridgeGuardrailMaterial(),
                resolved.guardrailMaterial(),
                road::setBridgeGuardrailMaterial);
        }
    }

    private static void renderBooleanOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            String fieldId,
            String label,
            Boolean stored,
            boolean effective,
            java.util.function.Consumer<Boolean> setter) {
        boolean inherited = stored == null;
        ImBoolean value = new ImBoolean(effective);
        if (ImGui.checkbox(label + "##road_" + fieldId + "_" + road.getId(), value)) {
            pushHistory(onHistory);
            setter.accept(value.get());
            ctx.onGenerationConfigChanged();
        }
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr(effective
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            "reset_" + fieldId + "_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                setter.accept(null);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderSpacingOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            RoadSystemConfig config,
            ResolvedBridgeStructure resolved) {
        boolean inherited = road.getBridgePierSpacingBlocks() == null;
        float[] spacing = {(float) resolved.pierSpacingBlocks()};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.bridge_pier_spacing_label")
                + "##road_pier_spacing_" + road.getId(),
            spacing,
            (float) RoadParameterLimits.MIN_BRIDGE_PIER_SPACING_BLOCKS,
            (float) RoadParameterLimits.MAX_BRIDGE_PIER_SPACING_BLOCKS,
            "%.1f")) {
            pushHistory(onHistory);
            road.setBridgePierSpacingBlocks((double) spacing[0]);
            ctx.onGenerationConfigChanged();
        }
        double inheritedValue = config != null
            ? config.getBridgePierSpacingBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_PIER_SPACING_BLOCKS;
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr("plugin.road.inherit_default_percent", String.format("%.1f", inheritedValue)),
            "reset_pier_spacing_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                road.setBridgePierSpacingBlocks(null);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderHeightOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            RoadSystemConfig config,
            ResolvedBridgeStructure resolved) {
        boolean inherited = road.getBridgeGuardrailHeightBlocks() == null;
        int[] height = {resolved.guardrailHeightBlocks()};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.bridge_guardrail_height_label")
                + "##road_guardrail_height_" + road.getId(),
            height,
            RoadParameterLimits.MIN_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
            RoadParameterLimits.MAX_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
            "%d")) {
            pushHistory(onHistory);
            road.setBridgeGuardrailHeightBlocks(height[0]);
            ctx.onGenerationConfigChanged();
        }
        int inheritedValue = config != null
            ? config.getBridgeGuardrailHeightBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS;
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr("plugin.road.inherit_default_int", inheritedValue),
            "reset_guardrail_height_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                road.setBridgeGuardrailHeightBlocks(null);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderMaterialOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            String fieldId,
            String label,
            String stored,
            String effective,
            java.util.function.Consumer<String> setter) {
        boolean inherited = stored == null || stored.isBlank();
        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##road_" + fieldId + "_" + road.getId(),
            label,
            effective,
            material -> {
                pushHistory(onHistory);
                setter.accept(material);
                ctx.onGenerationConfigChanged();
            },
            false);
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr("plugin.road.inherit_default_material", effective),
            "reset_" + fieldId + "_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                setter.accept(null);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void pushHistory(Runnable onHistory) {
        if (onHistory != null) {
            onHistory.run();
        }
    }

    private static void markCustom(RoadUiContext ctx, RoadSystemConfig config) {
        config.markCustom();
        ctx.onGenerationConfigChanged();
    }
}
