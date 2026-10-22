package com.plot.plugin.road.model;

import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentPersistence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNetworkProfileSourceMigrationTest {

    @Test
    void migratesManualProfileToEditedFitTerrain() {
        RoadNetwork.RoadData road = new RoadNetwork.RoadData();
        road.verticalMode = RoadVerticalMode.MANUAL_PROFILE.name();
        road.verticalAlignment = new VerticalAlignmentPersistence.VerticalAlignmentData();
        VerticalAlignmentPersistence.PviData pvi = new VerticalAlignmentPersistence.PviData();
        pvi.station = 0.0;
        pvi.elevation = 70.0;
        road.verticalAlignment.pvis = java.util.List.of(pvi);

        RoadNetworkProfileSourceMigration.normalizeRoadProfileSource(road);

        assertEquals(RoadVerticalMode.FIT_TERRAIN.name(), road.verticalMode);
        assertEquals(ProfileSource.EDITED.name(), road.profileSource);
    }

    @Test
    void dormantAlignmentOnFitTerrainBecomesAuto() {
        RoadNetwork.RoadData road = new RoadNetwork.RoadData();
        road.verticalMode = RoadVerticalMode.FIT_TERRAIN.name();
        road.verticalAlignment = new VerticalAlignmentPersistence.VerticalAlignmentData();
        VerticalAlignmentPersistence.PviData pvi = new VerticalAlignmentPersistence.PviData();
        pvi.station = 0.0;
        pvi.elevation = 70.0;
        road.verticalAlignment.pvis = java.util.List.of(pvi);

        RoadNetworkProfileSourceMigration.normalizeRoadProfileSource(road);

        assertEquals(ProfileSource.AUTO.name(), road.profileSource);
    }
}
