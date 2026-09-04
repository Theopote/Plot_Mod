package com.plot.plugin.building.generation.resolve;

import com.plot.plugin.building.model.spec.BuildingDefinition;

/**
 * 原始 {@link BuildingDefinition} 经 Massing / Site / Material 解析后的生成输入。
 * <p>
 * 刻意不包含 Facade 边映射、Roof skeleton、Accessory 几何——那些留在各 Stage。
 */
public final class ResolvedBuildingDefinition {
    private final BuildingDefinition definition;
    private final MassingGeometryResolver.ResolvedMassingGeometry massing;
    private final GenerationSiteResolver.ResolvedSiteElevation site;
    private final MaterialResolver.ResolvedMaterials materials;

    public ResolvedBuildingDefinition(
            BuildingDefinition definition,
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            GenerationSiteResolver.ResolvedSiteElevation site,
            MaterialResolver.ResolvedMaterials materials) {
        this.definition = definition;
        this.massing = massing;
        this.site = site;
        this.materials = materials;
    }

    public BuildingDefinition definition() {
        return definition;
    }

    public MassingGeometryResolver.ResolvedMassingGeometry massing() {
        return massing;
    }

    public GenerationSiteResolver.ResolvedSiteElevation site() {
        return site;
    }

    public MaterialResolver.ResolvedMaterials materials() {
        return materials;
    }

    public boolean isValid() {
        return definition != null && massing != null && massing.valid();
    }
}
