package com.plot.plugin.road.pipeline.crosssection;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;

/**
 * Immutable effective bridge pier/guardrail settings used during generation.
 */
public record ResolvedBridgeStructure(
        boolean generatePillars,
        double pierSpacingBlocks,
        String pierMaterial,
        boolean includeGuardrail,
        String guardrailMaterial,
        int guardrailHeightBlocks) {

    public static final String DEFAULT_PIER_MATERIAL = "minecraft:stone_bricks";
    public static final String DEFAULT_GUARDRAIL_MATERIAL = "minecraft:oak_fence";

    public ResolvedBridgeStructure {
        pierSpacingBlocks = RoadParameterLimits.clampBridgePierSpacingBlocks(pierSpacingBlocks);
        pierMaterial = normalizeMaterial(pierMaterial, DEFAULT_PIER_MATERIAL);
        guardrailMaterial = normalizeMaterial(guardrailMaterial, DEFAULT_GUARDRAIL_MATERIAL);
        guardrailHeightBlocks = RoadParameterLimits.clampBridgeGuardrailHeightBlocks(guardrailHeightBlocks);
    }

    public static ResolvedBridgeStructure defaults() {
        return fromConfig(null);
    }

    public static ResolvedBridgeStructure fromConfig(RoadSystemConfig config) {
        if (config == null) {
            return new ResolvedBridgeStructure(
                true,
                RoadParameterLimits.DEFAULT_BRIDGE_PIER_SPACING_BLOCKS,
                DEFAULT_PIER_MATERIAL,
                false,
                DEFAULT_GUARDRAIL_MATERIAL,
                RoadParameterLimits.DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS);
        }
        return new ResolvedBridgeStructure(
            config.isGenerateBridgePillars(),
            config.getBridgePierSpacingBlocks(),
            config.getBridgePierMaterial(),
            config.isIncludeBridgeGuardrail(),
            config.getBridgeGuardrailMaterial(),
            config.getBridgeGuardrailHeightBlocks());
    }

    public static ResolvedBridgeStructure resolve(Road road, RoadSystemConfig config) {
        ResolvedBridgeStructure inherited = fromConfig(config);
        if (road == null) {
            return inherited;
        }
        return new ResolvedBridgeStructure(
            road.getGenerateBridgePillars() != null
                ? road.getGenerateBridgePillars()
                : inherited.generatePillars(),
            road.getBridgePierSpacingBlocks() != null
                ? road.getBridgePierSpacingBlocks()
                : inherited.pierSpacingBlocks(),
            road.getBridgePierMaterial() != null && !road.getBridgePierMaterial().isBlank()
                ? road.getBridgePierMaterial()
                : inherited.pierMaterial(),
            road.getIncludeBridgeGuardrail() != null
                ? road.getIncludeBridgeGuardrail()
                : inherited.includeGuardrail(),
            road.getBridgeGuardrailMaterial() != null && !road.getBridgeGuardrailMaterial().isBlank()
                ? road.getBridgeGuardrailMaterial()
                : inherited.guardrailMaterial(),
            road.getBridgeGuardrailHeightBlocks() != null
                ? road.getBridgeGuardrailHeightBlocks()
                : inherited.guardrailHeightBlocks());
    }

    private static String normalizeMaterial(String material, String fallback) {
        return material == null || material.isBlank() ? fallback : material;
    }
}
