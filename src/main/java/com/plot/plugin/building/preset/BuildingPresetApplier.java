package com.plot.plugin.building.preset;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.model.spec.FootprintSpec;

import java.util.Objects;

/**
 * 将 Preset 默认参数应用到现有 {@link BuildingFootprint}（保留 id、名称与轮廓几何）。
 */
public final class BuildingPresetApplier {
    private BuildingPresetApplier() {
    }

    public static void apply(String presetId, BuildingFootprint footprint) {
        Objects.requireNonNull(footprint, "footprint");
        BuildingDefinition definition = BuildingPresetCatalog.buildDefinition(
            presetId,
            FootprintSpec.from(footprint));
        BuildingDefinitionMapper.applyMassingEnvelopeFacadeRoofFoundation(definition, footprint);
        footprint.setPresetId(presetId);
    }

    public static BuildingDefinition previewDefinition(String presetId, BuildingFootprint footprint) {
        return BuildingPresetCatalog.buildDefinition(presetId, FootprintSpec.from(footprint));
    }
}
