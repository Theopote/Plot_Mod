package com.plot.core.model;

import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.core.state.AppState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTest {

    @BeforeEach
    void resetAppState() {
        AppState.getInstance().initializeLayerSystem();
    }

    @Test
    void deserializeEmptyInputThrows() {
        ProjectFormatException ex = assertThrows(ProjectFormatException.class, () -> Project.deserialize(""));
        assertEquals(ProjectFormatException.Reason.EMPTY_INPUT, ex.getReason());
    }

    @Test
    void deserializeInvalidJsonThrows() {
        ProjectFormatException ex = assertThrows(ProjectFormatException.class,
                () -> Project.deserialize("{not-valid-json"));
        assertEquals(ProjectFormatException.Reason.INVALID_JSON, ex.getReason());
    }

    @Test
    void deserializeUnsupportedFormatVersionThrows() {
        String json = """
                {
                  "formatVersion": 99,
                  "name": "Bad Version",
                  "layers": []
                }
                """;
        ProjectFormatException ex = assertThrows(ProjectFormatException.class, () -> Project.deserialize(json));
        assertEquals(ProjectFormatException.Reason.UNSUPPORTED_FORMAT_VERSION, ex.getReason());
    }

    @Test
    void deserializeV1MigratesToCurrent() throws ProjectFormatException {
        String json = """
                {
                  "formatVersion": 1,
                  "name": "Legacy V1",
                  "id": "legacy-v1",
                  "layers": [
                    {
                      "id": "layer-1",
                      "name": "Layer 1",
                      "shapes": []
                    }
                  ]
                }
                """;
        Project project = Project.deserialize(json);
        assertEquals("Legacy V1", project.getName());
        assertEquals(1, project.getLayers().size());

        String saved = project.serialize();
        assertTrue(saved.contains("\"formatVersion\": " + ProjectSnapshot.CURRENT_FORMAT_VERSION)
                || saved.contains("\"formatVersion\":" + ProjectSnapshot.CURRENT_FORMAT_VERSION));
    }

    @Test
    void deserializeMissingFormatVersionMigratesFromV0() throws ProjectFormatException {
        String json = """
                {
                  "name": "No Version Field",
                  "id": "no-ver",
                  "layers": []
                }
                """;
        Project project = Project.deserialize(json);
        assertEquals("No Version Field", project.getName());
    }

    @Test
    void deserializeV1NullLayersMigratesToEmptyLayers() throws ProjectFormatException {
        String json = """
                {
                  "formatVersion": 1,
                  "name": "Null Layers",
                  "id": "null-layers",
                  "layers": null
                }
                """;
        Project project = Project.deserialize(json);
        assertEquals("Null Layers", project.getName());
        assertFalse(project.getLayers().isEmpty());
    }

    @Test
    void deserializeBareObjectMigratesWithDefaultName() throws ProjectFormatException {
        Project project = Project.deserialize("{}");
        assertEquals("Untitled", project.getName());
        assertFalse(project.getLayers().isEmpty());
    }

    @Test
    void serializeRoundTripPreservesProjectName() throws ProjectFormatException {
        Project project = new Project("Audit Test");
        project.syncLayersFrom(AppState.getInstance().getLayerManager());

        Project restored = Project.deserialize(project.serialize());
        assertEquals("Audit Test", restored.getName());
        assertFalse(restored.getLayers().isEmpty());
    }

    @Test
    void loadFromCorruptFileThrowsAndDoesNotApplyState(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.plot");
        Files.writeString(file, "{broken", StandardCharsets.UTF_8);

        AppState appState = AppState.getInstance();
        appState.initializeLayerSystem();
        LayerManager layerManager = appState.getLayerManager();
        Layer marker = new Layer("marker-layer");
        layerManager.addLayer(marker);
        appState.setActiveLayer(marker);
        String markerId = marker.getId();

        assertThrows(ProjectFormatException.class, () -> Project.loadFromFile(appState, file));

        assertEquals(markerId, appState.getActiveLayer().getId());
        assertTrue(layerManager.getLayers().stream().anyMatch(l -> markerId.equals(l.getId())));
    }

    @Test
    void saveToFileIsAtomicAndCreatesBackupAndAutosave(@TempDir Path dir) throws IOException {
        AppState appState = AppState.getInstance();
        appState.initializeLayerSystem();
        Project first = new Project("First Save");
        first.applyToAppState(appState);

        Path file = dir.resolve("test.plot");
        Project.saveToFile(appState, file);

        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("test.plot" + Project.TEMP_SUFFIX)));
        assertTrue(Files.exists(dir.resolve("test.plot" + Project.AUTOSAVE_SUFFIX)));

        Project second = new Project("Second Save");
        second.applyToAppState(appState);
        Project.saveToFile(appState, file);

        assertTrue(Files.exists(dir.resolve("test.plot" + Project.BACKUP_SUFFIX)));
        String backup = Files.readString(dir.resolve("test.plot" + Project.BACKUP_SUFFIX), StandardCharsets.UTF_8);
        assertTrue(backup.contains("First Save"));

        Project loaded = Project.deserialize(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("Second Save", loaded.getName());
    }

    @Test
    void writeAtomicallyDoesNotOverwriteOnValidationFailure(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("safe.plot");
        String goodJson = new Project("Good").serialize();
        Project.writeAtomically(file, goodJson);

        String original = Files.readString(file, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> Project.writeAtomically(file, ""));

        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void minimalValidSnapshotDeserializes() throws ProjectFormatException {
        String json = """
                {
                  "formatVersion": %d,
                  "name": "Minimal",
                  "id": "test-id",
                  "layers": []
                }
                """.formatted(ProjectSnapshot.CURRENT_FORMAT_VERSION);

        Project project = Project.deserialize(json);
        assertEquals("Minimal", project.getName());
        assertEquals(1, project.getLayers().size());
    }
}
