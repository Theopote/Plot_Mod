package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPreviewKeyTest {

    @Test
    void matchesSameSelectionAndConfig() {
        PatternProject project = projectWithFootprints("a", "b");
        PatternSelectionSet selection = new PatternSelectionSet();
        selection.select("a", false);

        PatternPreviewKey key = PatternPreviewKey.capture(
            selection.resolve(project),
            3L,
            null);

        assertTrue(key.matches(project, selection, 3L, null));
    }

    @Test
    void rejectsDifferentSelection() {
        PatternProject project = projectWithFootprints("a", "b");
        PatternSelectionSet previewSelection = new PatternSelectionSet();
        previewSelection.select("a", false);
        PatternPreviewKey key = PatternPreviewKey.capture(
            previewSelection.resolve(project),
            1L,
            null);

        PatternSelectionSet currentSelection = new PatternSelectionSet();
        currentSelection.select("b", false);

        assertFalse(key.matches(project, currentSelection, 1L, null));
    }

    @Test
    void rejectsStaleProjectRevision() {
        PatternProject project = projectWithFootprints("a");
        PatternSelectionSet selection = new PatternSelectionSet();
        selection.select("a", false);
        PatternPreviewKey key = PatternPreviewKey.capture(selection.resolve(project), 2L, null);

        assertFalse(key.matches(project, selection, 3L, null));
    }

    @Test
    void rejectsConfigChange() {
        PatternProject project = projectWithFootprints("a");
        PatternSelectionSet selection = new PatternSelectionSet();
        selection.select("a", false);
        PatternPreviewKey key = PatternPreviewKey.capture(selection.resolve(project), 1L, null);

        PatternFootprint footprint = project.getFootprint("a");
        ProceduralPatternConfig pattern = footprint.getPattern();
        pattern.setTileSize(pattern.getTileSize() + 1.0);
        footprint.setPattern(pattern);

        assertFalse(key.matches(project, selection, 1L, null));
    }

    private static PatternProject projectWithFootprints(String... ids) {
        PatternProject project = new PatternProject();
        for (String id : ids) {
            PatternFootprint footprint = new PatternFootprint(
                id,
                List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 4)));
            footprint.setSource(PatternSource.PROCEDURAL);
            project.addFootprint(footprint);
        }
        return project;
    }
}
