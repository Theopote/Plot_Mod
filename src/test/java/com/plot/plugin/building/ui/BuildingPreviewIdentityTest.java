package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPreviewIdentityTest {
    private static final int PROJECTION_A = projectionFingerprint(100.0, 900.0, 0.0, 600.0);
    private static final int PROJECTION_B = projectionFingerprint(200.0, 1000.0, 50.0, 650.0);

    private static BuildingFootprint square(String id, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size)
        ), true);
        footprint.setName(id);
        footprint.setFloors(4);
        return footprint;
    }

    private static int projectionFingerprint(
            double minX, double maxX, double minZ, double maxZ) {
        return new WorldProjectionSnapshot(
            new WorldViewBounds(minX, maxX, minZ, maxZ),
            256f,
            1f,
            800f,
            600f).fingerprint();
    }

    @Test
    void matchesSameTargetsAndContent() {
        BuildingFootprint a = square("a", 10);
        BuildingFootprint b = square("b", 20);
        List<BuildingFootprint> targets = List.of(a, b);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(targets, false, PROJECTION_A);

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            identity.validityAgainst(targets, true, false, PROJECTION_A));
    }

    @Test
    void staleWhenTargetIdsChange() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a), false, PROJECTION_A);

        BuildingFootprint b = square("b", 20);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(b), true, false, PROJECTION_A));
    }

    @Test
    void validWhenTargetOrderChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingFootprint b = square("b", 20);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a, b), false, PROJECTION_A);

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            identity.validityAgainst(List.of(b, a), true, false, PROJECTION_A));
    }

    @Test
    void validWhenPreviewUsedDistrictGenerationOrder() {
        BuildingFootprint low = square("building-low", 10);
        low.setFloors(2);
        BuildingFootprint high = square("building-high", 12);
        high.setFloors(8);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(high, low), false, PROJECTION_A);

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            identity.validityAgainst(List.of(low, high), true, false, PROJECTION_A));
    }

    @Test
    void staleWhenBuildingParametersChange() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a), false, PROJECTION_A);

        a.setFloors(12);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true, false, PROJECTION_A));
    }

    @Test
    void staleWhenMaterialChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a), false, PROJECTION_A);

        a.setWallMaterial("minecraft:bricks");
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true, false, PROJECTION_A));
    }

    @Test
    void staleWhenRoofPitchChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a), false, PROJECTION_A);

        a.setRoofPitchRatio(3);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true, false, PROJECTION_A));
    }

    @Test
    void staleWhenFrameOnlyModeChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a), false, PROJECTION_A);

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            identity.validityAgainst(List.of(a), true, false, PROJECTION_A));
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true, true, PROJECTION_A));
    }

    @Test
    void staleWhenProjectionChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a), false, PROJECTION_A);

        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true, false, PROJECTION_B));
        assertTrue(identity.matchesTargetsAndContent(List.of(a), false));
        assertFalse(identity.matchesProjection(PROJECTION_B));
    }
}
