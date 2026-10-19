package com.plot.ui.tools.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairNumericInputBufferTest {

    private PairNumericInputBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new PairNumericInputBuffer();
    }

    @Test
    void parseSingleWithoutSeparator() {
        buffer.append('1');
        buffer.append('0');
        assertEquals(10.0, buffer.tryParseSingle(), 1e-6);
        assertNull(buffer.tryParsePair());
    }

    @Test
    void parsePairWithComma() {
        type("10,5");
        assertNull(buffer.tryParseSingle());
        assertArrayEquals(new double[] {10.0, 5.0}, buffer.tryParsePair(), 1e-6);
    }

    @Test
    void parsePairWithDecimals() {
        type("10.5,20.25");
        assertArrayEquals(new double[] {10.5, 20.25}, buffer.tryParsePair(), 1e-6);
    }

    @Test
    void backspaceRemovesSeparator() {
        type("10,");
        assertTrue(buffer.backspace());
        assertEquals(10.0, buffer.tryParseSingle(), 1e-6);
    }

    private void type(String text) {
        for (char c : text.toCharArray()) {
            assertTrue(buffer.append(c), "failed on " + c);
        }
    }
}
