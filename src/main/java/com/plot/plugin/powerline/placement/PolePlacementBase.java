package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;

import java.util.OptionalInt;

/**
 * 单根电杆的竖向基准：工程地面（湖底/原地面）与杆体建造基准（水面或地表）。
 */
public record PolePlacementBase(int engineeringGroundY, int buildBaseY) {

    /** 杆体（含塔基）第一层方块 Y。 */
    public int poleLayerStartY() {
        return buildBaseY + 1;
    }

    /** 水面下需用杆材补全到建造基准。 */
    public boolean requiresUnderwaterFill() {
        return buildBaseY > engineeringGroundY;
    }

    public static PolePlacementBase resolve(Vec2d planPoint, TerrainSampler terrain) {
        int engineeringGroundY = terrain.sampleSurfaceY(planPoint);
        OptionalInt waterSurface = terrain.findExposedWaterSurface(planPoint);
        if (waterSurface.isPresent()) {
            int waterY = waterSurface.getAsInt();
            if (waterY > engineeringGroundY) {
                return new PolePlacementBase(engineeringGroundY, waterY);
            }
        }
        return new PolePlacementBase(engineeringGroundY, engineeringGroundY);
    }
}
