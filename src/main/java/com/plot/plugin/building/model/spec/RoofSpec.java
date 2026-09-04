package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 屋顶类型、坡度与材质。
 */
public final class RoofSpec {
    private final BuildingFootprint.RoofType type;
    private final int pitchRatio;
    private final String material;

    public RoofSpec(BuildingFootprint.RoofType type, int pitchRatio, String material) {
        this.type = type != null ? type : BuildingFootprint.RoofType.FLAT;
        this.pitchRatio = clamp(pitchRatio, 1, 16);
        this.material = normalizeMaterial(material);
    }

    public static RoofSpec from(BuildingFootprint footprint) {
        return new RoofSpec(
            footprint.getRoofType(),
            footprint.getRoofPitchRatio(),
            footprint.getRoofMaterial()
        );
    }

    public BuildingFootprint.RoofType type() {
        return type;
    }

    public int pitchRatio() {
        return pitchRatio;
    }

    public String material() {
        return material;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeMaterial(String material) {
        return material != null && !material.isBlank()
            ? material.trim()
            : BuildingFootprint.DEFAULT_ROOF_MATERIAL;
    }
}
