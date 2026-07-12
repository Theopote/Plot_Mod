package com.plot.plugin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPathHasherTest {

    @Test
    void hashPathIsStable() {
        String path = "C:/projects/demo.plotproj";
        assertEquals(ProjectPathHasher.hashPath(path), ProjectPathHasher.hashPath(path));
    }

    @Test
    void blankPathUsesDefault() {
        assertEquals("default", ProjectPathHasher.hashPath(""));
        assertEquals("default.json", ProjectPathHasher.projectFileName(null));
    }

    @Test
    void projectFileNameAppendsJson() {
        String fileName = ProjectPathHasher.projectFileName("C:/projects/demo.plotproj");
        assertTrue(fileName.endsWith(".json"));
        assertEquals(21, fileName.length());
    }
}
