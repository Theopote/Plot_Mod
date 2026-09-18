package com.plot.plugin.pattern.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternBuiltinPresetAssetsTest {

    @TempDir
    Path tempDir;

    @Test
    void installsZebraCrossingAsset() throws Exception {
        PatternBuiltinPresetAssets.installAll(tempDir);

        String relativePath = PatternBuiltinPresetAssets.relativePath("zebra_crossing");
        assertTrue(PatternPresetImageStore.isPresetAssetPath(relativePath));
        assertTrue(Files.exists(tempDir.resolve(relativePath)));

        int[] dimensions = PatternBuiltinPresetAssets.readDimensions(tempDir, "zebra_crossing");
        assertEquals(64, dimensions[0]);
        assertEquals(16, dimensions[1]);
    }
}
