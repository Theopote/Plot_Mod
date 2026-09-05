package com.plot.plugin.building.generation.resolve;

import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.site.BuildingSiteAnalysis;
import com.plot.plugin.building.site.BuildingSiteColumnSample;

import java.util.Map;

/**
 * 原始 {@link BuildingDefinition} 经 Massing / Site / Material 解析后的生成输入。
 * <p>
 * 刻意不包含 Facade 边映射、Roof skeleton、Accessory 几何——那些留在各 Stage。
 */
public final class ResolvedBuildingDefinition {
    private final BuildingDefinition definition;
    private final MassingGeometryResolver.ResolvedMassingGeometry massing;
    private final BuildingSiteAnalysis siteAnalysis;
    private final GenerationSiteResolver.ResolvedSiteElevation site;
    private final MaterialResolver.ResolvedMaterials materials;
    private final Map<Long, BuildingSiteColumnSample> siteColumnSamples;

    public ResolvedBuildingDefinition(
            BuildingDefinition definition,
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            GenerationSiteResolver.ResolvedSiteElevation site,
            MaterialResolver.ResolvedMaterials materials) {
        this(
            definition,
            massing,
            BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
            site,
            materials,
            Map.of());
    }

    public ResolvedBuildingDefinition(
            BuildingDefinition definition,
            MassingGeometryResolver.ResolvedMassingGeometry massing,
            BuildingSiteAnalysis siteAnalysis,
            GenerationSiteResolver.ResolvedSiteElevation site,
            MaterialResolver.ResolvedMaterials materials,
            Map<Long, BuildingSiteColumnSample> siteColumnSamples) {
        this.definition = definition;
        this.massing = massing;
        this.siteAnalysis = siteAnalysis != null
            ? siteAnalysis
            : BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);
        this.site = site;
        this.materials = materials;
        this.siteColumnSamples = siteColumnSamples == null || siteColumnSamples.isEmpty()
            ? Map.of()
            : Map.copyOf(siteColumnSamples);
    }

    public BuildingDefinition definition() {
        return definition;
    }

    public MassingGeometryResolver.ResolvedMassingGeometry massing() {
        return massing;
    }

    public BuildingSiteAnalysis siteAnalysis() {
        return siteAnalysis;
    }

    public GenerationSiteResolver.ResolvedSiteElevation site() {
        return site;
    }

    public MaterialResolver.ResolvedMaterials materials() {
        return materials;
    }

    public Map<Long, BuildingSiteColumnSample> siteColumnSamples() {
        return siteColumnSamples;
    }

    public boolean isValid() {
        return definition != null && massing != null && massing.valid();
    }
}
