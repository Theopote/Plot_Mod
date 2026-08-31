package com.plot.plugin.road.model.section;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionEngineeringEqualityTest {

    @Test
    void sameWidthDifferentLaneCountIsNotEqual() {
        RoadCrossSection left = sectionWithWidth(9);
        left.getCarriageway().setLaneCount(2);

        RoadCrossSection right = sectionWithWidth(9);
        right.getCarriageway().setLaneCount(4);

        assertFalse(RoadCrossSectionEngineeringEquality.equals(left, right));
    }

    @Test
    void sameWidthDifferentSidewalkIsNotEqual() {
        RoadCrossSection left = sectionWithWidth(9);
        left.getSidewalk().setEnabled(true);

        RoadCrossSection right = sectionWithWidth(9);
        right.getSidewalk().setEnabled(false);

        assertFalse(RoadCrossSectionEngineeringEquality.equals(left, right));
    }

    @Test
    void sameWidthDifferentBikeLaneIsNotEqual() {
        RoadCrossSection left = sectionWithWidth(9);
        left.getBikeLane().setEnabled(false);

        RoadCrossSection right = sectionWithWidth(9);
        right.getBikeLane().setEnabled(true);

        assertFalse(RoadCrossSectionEngineeringEquality.equals(left, right));
    }

    @Test
    void identicalEngineeringStateIsEqual() {
        RoadCrossSection left = fullyConfiguredSection();
        RoadCrossSection right = fullyConfiguredSection();

        assertTrue(RoadCrossSectionEngineeringEquality.equals(left, right));
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }

    private static RoadCrossSection fullyConfiguredSection() {
        RoadCrossSection section = sectionWithWidth(9);
        section.getCarriageway().setLaneCount(2);
        section.getSidewalk().setEnabled(true);
        section.getBikeLane().setEnabled(false);
        section.getMedian().setEnabled(true);
        section.getMedian().setWidth(2);
        return section;
    }
}
