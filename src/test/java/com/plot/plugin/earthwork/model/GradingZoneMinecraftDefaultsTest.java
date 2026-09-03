package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingZoneMinecraftDefaultsTest {

    @Test
    void buildingPadAndRoadLockHeight() {
        GradingZone pad = zone("pad");
        pad.setType(GradingZoneType.BUILDING_PAD);
        assertFalse(pad.isAutoAdjustElevation());
        assertTrue(pad.getType().locksDesignElevation());

        GradingZone road = zone("road");
        road.setType(GradingZoneType.ROAD_CORRIDOR);
        assertFalse(road.isAutoAdjustElevation());
        assertEquals(2, road.getDesignSurface().getWorkingMarginBlocks());
    }

    @Test
    void excavationPitDefaultsToDigDownWithoutWorkingFace() {
        GradingZone pit = zone("pit");
        pit.getDesignSurface().setFoundationDepth(2);
        pit.getDesignSurface().setPitWorkingAllowance(1);
        pit.setType(GradingZoneType.EXCAVATION_PIT);

        assertEquals(0, pit.getDesignSurface().getFoundationDepth());
        assertEquals(0, pit.getDesignSurface().getPitWorkingAllowance());
        assertEquals(6, pit.getDesignSurface().getDigDownBlocks());
        assertEquals(0, pit.getDesignSurface().getWorkingMarginBlocks());
        assertFalse(pit.isAutoAdjustElevation());
    }

    private static GradingZone zone(String name) {
        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0), new Vec2d(8, 0), new Vec2d(8, 8), new Vec2d(0, 8)));
        zone.setName(name);
        return zone;
    }
}
