package com.plot.core.command.commands;

import com.plot.ui.panel.layer.LayerStructureSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerStructureEditCommandTest {

    @Test
    void sameStructureDetectsIdenticalSnapshots() {
        LayerStructureSnapshot first = LayerStructureSnapshot.capture();
        LayerStructureSnapshot second = LayerStructureSnapshot.capture();
        assertTrue(first.sameStructureAs(second));
    }

    @Test
    void commandUndoRedoDelegatesToSnapshots() {
        LayerStructureSnapshot before = LayerStructureSnapshot.capture();
        LayerStructureSnapshot after = LayerStructureSnapshot.capture();

        LayerStructureEditCommand command = new LayerStructureEditCommand(
                before,
                after,
                "history.plot.layer_structure.reorder");

        command.undo();
        command.redo();
        assertFalse(command.getDescription().isBlank());
    }
}
