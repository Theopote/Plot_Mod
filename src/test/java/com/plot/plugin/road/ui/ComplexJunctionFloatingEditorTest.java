package com.plot.plugin.road.ui;

import com.plot.plugin.road.ui.editor.RoadFloatingEditor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComplexJunctionFloatingEditorTest {

    @Test
    void finishOrPromptWhenClosedReturnsClosed() {
        ComplexJunctionFloatingEditor editor = new ComplexJunctionFloatingEditor();
        assertFalse(editor.isOpen());
        assertEquals(RoadFloatingEditor.FinishResult.CLOSED, editor.finishOrPrompt(null));
    }

    @Test
    void openWithoutContextDoesNotOpen() {
        ComplexJunctionFloatingEditor editor = new ComplexJunctionFloatingEditor();
        editor.open(null, "n1");
        assertFalse(editor.isOpen());
    }

    @Test
    void controlsExposeSingletonEditor() {
        assertSame(
            ComplexJunctionControls.floatingEditor(),
            ComplexJunctionControls.floatingEditor());
    }
}
