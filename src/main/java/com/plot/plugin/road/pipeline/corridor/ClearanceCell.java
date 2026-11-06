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
        Integer corridorLateral,
        /** 站点解析的走廊包络宽；隧道清障优先用此值，避免变量横断面退回全局配置。 */
        Integer envelopeWidthBlocks) {

    public ClearanceCell(
            Vec2d planPoint,
            int designSurfaceY,
            int clearTopY,
            RoadCorridorConstructionMode mode,
            RoadConstructionType constructionType) {
        this(planPoint, designSurfaceY, clearTopY, mode, constructionType, null, null);
    }

    public ClearanceCell(
            Vec2d planPoint,
            int designSurfaceY,
            int clearTopY,
            RoadCorridorConstructionMode mode,
            RoadConstructionType constructionType,
            Integer corridorLateral) {
        this(planPoint, designSurfaceY, clearTopY, mode, constructionType, corridorLateral, null);
    }
}
