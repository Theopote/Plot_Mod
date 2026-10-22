package com.plot.plugin.road.model;

import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentPersistence;

/**
 * 将旧 {@link RoadVerticalMode#MANUAL_PROFILE} 语义迁移为
 * {@link ProfileSource#EDITED} + {@link RoadVerticalMode#FIT_TERRAIN}。
 */
final class RoadNetworkProfileSourceMigration {

    private RoadNetworkProfileSourceMigration() {
    }

    static RoadNetwork.NetworkData migrateV2ToV3(RoadNetwork.NetworkData data) {
        data.schemaVersion = 3;
        // profileSource 规范化在 {@link RoadNetwork#migrateNetworkData} 末尾、
        // verticalMode 规范化之后统一执行。
        return data;
    }

    static void normalizeLoadedProfileSources(RoadNetwork.NetworkData data) {
        if (data == null || data.roads == null) {
            return;
        }
        for (RoadNetwork.RoadData road : data.roads) {
            normalizeRoadProfileSource(road);
        }
    }

    private static void migrateProfileSources(RoadNetwork.NetworkData data) {
        if (data.roads == null) {
            return;
        }
        for (RoadNetwork.RoadData road : data.roads) {
            normalizeRoadProfileSource(road);
        }
    }

    static void normalizeRoadProfileSource(RoadNetwork.RoadData road) {
        if (road == null) {
            return;
        }
        if (road.profileSource != null && !road.profileSource.isBlank()) {
            normalizeLegacyVerticalMode(road);
            return;
        }
        if (RoadVerticalMode.FLAT.name().equalsIgnoreCase(road.verticalMode)) {
            road.profileSource = ProfileSource.AUTO.name();
            return;
        }
        if (RoadVerticalMode.MANUAL_PROFILE.name().equalsIgnoreCase(road.verticalMode)) {
            road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
            road.profileSource = ProfileSource.EDITED.name();
            if ((road.editedGeometrySignature == null || road.editedGeometrySignature == 0)
                    && hasManualVerticalProfile(road)) {
                road.editedGeometrySignature = 0;
            }
            return;
        }
        if (hasManualVerticalProfile(road)) {
            road.profileSource = ProfileSource.AUTO.name();
            if (road.verticalMode == null || road.verticalMode.isBlank()
                    || RoadVerticalMode.AUTO_SMOOTH.name().equalsIgnoreCase(road.verticalMode)) {
                road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
            }
            return;
        }
        road.profileSource = ProfileSource.AUTO.name();
        normalizeLegacyVerticalMode(road);
    }

    private static void normalizeLegacyVerticalMode(RoadNetwork.RoadData road) {
        if (RoadVerticalMode.MANUAL_PROFILE.name().equalsIgnoreCase(road.verticalMode)) {
            road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
        }
        if (RoadVerticalMode.AUTO_SMOOTH.name().equalsIgnoreCase(road.verticalMode)) {
            road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
        }
    }

    private static boolean hasManualVerticalProfile(RoadNetwork.RoadData road) {
        VerticalAlignmentPersistence.VerticalAlignmentData alignment = road.verticalAlignment;
        return alignment != null && alignment.pvis != null && !alignment.pvis.isEmpty();
    }
}
