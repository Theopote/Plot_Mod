package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class PowerLineOverviewLayoutCacheTest {
    private final IdentityCoordinateService coordinates = IdentityCoordinateService.INSTANCE;

    @AfterEach
    void tearDown() {
        PowerLineOverviewLayoutCache.clear();
    }

    @Test
    void reusesCachedLayoutUntilGeometryChanges() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(100, 0)));
        line.setMaxPoleSpacing(40);

        List<?> first = PowerLineOverviewLayoutCache.poleSites(line, coordinates);
        List<?> second = PowerLineOverviewLayoutCache.poleSites(line, coordinates);
        assertEquals(first, second);

        line.setMaxPoleSpacing(20);
        List<?> afterEdit = PowerLineOverviewLayoutCache.poleSites(line, coordinates);
        assertNotSame(first, afterEdit);
    }

    @Test
    void retainOnlyDropsRemovedLines() {
        PowerLineFootprint kept = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(50, 0)));
        PowerLineFootprint removed = new PowerLineFootprint(List.of(new Vec2d(0, 10), new Vec2d(50, 10)));
        PowerLineOverviewLayoutCache.poleSites(kept, coordinates);
        PowerLineOverviewLayoutCache.poleSites(removed, coordinates);

        PowerLineOverviewLayoutCache.retainOnly(Set.of(kept.getId()));

        removed.setPoleSpacingMode(PoleSpacingMode.TOWER_COUNT);
        removed.setTargetTowerCount(4);
        assertEquals(4, PowerLineOverviewLayoutCache.poleCount(removed, coordinates));
        assertEquals(
            PowerLineOverviewLayoutCache.poleSites(kept, coordinates),
            PowerLineOverviewLayoutCache.poleSites(kept, coordinates));
    }
}
