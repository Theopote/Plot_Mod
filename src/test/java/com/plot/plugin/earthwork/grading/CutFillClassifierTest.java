package com.plot.plugin.earthwork.grading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutFillClassifierTest {

    @Test
    void deltaReportsCutWhenGroundAboveTarget() {
        CutFillClassifier.ColumnDelta delta = CutFillClassifier.delta(68, 64);
        assertEquals(4L, delta.cutVolume());
        assertEquals(0L, delta.fillVolume());
        assertEquals(CutFillClassifier.Kind.CUT, CutFillClassifier.kind(68, 64));
    }

    @Test
    void deltaReportsFillWhenGroundBelowTarget() {
        CutFillClassifier.ColumnDelta delta = CutFillClassifier.delta(62, 65);
        assertEquals(0L, delta.cutVolume());
        assertEquals(3L, delta.fillVolume());
        assertEquals(CutFillClassifier.Kind.FILL, CutFillClassifier.kind(62, 65));
    }

    @Test
    void deltaIsNoOpWhenGroundMatchesTarget() {
        CutFillClassifier.ColumnDelta delta = CutFillClassifier.delta(64, 64);
        assertTrue(delta.isNoOp());
        assertEquals(CutFillClassifier.Kind.NONE, CutFillClassifier.kind(64, 64));
    }
}
