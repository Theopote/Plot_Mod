package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeFormatUtilsTest {

    @Test
    void fortyFiveDegreesMapsToOneToOneRatio() {
        assertEquals(1.0f, SlopeFormatUtils.angleDegreesToBatterRatio(45.0f), 0.05f);
    }

    @Test
    void ratioRoundTripsThroughAngle() {
        float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(30.0f);
        assertEquals(30.0f, SlopeFormatUtils.batterRatioToAngleDegrees(ratio), 0.6f);
    }

    @Test
    void steepAngleProducesShorterHorizontalRun() {
        float shallow = SlopeFormatUtils.angleDegreesToBatterRatio(30.0f);
        float steep = SlopeFormatUtils.angleDegreesToBatterRatio(60.0f);
        assertTrue(shallow > steep);
    }
}
