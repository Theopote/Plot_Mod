package com.plot.plugin.road.pipeline.profile.environment;

/**
 * Environment column at one profile station: solid terrain, optional exposed water, semantics.
 */
public record EnvironmentSample(
        double station,
        int terrainY,
        Integer waterSurfaceY,
        int waterDepth,
        SurfaceContext context) {

    public boolean hasWater() {
        return waterSurfaceY != null;
    }

    /** 裸露水面：水面严格高于固体地表，自动纵断面须抬到水面以上。 */
    public boolean hasExposedWater() {
        return waterSurfaceY != null && waterSurfaceY > terrainY;
    }

    public static EnvironmentSample land(double station, int terrainY) {
        return new EnvironmentSample(station, terrainY, null, 0, SurfaceContext.LAND);
    }
}
