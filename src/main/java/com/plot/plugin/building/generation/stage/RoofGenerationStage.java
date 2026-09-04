package com.plot.plugin.building.generation.stage;

import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingRoofGenerator;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.RoofSpec;

/**
 * 屋顶阶段：解析有效屋顶类型，并在坡屋顶时调用 BuildingRoofGenerator。
 */
public final class RoofGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "roof";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();

        BuildingFootprint.RoofType roofType = resolveRoofType(definition, context.getOuterPoints(), result);
        result.effectiveRoofType = roofType;

        if (roofType != BuildingFootprint.RoofType.FLAT) {
            RoofSpec roof = definition.roof();
            BuildingRoofGenerator.generate(
                result,
                context.getOuterPoints(),
                context.getTopFloorY(),
                context.getRoofBlockId(),
                roofType,
                roof.pitchRatio(),
                context.getCoordinateService(),
                context.getProjectionService());
        }
    }

    public static BuildingFootprint.RoofType resolveRoofType(
            BuildingDefinition definition,
            java.util.List<com.plot.api.geometry.Vec2d> outerPoints,
            BuildingGenerationResult result) {
        RoofSpec roof = definition.roof();
        BuildingFootprint.RoofType requested = roof.type();
        if (requested == BuildingFootprint.RoofType.FLAT) {
            return BuildingFootprint.RoofType.FLAT;
        }
        if (BuildingGeometryUtils.isSlopedRoofEligible(outerPoints)) {
            return requested;
        }
        result.warnings.add("plugin.building.warn.roof_downgrade");
        return BuildingFootprint.RoofType.FLAT;
    }

    /**
     * @deprecated 使用 {@link #resolveRoofType(BuildingDefinition, java.util.List, BuildingGenerationResult)}
     */
    @Deprecated
    public static BuildingFootprint.RoofType resolveRoofType(
            BuildingFootprint footprint,
            BuildingGenerationResult result) {
        return resolveRoofType(BuildingDefinition.fromFootprint(footprint), footprint.getOuterPoints(), result);
    }
}
