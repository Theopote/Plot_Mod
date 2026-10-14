package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;

/** 列级三维清障 envelope。 */
public record ClearanceCell(
        Vec2d planPoint,
        int designSurfaceY,
        int clearTopY,
        RoadCorridorConstructionMode mode,
        RoadConstructionType constructionType,
        Integer corridorLateral) {

    public ClearanceCell(
            Vec2d planPoint,
            int designSurfaceY,
            int clearTopY,
            RoadCorridorConstructionMode mode,
            RoadConstructionType constructionType) {
        this(planPoint, designSurfaceY, clearTopY, mode, constructionType, null);
    }
}
