package com.plot.plugin.building.model.spec;

import com.plot.plugin.building.model.BuildingFootprint;

/**
 * 阳台：从指定墙段外挑的楼板与栏杆。
 */
public record BalconySpec(
        int wallSegmentIndex,
        double positionRatio,
        int floor,
        int width,
        int depth,
        String slabMaterial,
        String railingMaterial) {

    public BalconySpec {
        width = Math.max(1, Math.min(width, 16));
        depth = Math.max(1, Math.min(depth, 8));
        floor = Math.max(0, floor);
    }

    public static BalconySpec from(BuildingFootprint.Balcony balcony) {
        return new BalconySpec(
            balcony.wallSegmentIndex,
            balcony.positionRatio,
            balcony.floor,
            balcony.width,
            balcony.depth,
            balcony.slabMaterial,
            balcony.railingMaterial);
    }

    public String resolvedSlabMaterial() {
        if (slabMaterial != null && !slabMaterial.isBlank()) {
            return slabMaterial.trim();
        }
        return BuildingFootprint.DEFAULT_FLOOR_MATERIAL;
    }

    public String resolvedRailingMaterial() {
        if (railingMaterial != null && !railingMaterial.isBlank()) {
            return railingMaterial.trim();
        }
        return "minecraft:oak_fence";
    }
}
