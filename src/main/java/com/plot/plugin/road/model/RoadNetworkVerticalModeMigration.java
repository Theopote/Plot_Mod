package com.plot.plugin.road.model;

import com.plot.plugin.road.vertical.FlatVerticalIntentPersistence;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentPersistence;

/**
 * 旧存档垂直模式兼容：将「未显式写入 verticalMode、产品语义为适应地形」的道路迁移为
 * {@link RoadVerticalMode#FIT_TERRAIN}。
 * <p>
 * v1 序列化曾用 {@link Road#getVerticalMode()} 落盘，会把隐式默认写成 {@code AUTO_SMOOTH}；
 * 从 schema 1 升级到 2 时一并纠正。v2+ 若仍显式保存 {@code AUTO_SMOOTH} 则保留（高级/兼容用途）。
 */
final class RoadNetworkVerticalModeMigration {

    private RoadNetworkVerticalModeMigration() {
    }

    static RoadNetwork.NetworkData migrateV1ToV2(RoadNetwork.NetworkData data) {
        data.schemaVersion = 2;
        migrateLegacyAutoSmoothFromV1(data);
        return data;
    }

    static void normalizeLoadedVerticalModes(RoadNetwork.NetworkData data) {
        if (data == null || data.roads == null) {
            return;
        }
        for (RoadNetwork.RoadData road : data.roads) {
            normalizeOmittedVerticalMode(road);
        }
    }

    private static void migrateLegacyAutoSmoothFromV1(RoadNetwork.NetworkData data) {
        if (data.roads == null) {
            return;
        }
        for (RoadNetwork.RoadData road : data.roads) {
            if (shouldMigrateLegacyAutoSmooth(road)) {
                road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
            }
        }
    }

    private static void normalizeOmittedVerticalMode(RoadNetwork.RoadData road) {
        if (road == null || road.verticalMode != null && !road.verticalMode.isBlank()) {
            return;
        }
        if (hasFlatVerticalIntent(road)) {
            road.verticalMode = RoadVerticalMode.FLAT.name();
            return;
        }
        if (hasManualVerticalProfile(road)) {
            road.verticalMode = RoadVerticalMode.MANUAL_PROFILE.name();
            return;
        }
        road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
    }

    private static boolean shouldMigrateLegacyAutoSmooth(RoadNetwork.RoadData road) {
        if (road == null || hasFlatVerticalIntent(road) || hasManualVerticalProfile(road)) {
            return false;
        }
        if (RoadVerticalMode.FIT_TERRAIN.name().equalsIgnoreCase(road.verticalMode)) {
            return false;
        }
        if (RoadVerticalMode.FLAT.name().equalsIgnoreCase(road.verticalMode)
                || RoadVerticalMode.MANUAL_PROFILE.name().equalsIgnoreCase(road.verticalMode)) {
            return false;
        }
        return road.verticalMode == null
            || road.verticalMode.isBlank()
            || RoadVerticalMode.AUTO_SMOOTH.name().equalsIgnoreCase(road.verticalMode);
    }

    private static boolean hasFlatVerticalIntent(RoadNetwork.RoadData road) {
        if (road == null) {
            return false;
        }
        if (RoadVerticalMode.FLAT.name().equalsIgnoreCase(road.verticalMode)) {
            return true;
        }
        FlatVerticalIntentPersistence.FlatVerticalIntentData intent = road.flatVerticalIntent;
        return intent != null && Double.isFinite(intent.baseElevation);
    }

    private static boolean hasManualVerticalProfile(RoadNetwork.RoadData road) {
        if (road == null) {
            return false;
        }
        if (RoadVerticalMode.MANUAL_PROFILE.name().equalsIgnoreCase(road.verticalMode)) {
            return true;
        }
        VerticalAlignmentPersistence.VerticalAlignmentData alignment = road.verticalAlignment;
        return alignment != null && alignment.pvis != null && !alignment.pvis.isEmpty();
    }
}
