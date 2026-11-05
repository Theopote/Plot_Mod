package com.plot.plugin.road.pipeline.corridor;

/**
 * 水域填筑授权：路基主体与边坡填筑分属不同施工所有权，不可共用一个全局列集合。
 */
public record WaterConstructionPermission(
        boolean allowRoadbedFill,
        boolean allowSlopeFill) {

    public static final WaterConstructionPermission NONE =
        new WaterConstructionPermission(false, false);

    public WaterConstructionPermission merge(WaterConstructionPermission other) {
        if (other == null) {
            return this;
        }
        return new WaterConstructionPermission(
            allowRoadbedFill || other.allowRoadbedFill,
            allowSlopeFill || other.allowSlopeFill);
    }

    public WaterConstructionPermission withRoadbedFill(boolean allow) {
        return new WaterConstructionPermission(allowRoadbedFill || allow, allowSlopeFill);
    }

    public WaterConstructionPermission withSlopeFill(boolean allow) {
        return new WaterConstructionPermission(allowRoadbedFill, allowSlopeFill || allow);
    }
}
