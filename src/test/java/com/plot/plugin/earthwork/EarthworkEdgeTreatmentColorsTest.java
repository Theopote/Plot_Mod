package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.EdgeTreatment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EarthworkEdgeTreatmentColorsTest {

    @Test
    void eachTreatmentHasDistinctColor() {
        assertNotEquals(
            EarthworkEdgeTreatmentColors.colorFor(EdgeTreatment.VERTICAL),
            EarthworkEdgeTreatmentColors.colorFor(EdgeTreatment.CUT_FILL_SLOPE));
        assertNotEquals(
            EarthworkEdgeTreatmentColors.colorFor(EdgeTreatment.RETAINING_WALL),
            EarthworkEdgeTreatmentColors.colorFor(EdgeTreatment.MATCH_EXISTING));
    }
}
