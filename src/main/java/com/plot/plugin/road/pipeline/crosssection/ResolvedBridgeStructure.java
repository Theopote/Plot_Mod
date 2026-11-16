package com.plot.plugin.road.pipeline.crosssection;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.BridgePierStyle;

/**
 * Immutable effective bridge pier/guardrail settings used during generation and preview.
 */
public record ResolvedBridgeStructure(
        boolean generatePillars,
        double pierSpacingBlocks,
        String pierMaterial,
        BridgePierStyle pierStyle,
        int pierDiameterBlocks,
        int pierOffsetBlocks,
        boolean includeGuardrail,
        String guardrailMaterial,
        int guardrailHeightBlocks) {

    public static final String DEFAULT_PIER_MATERIAL = "minecraft:stone_bricks";
    public static final String DEFAULT_GUARDRAIL_MATERIAL = "minecraft:oak_fence";

    public ResolvedBridgeStructure {
        pierSpacingBlocks = RoadParameterLimits.clampBridgePierSpacingBlocks(pierSpacingBlocks);
        pierMaterial = normalizeMaterial(pierMaterial, DEFAULT_PIER_MATERIAL);
        pierStyle = pierStyle != null ? pierStyle : BridgePierStyle.SINGLE;
        pierDiameterBlocks = RoadParameterLimits.clampBridgePierDiameterStored(pierDiameterBlocks);
        pierOffsetBlocks = Math.max(1, pierOffsetBlocks);
        guardrailMaterial = normalizeMaterial(guardrailMaterial, DEFAULT_GUARDRAIL_MATERIAL);
        guardrailHeightBlocks = RoadParameterLimits.clampBridgeGuardrailHeightBlocks(guardrailHeightBlocks);
    }

    public static ResolvedBridgeStructure defaults() {
        return fromConfig(null, RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH);
    }

    public static ResolvedBridgeStructure fromConfig(RoadSystemConfig config) {
        return fromConfig(config, RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH);
    }

    public static ResolvedBridgeStructure fromConfig(RoadSystemConfig config, int carriagewayWidth) {
        if (config == null) {
            return clampToCarriageway(
                new ResolvedBridgeStructure(
                    true,
                    RoadParameterLimits.DEFAULT_BRIDGE_PIER_SPACING_BLOCKS,
                    DEFAULT_PIER_MATERIAL,
                    BridgePierStyle.SINGLE,
                    RoadParameterLimits.DEFAULT_BRIDGE_PIER_DIAMETER_BLOCKS,
                    RoadParameterLimits.DEFAULT_BRIDGE_PIER_OFFSET_BLOCKS,
                    false,
                    DEFAULT_GUARDRAIL_MATERIAL,
                    RoadParameterLimits.DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS),
                carriagewayWidth);
        }
        return clampToCarriageway(
            new ResolvedBridgeStructure(
                config.isGenerateBridgePillars(),
                config.getBridgePierSpacingBlocks(),
                config.getBridgePierMaterial(),
                config.getBridgePierStyle(),
                config.getBridgePierDiameterBlocks(),
                config.getBridgePierOffsetBlocks(),
                config.isIncludeBridgeGuardrail(),
                config.getBridgeGuardrailMaterial(),
                config.getBridgeGuardrailHeightBlocks()),
            carriagewayWidth);
    }

    public static ResolvedBridgeStructure resolve(Road road, RoadSystemConfig config) {
        int carriagewayWidth = resolveCarriagewayWidth(road, config);
        ResolvedBridgeStructure inherited = fromConfig(config, carriagewayWidth);
        if (road == null) {
            return inherited;
        }
        return clampToCarriageway(
            new ResolvedBridgeStructure(
                road.getGenerateBridgePillars() != null
                    ? road.getGenerateBridgePillars()
                    : inherited.generatePillars(),
                road.getBridgePierSpacingBlocks() != null
                    ? road.getBridgePierSpacingBlocks()
                    : inherited.pierSpacingBlocks(),
                road.getBridgePierMaterial() != null && !road.getBridgePierMaterial().isBlank()
                    ? road.getBridgePierMaterial()
                    : inherited.pierMaterial(),
                road.getBridgePierStyle() != null
                    ? road.getBridgePierStyle()
                    : inherited.pierStyle(),
                road.getBridgePierDiameterBlocks() != null
                    ? road.getBridgePierDiameterBlocks()
                    : inherited.pierDiameterBlocks(),
                road.getBridgePierOffsetBlocks() != null
                    ? road.getBridgePierOffsetBlocks()
                    : inherited.pierOffsetBlocks(),
                road.getIncludeBridgeGuardrail() != null
                    ? road.getIncludeBridgeGuardrail()
                    : inherited.includeGuardrail(),
                road.getBridgeGuardrailMaterial() != null && !road.getBridgeGuardrailMaterial().isBlank()
                    ? road.getBridgeGuardrailMaterial()
                    : inherited.guardrailMaterial(),
                road.getBridgeGuardrailHeightBlocks() != null
                    ? road.getBridgeGuardrailHeightBlocks()
                    : inherited.guardrailHeightBlocks()),
            carriagewayWidth);
    }

    public ResolvedBridgeStructure withCarriagewayWidth(int carriagewayWidth) {
        return clampToCarriageway(this, carriagewayWidth);
    }

    private static ResolvedBridgeStructure clampToCarriageway(
            ResolvedBridgeStructure raw,
            int carriagewayWidth) {
        int diameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
            carriagewayWidth, raw.pierDiameterBlocks(), raw.pierStyle());
        int offset = raw.pierStyle() == BridgePierStyle.DOUBLE
            ? RoadParameterLimits.clampBridgePierOffsetBlocks(
                carriagewayWidth, diameter, raw.pierOffsetBlocks())
            : RoadParameterLimits.minDoublePierOffsetBlocks(carriagewayWidth, diameter);
        return new ResolvedBridgeStructure(
            raw.generatePillars(),
            raw.pierSpacingBlocks(),
            raw.pierMaterial(),
            raw.pierStyle(),
            diameter,
            offset,
            raw.includeGuardrail(),
            raw.guardrailMaterial(),
            raw.guardrailHeightBlocks());
    }

    private static int resolveCarriagewayWidth(Road road, RoadSystemConfig config) {
        if (road != null) {
            Integer width = road.getWidth();
            if (width != null) {
                return RoadParameterLimits.clampCarriagewayWidth(width);
            }
            if (config != null) {
                return road.getEffectiveWidth(config);
            }
        }
        if (config != null) {
            return config.getRoadWidth();
        }
        return RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH;
    }

    private static String normalizeMaterial(String material, String fallback) {
        return material == null || material.isBlank() ? fallback : material;
    }
}
