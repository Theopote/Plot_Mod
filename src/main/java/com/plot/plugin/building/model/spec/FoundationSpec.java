package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 场地与地基：首层标高与回填材质。
 */
public final class FoundationSpec {
    private final String fillMaterial;
    private final Integer manualBaseElevation;

    public FoundationSpec(String fillMaterial, Integer manualBaseElevation) {
        this.fillMaterial = normalizeMaterial(fillMaterial);
        this.manualBaseElevation = manualBaseElevation;
    }

    public static FoundationSpec from(BuildingFootprint footprint) {
        return new FoundationSpec(
            footprint.getFoundationFillMaterial(),
            footprint.getManualBaseElevation()
        );
    }

    public String fillMaterial() {
        return fillMaterial;
    }

    public Integer manualBaseElevation() {
        return manualBaseElevation;
    }

    private static String normalizeMaterial(String material) {
        return material != null && !material.isBlank()
            ? material.trim()
            : BuildingFootprint.DEFAULT_FOUNDATION_FILL;
    }
}
