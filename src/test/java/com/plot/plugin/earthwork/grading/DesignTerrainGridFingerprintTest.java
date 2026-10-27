package com.plot.plugin.earthwork.grading;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DesignTerrainGridFingerprintTest {

    @Test
    void fingerprintChangesWhenTargetHeightChanges() {
        DesignTerrainGrid before = gridWithTarget(64);
        DesignTerrainGrid after = gridWithTarget(65);
        assertNotEquals(
            DesignTerrainGrid.earthworkContentFingerprint(before),
            DesignTerrainGrid.earthworkContentFingerprint(after));
    }

    private static DesignTerrainGrid gridWithTarget(int targetY) {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cell = new DesignTerrainCell(2, 2, new Vec2d(2.5, 2.5), 64);
        cell.setTargetY(targetY);
        cell.setZoneId("pad");
        grid.put(2, 2, cell);
        return grid;
    }
}
