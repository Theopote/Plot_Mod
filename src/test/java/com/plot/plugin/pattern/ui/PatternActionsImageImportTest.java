package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.pattern.image.PatternImageStore;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.plugin.pattern.model.PatternSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternActionsImageImportTest {

    @TempDir
    Path tempDir;

    @Test
    void failedImportDoesNotPushHistory() throws IOException {
        PatternPluginState state = newStateWithFootprint("fp-import");
        PatternActions actions = actions(state);
        actions.setPluginDataDir(tempDir);

        Path corrupt = tempDir.resolve("corrupt.png");
        Files.writeString(corrupt, "not-a-png");

        actions.importImageFromPath("fp-import", corrupt.toString());

        assertFalse(state.getProjectHistory().canUndo());
        assertEquals("", state.getProject().getFootprint("fp-import").getImagePattern().getImagePath());
    }

    @Test
    void successfulImportPushesUndoHistory() throws IOException {
        PatternPluginState state = newStateWithFootprint("fp-import");
        PatternActions actions = actions(state);
        actions.setPluginDataDir(tempDir);

        Path source = tempDir.resolve("logo.png");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", source.toFile());

        actions.importImageFromPath("fp-import", source.toString());

        assertTrue(state.getProjectHistory().canUndo());
        String importedPath = state.getProject().getFootprint("fp-import").getImagePattern().getImagePath();
        assertTrue(importedPath.startsWith("images/fp-import/"));
        assertTrue(PatternImageStore.loadRaster(
            tempDir,
            state.getProject().getFootprint("fp-import").getImagePattern()).isPresent());
    }

    private static PatternPluginState newStateWithFootprint(String footprintId) {
        PatternPluginState state = new PatternPluginState();
        PatternProject project = new PatternProject();
        PatternFootprint footprint = new PatternFootprint(footprintId, List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)));
        footprint.setSource(PatternSource.IMAGE);
        project.addFootprint(footprint);
        state.setProject(project);
        return state;
    }

    private static PatternActions actions(PatternPluginState state) {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            null,
            null,
            null,
            null);
        return new PatternActions(host, state, new Object());
    }
}
