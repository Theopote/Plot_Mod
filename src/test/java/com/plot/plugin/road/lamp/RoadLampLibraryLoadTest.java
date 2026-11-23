package com.plot.plugin.road.lamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLampLibraryLoadTest {
    @TempDir
    Path dir;

    @Test
    void blankOrNullJsonLoadsAsEmptyLibrary() throws Exception {
        Path blank = dir.resolve("blank.json");
        Files.writeString(blank, "   \n");
        assertTrue(new RoadLampLibrary(blank).load().isEmpty());

        Path nullJson = dir.resolve("null.json");
        Files.writeString(nullJson, "null");
        assertTrue(new RoadLampLibrary(nullJson).load().isEmpty());
    }
}
