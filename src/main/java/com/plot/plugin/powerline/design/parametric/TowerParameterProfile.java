package com.plot.plugin.powerline.design.parametric;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;

import java.util.List;
import java.util.Map;

public record TowerParameterProfile(
        String id,
        TowerSilhouette silhouette,
        ParameterRange heightRange,
        ParameterRange baseWidthRange,
        ParameterRange armSpanRange,
        ParameterRange depthScaleRange,
        double defaultDepthRatio,
        double referenceHeight,
        double referenceBaseWidth,
        double referenceArmSpan,
        double topWireLift,
        List<TowerStationTemplate> stationTemplates,
        List<TowerArmTemplate> armTemplates,
        Map<StructureDensity, List<BayDensityConfig>> bayConfigsByDensity,
        MaterialMix legMaterial,
        MaterialMix braceMaterial,
        MaterialMix armMaterial) {
}
