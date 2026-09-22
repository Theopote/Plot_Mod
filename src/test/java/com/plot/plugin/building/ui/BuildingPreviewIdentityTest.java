package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingPreviewIdentityTest {

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

    @Test
    void matchesSameTargetsAndContent() {
        BuildingFootprint a = square("a", 10);
        BuildingFootprint b = square("b", 20);
        List<BuildingFootprint> targets = List.of(a, b);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(targets);

        assertEquals(
            BuildingPreviewIdentity.Validity.VALID,
            identity.validityAgainst(targets, true));
    }

    @Test
    void staleWhenTargetIdsChange() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a));

        BuildingFootprint b = square("b", 20);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(b), true));
    }

    @Test
    void staleWhenTargetOrderChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingFootprint b = square("b", 20);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a, b));

        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(b, a), true));
    }

    @Test
    void staleWhenBuildingParametersChange() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a));

        a.setFloors(12);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true));
    }

    @Test
    void staleWhenMaterialChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a));

        a.setWallMaterial("minecraft:bricks");
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true));
    }

    @Test
    void staleWhenRoofPitchChanges() {
        BuildingFootprint a = square("a", 10);
        BuildingPreviewIdentity identity = BuildingPreviewIdentity.capture(List.of(a));

        a.setRoofPitchRatio(3);
        assertEquals(
            BuildingPreviewIdentity.Validity.STALE,
            identity.validityAgainst(List.of(a), true));
    }
}
