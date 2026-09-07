package com.plot.plugin.powerline.model;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineDesignProjectTest {

    @Test
    void saveAndLoadRoundTrip(@TempDir Path dir) throws IOException {
        PowerLineDesignProject project = new PowerLineDesignProject();
        PoleDesign design = PoleDesign.fromJson(PoleDesignCatalog.latticeSteelTower().toJson());
        design.setName("Saved Lattice");
        project.addDesign(design);

        Path file = dir.resolve("designs.json");
        project.saveTo(file);
        assertTrue(Files.exists(file));

        PowerLineDesignProject loaded = PowerLineDesignProject.loadFrom(file);
        assertEquals(1, loaded.getDesignCount());
        PoleDesign restored = loaded.getDesign(design.getId());
        assertNotNull(restored);
        assertEquals("Saved Lattice", restored.getName());
        assertEquals(design.totalHeight(), restored.totalHeight());
    }
}
