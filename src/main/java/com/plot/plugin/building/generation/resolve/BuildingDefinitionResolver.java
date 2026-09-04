package com.plot.plugin.building.generation.resolve;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;

/**
 * Footprint → 原始 {@link BuildingDefinition}（不含场地/体素几何解析）。
 */
public final class BuildingDefinitionResolver {
    private BuildingDefinitionResolver() {
    }

    public static BuildingDefinition fromFootprint(BuildingFootprint footprint) {
        return BuildingDefinitionMapper.fromFootprint(footprint);
    }

    public static BuildingFootprint footprintForTesting(BuildingDefinition definition) {
        BuildingFootprint footprint = new BuildingFootprint(
            definition.footprint().id(),
            com.plot.plugin.building.BuildingGeometryUtils.copyPoints(definition.footprint().outerPoints()),
            definition.footprint().rectangular());
        BuildingDefinitionMapper.applyMassingEnvelopeFacadeRoofFoundation(definition, footprint);
        return footprint;
    }
}
