package com.plot.api.building;

/**
 * 建筑与土方垫层关联状态（Building ↔ Earthwork 契约 DTO）。
 */
public record BuildingPadElevationStatus(
        BuildingPadElevationMode mode,
        String zoneName,
        String siteName,
        Integer resolvedElevation) {

    public BuildingPadElevationStatus {
        mode = mode != null ? mode : BuildingPadElevationMode.NONE;
        zoneName = zoneName != null ? zoneName : "";
        siteName = siteName != null ? siteName : "";
    }

    public static BuildingPadElevationStatus none() {
        return new BuildingPadElevationStatus(BuildingPadElevationMode.NONE, "", "", null);
    }

    public boolean isLinked() {
        return mode != BuildingPadElevationMode.NONE;
    }
}
