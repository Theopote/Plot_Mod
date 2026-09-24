package com.plot.plugin.building.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.spec.WallFacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy {@link BuildingFootprint#setWindowSpacing(int)} 与旧 WallFacade JSON 迁移路径。
 * 新测试应使用 {@code setWindowsEnabled} / {@code setWindowPierWidth}。
 */
class WindowSpacingLegacyMigrationTest {

    @Test
    void setWindowSpacingZeroDisablesWindows() {
        BuildingFootprint footprint = sampleFootprint();
        footprint.setWindowSpacing(4);
        assertTrue(footprint.isWindowsEnabled());

        footprint.setWindowSpacing(0);
        assertFalse(footprint.isWindowsEnabled());
        assertEquals(0, footprint.getWindowSpacing());
    }

    @Test
    void setWindowSpacingMigratesPierFromSpacingMinusWidth() {
        BuildingFootprint footprint = sampleFootprint();
        footprint.setWindowWidth(2);
        footprint.setWindowSpacing(5);

        assertTrue(footprint.isWindowsEnabled());
        assertEquals(2, footprint.getWindowWidth());
        assertEquals(3, footprint.getWindowPierWidth());
        assertEquals(5, footprint.getWindowSpacing());
    }

    @Test
    void wallFacadeJsonWithLegacySpacingStillLoads() {
        String json = """
            {
              "buildings": [{
                "id": "legacy-facade",
                "name": "Legacy",
                "outerPoints": [
                  {"x": 0, "y": 0},
                  {"x": 8, "y": 0},
                  {"x": 8, "y": 6},
                  {"x": 0, "y": 6}
                ],
                "isRectangular": true,
                "floors": 2,
                "floorHeight": 3,
                "wallThickness": 1,
                "windowSpacing": 0,
                "windowWidth": 1,
                "windowHeight": 2,
                "windowSillHeight": 1,
                "wallFacades": [{
                  "wallSegmentIndex": 0,
                  "windowSpacing": 4,
                  "windowWidth": 1,
                  "windowHeight": 2,
                  "windowSillHeight": 1
                }]
              }]
            }
            """;

        BuildingProject project = BuildingProject.fromJson(json);
        BuildingFootprint restored = project.getBuilding("legacy-facade");

        assertEquals(1, restored.getWallFacades().size());
        WindowPatternSpec pattern = restored.getWallFacades().getFirst().windowPattern();
        assertTrue(pattern.enabled());
        assertEquals(4, pattern.spacing());
        assertEquals(3, pattern.pierWidth());
    }

    @Test
    void wallFacadeJsonWithNewFieldsRoundTripsWithoutSpacing() {
        BuildingFootprint footprint = sampleFootprint();
        footprint.setWallFacades(List.of(
            WallFacadeSpec.of(0, WindowPatternSpec.of(2, 2, 2, 1)),
            WallFacadeSpec.noWindows(1)
        ));

        BuildingProject project = new BuildingProject();
        project.addBuilding(footprint);
        BuildingFootprint restored = BuildingProject.fromJson(project.toJson()).getBuilding(footprint.getId());

        assertEquals(2, restored.getWallFacades().size());
        WindowPatternSpec south = restored.getWallFacades().getFirst().windowPattern();
        assertTrue(south.enabled());
        assertEquals(2, south.width());
        assertEquals(2, south.pierWidth());
        assertFalse(restored.getWallFacades().get(1).windowPattern().enabled());
    }

    private static BuildingFootprint sampleFootprint() {
        return new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
    }
}
