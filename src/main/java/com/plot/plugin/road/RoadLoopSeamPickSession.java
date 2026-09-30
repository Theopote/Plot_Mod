package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

/** 画布上为闭环道路重设剖面开口点的拾取会话。 */
public final class RoadLoopSeamPickSession {
    private boolean active;
    private String roadId = "";

    public boolean isActive() {
        return active;
    }

    public String roadId() {
        return roadId;
    }

    public void begin(String roadId) {
        this.roadId = roadId != null ? roadId : "";
        this.active = roadId != null && !roadId.isBlank();
    }

    public void cancel() {
        active = false;
        roadId = "";
    }

    public boolean matchesRoad(String candidateRoadId) {
        return active && roadId.equals(candidateRoadId);
    }

    public Vec2d lastClickPosition;
}
