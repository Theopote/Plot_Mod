package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 入口雨篷：从指定墙段外挑的水平遮阳/雨棚。
 */
public record CanopySpec(
        int wallSegmentIndex,
        double positionRatio,
        int floor,
        int width,
        int depth,
        int clearance,
        String material) {

    public CanopySpec {
        width = Math.max(1, Math.min(width, 16));
        depth = Math.max(1, Math.min(depth, 8));
        clearance = Math.max(2, Math.min(clearance, 16));
        floor = Math.max(0, floor);
    }

    public static CanopySpec from(BuildingFootprint.Canopy canopy) {
        return new CanopySpec(
            canopy.wallSegmentIndex,
            canopy.positionRatio,
            canopy.floor,
            canopy.width,
            canopy.depth,
            canopy.clearance,
            canopy.material);
    }

    public String resolvedMaterial() {
        if (material != null && !material.isBlank()) {
            return material.trim();
        }
        return BuildingFootprint.DEFAULT_ROOF_MATERIAL;
    }
}
