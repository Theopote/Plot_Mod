package com.plot.plugin.road;

import com.plot.plugin.road.SlopeFormatUtils.DisplayFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SlopeFormatUtilsTest {

    @Test
    void percentAndRatioAreReciprocal() {
        assertEquals(10.0f, SlopeFormatUtils.horizontalRatioToPercent(10.0f), 1e-4f);
        assertEquals(10.0f, SlopeFormatUtils.percentToHorizontalRatio(10.0f), 1e-4f);
        assertEquals(66.6667f, SlopeFormatUtils.horizontalRatioToPercent(1.5f), 0.01f);
    }

    @Test
    void parseGradePercentFormats() {
        assertEquals(10.0f, SlopeFormatUtils.parseInput("10%", DisplayFormat.PERCENT, false));
        assertEquals(8.5f, SlopeFormatUtils.parseInput("8.5", DisplayFormat.PERCENT, false));
        assertEquals(10.0f, SlopeFormatUtils.parseInput("1:10", DisplayFormat.RATIO, false));
    }

    @Test
    void parseBatterRatioFormats() {
        assertEquals(1.5f, SlopeFormatUtils.parseInput("1:1.5", DisplayFormat.RATIO, true));
        assertEquals(2.0f, SlopeFormatUtils.parseInput("2", DisplayFormat.RATIO, true));
        assertEquals(2.0f, SlopeFormatUtils.parseInput("50%", DisplayFormat.PERCENT, true), 0.01f);
    }

    @Test
    void parseRejectsInvalidInput() {
        assertNull(SlopeFormatUtils.parseInput("", DisplayFormat.PERCENT, false));
        assertNull(SlopeFormatUtils.parseInput("abc", DisplayFormat.PERCENT, false));
    }

    @Test
    void clampRespectsEngineeringBounds() {
        assertEquals(45.0f, SlopeFormatUtils.clampGradePercent(90.0f));
        assertEquals(5.0f, SlopeFormatUtils.clampBatterRatio(9.0f));
    }

    @Test
    void formatDualDisplaysEquivalentValues() {
        assertEquals("10.0% ≈ 1:10.0", SlopeFormatUtils.formatDualGrade(10.0f));
        assertEquals("1:1.5 ≈ 66.7%", SlopeFormatUtils.formatDualBatter(1.5f));
    }
}
