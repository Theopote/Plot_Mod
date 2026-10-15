package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.VerticalAlignmentViolation;
import com.plot.plugin.road.vertical.VerticalAlignmentViolationKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileAlignmentViolationFormatterTest {

    @Test
    void formatsKnownViolationKinds() {
        assertFalse(ProfileAlignmentViolationFormatter.format(
            VerticalAlignmentViolation.between(
                VerticalAlignmentViolationKind.VERTICAL_CURVE_OVERLAP, 1, 2)).isBlank());
        assertFalse(ProfileAlignmentViolationFormatter.format(
            VerticalAlignmentViolation.of(
                VerticalAlignmentViolationKind.VERTICAL_CURVE_OUT_OF_RANGE, 0)).isBlank());
        assertFalse(ProfileAlignmentViolationFormatter.format(
            VerticalAlignmentViolation.between(
                VerticalAlignmentViolationKind.PVI_STATION_DUPLICATE, 0, 1)).isBlank());
        assertTrue(ProfileAlignmentViolationFormatter.format(null).isBlank());
    }
}
