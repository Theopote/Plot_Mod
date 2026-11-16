package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.function.Consumer;

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
            renderPierStyleCombo(
                "##cfg_pier_style",
                config.getBridgePierStyle(),
                style -> {
                    config.setBridgePierStyle(style);
                    markCustom(ctx, config);
                });
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

    /** 批量草稿：写入 {@link RoadNetworkManager#updateBatchEditDraft}。 */
    public static void renderBatch(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        if (draft == null) {
            return;
        }
        renderBatchWorking(ctx, draft, updated -> ctx.networkManager().updateBatchEditDraft(updated));
    }

    /** 浮动批量窗：直接改写工作副本。 */
    public static void renderBatchWorking(
            RoadUiContext ctx,
            RoadNetworkManager.BatchEditDefaults draft,
            Consumer<RoadNetworkManager.BatchEditDefaults> onChanged) {
        if (draft == null || onChanged == null) {
            return;
        }
        ImGui.textWrapped(PlotI18n.tr("plugin.road.bridge_structure_batch_hint"));
        ImGui.spacing();

        RoadNetworkManager.BatchEditDefaults current = draft;
        ImBoolean pillars = new ImBoolean(current.generateBridgePillars());
        if (ImGui.checkbox(
            PlotI18n.tr("plugin.road.generate_bridge_pillars") + "##batch_bridge_pillars",
            pillars)) {
            current = current.withBridgeStructure(
                pillars.get(),
                current.bridgePierSpacingBlocks(),
                current.bridgePierMaterial(),
                current.bridgePierStyle(),
                current.includeBridgeGuardrail(),
                current.bridgeGuardrailMaterial(),
                current.bridgeGuardrailHeightBlocks());
            onChanged.accept(current);
        }
        if (current.generateBridgePillars()) {
            float[] spacing = {(float) current.bridgePierSpacingBlocks()};
            if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.bridge_pier_spacing_label") + "##batch_pier_spacing",
                spacing,
                (float) RoadParameterLimits.MIN_BRIDGE_PIER_SPACING_BLOCKS,
                (float) RoadParameterLimits.MAX_BRIDGE_PIER_SPACING_BLOCKS,
                "%.1f")) {
                current = current.withBridgeStructure(
                    current.generateBridgePillars(),
                    spacing[0],
                    current.bridgePierMaterial(),
                    current.bridgePierStyle(),
                    current.includeBridgeGuardrail(),
                    current.bridgeGuardrailMaterial(),
                    current.bridgeGuardrailHeightBlocks());
                onChanged.accept(current);
            }
            RoadNetworkManager.BatchEditDefaults styleBase = current;
            renderPierStyleCombo(
                "##batch_pier_style",
                current.bridgePierStyle(),
                style -> onChanged.accept(styleBase.withBridgeStructure(
                    styleBase.generateBridgePillars(),
                    styleBase.bridgePierSpacingBlocks(),
                    styleBase.bridgePierMaterial(),
                    style,
                    styleBase.includeBridgeGuardrail(),
                    styleBase.bridgeGuardrailMaterial(),
                    styleBase.bridgeGuardrailHeightBlocks())));
            if (ctx != null) {
                RoadNetworkManager.BatchEditDefaults materialBase = current;
                RoadUiWidgets.renderBlockMaterialPicker(
                    ctx,
                    "##batch_pier_material",
                    PlotI18n.tr("plugin.road.bridge_pier_material"),
                    current.bridgePierMaterial(),
                    material -> onChanged.accept(materialBase.withBridgeStructure(
                        materialBase.generateBridgePillars(),
                        materialBase.bridgePierSpacingBlocks(),
                        material,
                        materialBase.bridgePierStyle(),
                        materialBase.includeBridgeGuardrail(),
                        materialBase.bridgeGuardrailMaterial(),
                        materialBase.bridgeGuardrailHeightBlocks())),
                    false);
            }
        }

        ImBoolean guardrail = new ImBoolean(current.includeBridgeGuardrail());
        if (ImGui.checkbox(
            PlotI18n.tr("plugin.road.include_bridge_guardrail") + "##batch_bridge_guardrail",
            guardrail)) {
            current = current.withBridgeStructure(
                current.generateBridgePillars(),
                current.bridgePierSpacingBlocks(),
                current.bridgePierMaterial(),
                current.bridgePierStyle(),
                guardrail.get(),
                current.bridgeGuardrailMaterial(),
                current.bridgeGuardrailHeightBlocks());
            onChanged.accept(current);
        }
        ImGui.textDisabled(PlotI18n.tr("plugin.road.bridge_guardrail_vs_facility_hint"));
        if (current.includeBridgeGuardrail()) {
            int[] height = {current.bridgeGuardrailHeightBlocks()};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.bridge_guardrail_height_label") + "##batch_guardrail_height",
                height,
                RoadParameterLimits.MIN_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
                RoadParameterLimits.MAX_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
                "%d")) {
                current = current.withBridgeStructure(
                    current.generateBridgePillars(),
                    current.bridgePierSpacingBlocks(),
                    current.bridgePierMaterial(),
                    current.bridgePierStyle(),
                    current.includeBridgeGuardrail(),
                    current.bridgeGuardrailMaterial(),
                    height[0]);
                onChanged.accept(current);
            }
            if (ctx != null) {
                RoadNetworkManager.BatchEditDefaults materialBase = current;
                RoadUiWidgets.renderBlockMaterialPicker(
                    ctx,
                    "##batch_guardrail_material",
                    PlotI18n.tr("plugin.road.bridge_guardrail_material"),
                    current.bridgeGuardrailMaterial(),
                    material -> onChanged.accept(materialBase.withBridgeStructure(
                        materialBase.generateBridgePillars(),
                        materialBase.bridgePierSpacingBlocks(),
                        materialBase.bridgePierMaterial(),
                        materialBase.bridgePierStyle(),
                        materialBase.includeBridgeGuardrail(),
                        material,
                        materialBase.bridgeGuardrailHeightBlocks())),
                    false);
            }
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
            renderPierStyleOverride(ctx, road, onHistory, config, resolved);
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

    private static void renderPierStyleOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            RoadSystemConfig config,
            ResolvedBridgeStructure resolved) {
        boolean inherited = road.getBridgePierStyle() == null;
        renderPierStyleCombo(
            "##road_pier_style_" + road.getId(),
            resolved.pierStyle(),
            style -> {
                pushHistory(onHistory);
                road.setBridgePierStyle(style);
                ctx.onGenerationConfigChanged();
            });
        BridgePierStyle inheritedValue = config != null ? config.getBridgePierStyle() : BridgePierStyle.AUTO;
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr("plugin.road.inherit_default_material", pierStyleLabel(inheritedValue)),
            "reset_pier_style_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                road.setBridgePierStyle(null);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderPierStyleCombo(
            String idSuffix,
            BridgePierStyle current,
            java.util.function.Consumer<BridgePierStyle> onChanged) {
        BridgePierStyle[] styles = BridgePierStyle.values();
        String[] labels = new String[styles.length];
        int selected = 0;
        for (int i = 0; i < styles.length; i++) {
            labels[i] = pierStyleLabel(styles[i]);
            if (styles[i] == current) {
                selected = i;
            }
        }
        ImInt index = new ImInt(selected);
        if (ImGui.combo(
            PlotI18n.tr("plugin.road.bridge_pier_style_label") + idSuffix,
            index,
            labels)) {
            onChanged.accept(styles[index.get()]);
        }
    }

    private static String pierStyleLabel(BridgePierStyle style) {
        BridgePierStyle value = style != null ? style : BridgePierStyle.AUTO;
        return PlotI18n.tr("plugin.road.bridge_pier_style." + value.name().toLowerCase());
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
