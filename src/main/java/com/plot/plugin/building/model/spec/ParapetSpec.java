package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 女儿墙：沿顶层外墙向上延伸的矮墙。
 */
public record ParapetSpec(boolean enabled, int height, String material) {
    public static final int DEFAULT_HEIGHT = 1;

    public ParapetSpec {
        height = Math.max(1, Math.min(height, 8));
    }

    public static ParapetSpec disabled() {
        return new ParapetSpec(false, DEFAULT_HEIGHT, null);
    }

    public static ParapetSpec from(BuildingFootprint footprint) {
        if (footprint == null || !footprint.isParapetEnabled()) {
            return disabled();
        }
        String material = footprint.getParapetMaterial();
        if (material == null || material.isBlank()) {
            material = footprint.getWallMaterial().getPrimaryMaterial();
        }
        return new ParapetSpec(true, footprint.getParapetHeight(), material);
    }

    public String resolvedMaterial() {
        if (material != null && !material.isBlank()) {
            return material.trim();
        }
        return BuildingFootprint.DEFAULT_WALL_MATERIAL;
    }
}
