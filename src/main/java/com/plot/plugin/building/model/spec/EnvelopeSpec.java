package com.plot.plugin.building.model.spec;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 围护结构：墙厚与墙/楼板材质。
 */
public final class EnvelopeSpec {
    private final int wallThickness;
    private final MaterialMix wallMaterial;
    private final MaterialMix floorMaterial;

    public EnvelopeSpec(int wallThickness, MaterialMix wallMaterial, MaterialMix floorMaterial) {
        this.wallThickness = clamp(wallThickness, 1, 8);
        this.wallMaterial = normalizeWallMaterial(wallMaterial);
        this.floorMaterial = normalizeFloorMaterial(floorMaterial);
    }

    public static EnvelopeSpec from(BuildingFootprint footprint) {
        return new EnvelopeSpec(
            footprint.getWallThickness(),
            footprint.getWallMaterial(),
            footprint.getFloorMaterial()
        );
    }

    public int wallThickness() {
        return wallThickness;
    }

    public MaterialMix wallMaterial() {
        return wallMaterial;
    }

    public MaterialMix floorMaterial() {
        return floorMaterial;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static MaterialMix normalizeWallMaterial(MaterialMix material) {
        if (material != null && material.getPrimaryMaterial() != null && !material.getPrimaryMaterial().isBlank()) {
            return material;
        }
        return MaterialMix.single(BuildingFootprint.DEFAULT_WALL_MATERIAL);
    }

    private static MaterialMix normalizeFloorMaterial(MaterialMix material) {
        if (material != null && material.getPrimaryMaterial() != null && !material.getPrimaryMaterial().isBlank()) {
            return material;
        }
        return MaterialMix.single(BuildingFootprint.DEFAULT_FLOOR_MATERIAL);
    }
}
