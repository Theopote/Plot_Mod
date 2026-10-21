package com.plot.plugin.earthwork.pipeline;

/**
 * 单个最终放置记录的施工归属。写入时保存，避免事后仅靠 X/Z 反查设计网格。
 */
public record PlacementMeta(String zoneId) {
    public static final PlacementMeta UNASSIGNED = new PlacementMeta("");

    public PlacementMeta {
        zoneId = zoneId != null ? zoneId : "";
    }

    public static PlacementMeta ofZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return UNASSIGNED;
        }
        return new PlacementMeta(zoneId);
    }

    public boolean isAssigned() {
        return !zoneId.isBlank();
    }
}
