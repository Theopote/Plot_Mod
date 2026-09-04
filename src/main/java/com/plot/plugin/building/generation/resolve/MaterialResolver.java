package com.plot.plugin.building.generation.resolve;

import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.model.spec.BuildingDefinition;

/**
 * 将 Definition 中的材料引用解析为生成用 block id。
 */
public final class MaterialResolver {
    private MaterialResolver() {
    }

    public record ResolvedMaterials(
            String foundationFillBlockId,
            String roofBlockId) {
    }

    public static ResolvedMaterials resolve(BuildingDefinition definition) {
        if (definition == null) {
            return new ResolvedMaterials("minecraft:dirt", "minecraft:oak_planks");
        }
        return new ResolvedMaterials(
            BuildingGeometryUtils.resolveBlockId(definition.foundation().fillMaterial()),
            BuildingGeometryUtils.resolveBlockId(definition.roof().material())
        );
    }
}
