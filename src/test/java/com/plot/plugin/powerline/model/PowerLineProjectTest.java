package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineProjectTest {

    @Test
    void jsonRoundTripPreservesAllFields() {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(40, 20)));
        line.setName("Main Feeder");
        line.setMaxPoleSpacing(18.0);
        line.setCornerAngleThreshold(10.0);
        line.setPoleHeight(12.0);
        line.setSagRatio(0.2);
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setPoleMaterial(MaterialMix.single("minecraft:spruce_fence"));
        line.setPoleDesignId("design-1");
        project.addLine(line);

        PowerLineProject restored = PowerLineProject.fromJson(project.toJson());
        PowerLineFootprint restoredLine = restored.getLine(line.getId());
        assertNotNull(restoredLine);
        assertEquals("Main Feeder", restoredLine.getName());
        assertEquals(3, restoredLine.getPathPoints().size());
        assertEquals(18.0, restoredLine.getMaxPoleSpacing(), 1e-6);
        assertEquals(10.0, restoredLine.getCornerAngleThreshold(), 1e-6);
        assertEquals(12.0, restoredLine.getPoleHeight(), 1e-6);
        assertEquals(0.2, restoredLine.getSagRatio(), 1e-6);
        assertEquals("minecraft:chain", restoredLine.getWireMaterial().getPrimaryMaterial());
        assertEquals("minecraft:spruce_fence", restoredLine.getPoleMaterial().getPrimaryMaterial());
        assertEquals("design-1", restoredLine.getPoleDesignId());
        assertEquals(line.getRoadId(), restoredLine.getRoadId());
        String json = project.toJson();
        assertFalse(json.contains("\"closeSpacingWarningThreshold\""));
        assertFalse(json.contains("\"minPoleSpacing\""));
        assertFalse(json.contains("\"spacingCustomized\""));
    }

    @Test
    void legacyCloseSpacingFieldsAreIgnoredOnLoad() {
        String json = """
            {
              "lines": [{
                "id": "legacy-line",
                "pathPoints": [{"x": 0.0, "y": 0.0}, {"x": 40.0, "y": 0.0}],
                "minPoleSpacing": 9.0,
                "closeSpacingWarningThreshold": 11.0,
                "maxPoleSpacing": 18.0
              }]
            }
            """;

        PowerLineProject restored = PowerLineProject.fromJson(json);
        PowerLineFootprint restoredLine = restored.getLine("legacy-line");
        assertNotNull(restoredLine);
        assertEquals(18.0, restoredLine.getMaxPoleSpacing(), 1e-6);
    }

    @Test
    void legacySpacingCustomizedJsonLoadsPreferredSpacingFromMaxPoleSpacing() {
        String json = """
            {
              "lines": [{
                "id": "legacy-spacing",
                "pathPoints": [{"x": 0.0, "y": 0.0}, {"x": 40.0, "y": 0.0}],
                "spacingCustomized": true,
                "maxPoleSpacing": 22.0
              }]
            }
            """;

        PowerLineProject restored = PowerLineProject.fromJson(json);
        PowerLineFootprint restoredLine = restored.getLine("legacy-spacing");
        assertNotNull(restoredLine);
        assertEquals(22.0, restoredLine.getStyleOverrides().getPreferredSpacing(), 1e-6);
    }

    @Test
    void saveToIsAtomicAndRoundTrips(@TempDir Path dir) throws IOException {
        PowerLineProject project = new PowerLineProject();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        line.setName("Atomic");
        project.addLine(line);

        Path file = dir.resolve("powerline.json");
        project.saveTo(file);
        assertTrue(Files.exists(file));

        PowerLineProject loaded = PowerLineProject.loadFrom(file);
        assertEquals(1, loaded.getLineCount());
        assertEquals("Atomic", loaded.getLine(line.getId()).getName());
    }

    @Test
    void corruptJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> PowerLineProject.fromJson("{broken"));
    }
}
