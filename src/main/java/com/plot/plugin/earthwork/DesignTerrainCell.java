package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;

/**
 * Design Terrain 合成后的单格结果（运行时，不持久化）。
 */
public final class DesignTerrainCell {
    private final int worldX;
    private final int worldZ;
    private final Vec2d center;
    private final int existingGroundY;
    private int targetY;
    private String zoneId;
    private boolean excluded;
    private boolean noTouch;

    public DesignTerrainCell(
            int worldX,
            int worldZ,
            Vec2d center,
            int existingGroundY) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.center = center;
        this.existingGroundY = existingGroundY;
        this.targetY = existingGroundY;
    }

    public int worldX() {
        return worldX;
    }

    public int worldZ() {
        return worldZ;
    }

    public Vec2d center() {
        return center;
    }

    public int existingGroundY() {
        return existingGroundY;
    }

    public int targetY() {
        return targetY;
    }

    public void setTargetY(int targetY) {
        this.targetY = targetY;
    }

    public String zoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public boolean excluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public boolean noTouch() {
        return noTouch;
    }

    public void setNoTouch(boolean noTouch) {
        this.noTouch = noTouch;
    }

    public int deltaY() {
        return targetY - existingGroundY;
    }

    public boolean participatesInEarthwork() {
        return !excluded && !noTouch;
    }
}
