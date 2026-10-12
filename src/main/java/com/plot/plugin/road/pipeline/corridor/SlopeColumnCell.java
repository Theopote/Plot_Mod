package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;

/** 列级边坡挖填 envelope（跨截面合并后的最终坡面）。 */
public final class SlopeColumnCell {
    private final Vec2d planPoint;
    private Integer cutSurfaceY;
    private Integer fillSurfaceY;
    private String cutMaterialId;
    private String fillMaterialId;

    public SlopeColumnCell(Vec2d planPoint) {
        this.planPoint = planPoint;
    }

    public Vec2d planPoint() {
        return planPoint;
    }

    public Integer cutSurfaceY() {
        return cutSurfaceY;
    }

    public Integer fillSurfaceY() {
        return fillSurfaceY;
    }

    public String cutMaterialId() {
        return cutMaterialId;
    }

    public String fillMaterialId() {
        return fillMaterialId;
    }

    /** 挖方坡面：取各截面贡献的最低标高（最深挖方）。 */
    public void mergeCut(int slopeY, String materialId) {
        if (cutSurfaceY == null || slopeY < cutSurfaceY) {
            cutSurfaceY = slopeY;
            cutMaterialId = materialId;
        }
    }

    /** 填方坡面：取各截面贡献的最高标高。 */
    public void mergeFill(int slopeY, String materialId) {
        if (fillSurfaceY == null || slopeY > fillSurfaceY) {
            fillSurfaceY = slopeY;
            fillMaterialId = materialId;
        }
    }

    public boolean hasCut() {
        return cutSurfaceY != null;
    }

    public boolean hasFill() {
        return fillSurfaceY != null;
    }
}
