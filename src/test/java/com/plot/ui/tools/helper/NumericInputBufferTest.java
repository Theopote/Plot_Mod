package com.plot.ui.tools.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericInputBufferTest {

    private NumericInputBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new NumericInputBuffer();
    }

    @Test
    void appendDigitsAndParse() {
        assertTrue(buffer.append('4'));
        assertTrue(buffer.append('5'));
        assertEquals(45.0, buffer.tryParse(), 1e-6);
    }

    @Test
    void partialInputReturnsNull() {
        assertTrue(buffer.append('-'));
        assertNull(buffer.tryParse());
    }

    @Test
    void backspaceRemovesLastChar() {
        buffer.append('1');
        buffer.append('2');
        assertTrue(buffer.backspace());
        assertEquals(1.0, buffer.tryParse(), 1e-6);
    }

    @Test
    void clearResetsBuffer() {
        buffer.append('9');
        buffer.clear();
        assertTrue(buffer.isEmpty());
        assertNull(buffer.tryParse());
    }

    @Test
    void rejectsSecondDecimalSeparator() {
        assertTrue(buffer.append('1'));
        assertTrue(buffer.append('.'));
        assertFalse(buffer.append(','));
    }
}
