package com.plot.plugin.powerline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineSelectionSetTest {

    @Test
    void hasMultipleSelectedWhenMoreThanOneLineChosen() {
        PowerLineSelectionSet selection = new PowerLineSelectionSet();
        assertFalse(selection.hasMultipleSelected());

        selection.select("line-a", false);
        assertFalse(selection.hasMultipleSelected());

        selection.select("line-b", true);
        assertTrue(selection.hasMultipleSelected());
        assertTrue(selection.contains("line-a"));
        assertTrue(selection.contains("line-b"));
    }

    @Test
    void selectAllMarksMultipleSelection() {
        PowerLineSelectionSet selection = new PowerLineSelectionSet();
        selection.selectAll(java.util.List.of("line-a", "line-b", "line-c"));
        assertTrue(selection.hasMultipleSelected());
        assertEquals(3, selection.size());
    }
}
