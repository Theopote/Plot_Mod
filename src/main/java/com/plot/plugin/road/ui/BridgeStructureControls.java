package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import com.plot.plugin.ui.PluginUiColors;
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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.bridge_structure_defaults_hint"));
        ImGui.spacing();

        int carriagewayWidth = config.getRoadWidth();
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
            renderDiameterSlider(
                "##cfg_pier_diameter",
                carriagewayWidth,
                config.getBridgePierStyle(),
                config.getBridgePierDiameterBlocks(),
                value -> {
                    config.setBridgePierDiameterBlocks(value);
                    markCustom(ctx, config);
                });
            if (config.getBridgePierStyle() == BridgePierStyle.DOUBLE) {
                renderOffsetSlider(
                    "##cfg_pier_offset",
                    carriagewayWidth,
                    config.getBridgePierDiameterBlocks(),
                    config.getBridgePierOffsetBlocks(),
                    value -> {
                        config.setBridgePierOffsetBlocks(value);
                        markCustom(ctx, config);
                    });
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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.bridge_guardrail_vs_facility_hint"));
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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.bridge_structure_batch_hint"));
        ImGui.spacing();

        int carriagewayWidth = draft.width();
        final RoadNetworkManager.BatchEditDefaults[] current = {draft};
        ImBoolean pillars = new ImBoolean(current[0].generateBridgePillars());
        if (ImGui.checkbox(
            PlotI18n.tr("plugin.road.generate_bridge_pillars") + "##batch_bridge_pillars",
            pillars)) {
            current[0] = withBridge(current[0], b -> b.generate = pillars.get());
            onChanged.accept(current[0]);
        }
        if (current[0].generateBridgePillars()) {
            float[] spacing = {(float) current[0].bridgePierSpacingBlocks()};
            if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.road.bridge_pier_spacing_label") + "##batch_pier_spacing",
                spacing,
                (float) RoadParameterLimits.MIN_BRIDGE_PIER_SPACING_BLOCKS,
                (float) RoadParameterLimits.MAX_BRIDGE_PIER_SPACING_BLOCKS,
                "%.1f")) {
                current[0] = withBridge(current[0], b -> b.spacing = spacing[0]);
                onChanged.accept(current[0]);
            }
            renderPierStyleCombo(
                "##batch_pier_style",
                current[0].bridgePierStyle(),
                style -> {
                    current[0] = withBridge(current[0], b -> b.style = style);
                    onChanged.accept(current[0]);
                });
            renderDiameterSlider(
                "##batch_pier_diameter",
                carriagewayWidth,
                current[0].bridgePierStyle(),
                current[0].bridgePierDiameterBlocks(),
                value -> {
                    current[0] = withBridge(current[0], b -> b.diameter = value);
                    onChanged.accept(current[0]);
                });
            if (current[0].bridgePierStyle() == BridgePierStyle.DOUBLE) {
                renderOffsetSlider(
                    "##batch_pier_offset",
                    carriagewayWidth,
                    current[0].bridgePierDiameterBlocks(),
                    current[0].bridgePierOffsetBlocks(),
                    value -> {
                        current[0] = withBridge(current[0], b -> b.offset = value);
                        onChanged.accept(current[0]);
                    });
            }
            if (ctx != null) {
                RoadUiWidgets.renderBlockMaterialPicker(
                    ctx,
                    "##batch_pier_material",
                    PlotI18n.tr("plugin.road.bridge_pier_material"),
                    current[0].bridgePierMaterial(),
                    material -> {
                        current[0] = withBridge(current[0], b -> b.pierMaterial = material);
                        onChanged.accept(current[0]);
                    },
                    false);
            }
        }

        ImBoolean guardrail = new ImBoolean(current[0].includeBridgeGuardrail());
        if (ImGui.checkbox(
            PlotI18n.tr("plugin.road.include_bridge_guardrail") + "##batch_bridge_guardrail",
            guardrail)) {
            current[0] = withBridge(current[0], b -> b.includeGuardrail = guardrail.get());
            onChanged.accept(current[0]);
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.bridge_guardrail_vs_facility_hint"));
        if (current[0].includeBridgeGuardrail()) {
            int[] height = {current[0].bridgeGuardrailHeightBlocks()};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.bridge_guardrail_height_label") + "##batch_guardrail_height",
                height,
                RoadParameterLimits.MIN_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
                RoadParameterLimits.MAX_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS,
                "%d")) {
                current[0] = withBridge(current[0], b -> b.guardrailHeight = height[0]);
                onChanged.accept(current[0]);
            }
            if (ctx != null) {
                RoadUiWidgets.renderBlockMaterialPicker(
                    ctx,
                    "##batch_guardrail_material",
                    PlotI18n.tr("plugin.road.bridge_guardrail_material"),
                    current[0].bridgeGuardrailMaterial(),
                    material -> {
                        current[0] = withBridge(current[0], b -> b.guardrailMaterial = material);
                        onChanged.accept(current[0]);
                    },
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
        int carriagewayWidth = road.getEffectiveWidth(config);

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
            renderDiameterOverride(ctx, road, onHistory, config, resolved, carriagewayWidth);
            if (resolved.pierStyle() == BridgePierStyle.DOUBLE) {
                renderOffsetOverride(ctx, road, onHistory, config, resolved, carriagewayWidth);
            }
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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.bridge_guardrail_vs_facility_hint"));

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
        BridgePierStyle inheritedValue = config != null ? config.getBridgePierStyle() : BridgePierStyle.SINGLE;
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

    private static void renderDiameterOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            RoadSystemConfig config,
            ResolvedBridgeStructure resolved,
            int carriagewayWidth) {
        boolean inherited = road.getBridgePierDiameterBlocks() == null;
        renderDiameterSlider(
            "##road_pier_diameter_" + road.getId(),
            carriagewayWidth,
            resolved.pierStyle(),
            resolved.pierDiameterBlocks(),
            value -> {
                pushHistory(onHistory);
                road.setBridgePierDiameterBlocks(value);
                ctx.onGenerationConfigChanged();
            });
        int inheritedValue = config != null
            ? config.getBridgePierDiameterBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_PIER_DIAMETER_BLOCKS;
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr("plugin.road.inherit_default_int", inheritedValue),
            "reset_pier_diameter_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                road.setBridgePierDiameterBlocks(null);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderOffsetOverride(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            RoadSystemConfig config,
            ResolvedBridgeStructure resolved,
            int carriagewayWidth) {
        boolean inherited = road.getBridgePierOffsetBlocks() == null;
        renderOffsetSlider(
            "##road_pier_offset_" + road.getId(),
            carriagewayWidth,
            resolved.pierDiameterBlocks(),
            resolved.pierOffsetBlocks(),
            value -> {
                pushHistory(onHistory);
                road.setBridgePierOffsetBlocks(value);
                ctx.onGenerationConfigChanged();
            });
        int inheritedValue = config != null
            ? config.getBridgePierOffsetBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_PIER_OFFSET_BLOCKS;
        RoadUiWidgets.renderOverrideFooter(
            inherited,
            PlotI18n.tr("plugin.road.inherit_default_int", inheritedValue),
            "reset_pier_offset_" + road.getId(),
            () -> {
                pushHistory(onHistory);
                road.setBridgePierOffsetBlocks(null);
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

    private static void renderDiameterSlider(
            String idSuffix,
            int carriagewayWidth,
            BridgePierStyle style,
            int current,
            java.util.function.Consumer<Integer> onChanged) {
        BridgePierStyle pierStyle = style != null ? style : BridgePierStyle.SINGLE;
        int[] options = RoadParameterLimits.validBridgePierDiameters(carriagewayWidth, pierStyle);
        int clamped = RoadParameterLimits.clampBridgePierDiameterBlocks(
            carriagewayWidth, current, pierStyle);
        renderDiscreteIntChoices(
            PlotI18n.tr("plugin.road.bridge_pier_diameter_label") + idSuffix,
            options,
            clamped,
            onChanged,
            PlotI18n.tr("hint.plot.road.bridge_pier_diameter"),
            PlotI18n.tr("plugin.road.bridge_pier_diameter_locked", clamped, carriagewayWidth));
    }

    private static void renderOffsetSlider(
            String idSuffix,
            int carriagewayWidth,
            int diameter,
            int current,
            java.util.function.Consumer<Integer> onChanged) {
        int clampedDiameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
            carriagewayWidth, diameter, BridgePierStyle.DOUBLE);
        int[] options = RoadParameterLimits.validBridgePierOffsets(carriagewayWidth, clampedDiameter);
        int clamped = RoadParameterLimits.clampBridgePierOffsetBlocks(
            carriagewayWidth, clampedDiameter, current);
        int gap = RoadParameterLimits.doublePierGapBlocks(clamped, clampedDiameter);
        renderDiscreteIntChoices(
            PlotI18n.tr("plugin.road.bridge_pier_offset_label") + idSuffix,
            options,
            clamped,
            onChanged,
            PlotI18n.tr("hint.plot.road.bridge_pier_offset", gap),
            PlotI18n.tr("plugin.road.bridge_pier_offset_locked", clamped, gap));
    }

    /**
     * 直径/偏移合法值常为稀疏序列：用下拉选择；仅一个合法值时禁用下拉并提示约束原因，
     * 避免滑条 min==max 时看起来像坏了。
     */
    private static void renderDiscreteIntChoices(
            String label,
            int[] options,
            int current,
            java.util.function.Consumer<Integer> onChanged,
            String multiHint,
            String lockedHint) {
        if (options == null || options.length == 0) {
            return;
        }
        String[] labels = new String[options.length];
        int selected = 0;
        for (int i = 0; i < options.length; i++) {
            labels[i] = Integer.toString(options[i]);
            if (options[i] == current) {
                selected = i;
            }
        }
        boolean locked = options.length == 1;
        if (locked && current != options[0]) {
            onChanged.accept(options[0]);
            selected = 0;
        }
        ImInt index = new ImInt(selected);
        if (locked) {
            ImGui.beginDisabled();
        }
        boolean changed = ImGui.combo(label, index, labels);
        if (locked) {
            ImGui.endDisabled();
        }
        if (changed && !locked) {
            onChanged.accept(options[index.get()]);
        }
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(locked ? lockedHint : multiHint);
        }
    }

    private static String pierStyleLabel(BridgePierStyle style) {
        BridgePierStyle value = style != null ? style : BridgePierStyle.SINGLE;
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

    private static final class BridgeMut {
        boolean generate;
        double spacing;
        String pierMaterial;
        BridgePierStyle style;
        int diameter;
        int offset;
        boolean includeGuardrail;
        String guardrailMaterial;
        int guardrailHeight;
    }

    private static RoadNetworkManager.BatchEditDefaults withBridge(
            RoadNetworkManager.BatchEditDefaults base,
            Consumer<BridgeMut> mutator) {
        BridgeMut mut = new BridgeMut();
        mut.generate = base.generateBridgePillars();
        mut.spacing = base.bridgePierSpacingBlocks();
        mut.pierMaterial = base.bridgePierMaterial();
        mut.style = base.bridgePierStyle();
        mut.diameter = base.bridgePierDiameterBlocks();
        mut.offset = base.bridgePierOffsetBlocks();
        mut.includeGuardrail = base.includeBridgeGuardrail();
        mut.guardrailMaterial = base.bridgeGuardrailMaterial();
        mut.guardrailHeight = base.bridgeGuardrailHeightBlocks();
        mutator.accept(mut);
        return base.withBridgeStructure(
            mut.generate,
            mut.spacing,
            mut.pierMaterial,
            mut.style,
            mut.diameter,
            mut.offset,
            mut.includeGuardrail,
            mut.guardrailMaterial,
            mut.guardrailHeight);
    }
}
