package com.plot.plugin.road.ui;

import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentEditorTest {

    @Test
    void buildPvisSortsAndAppliesCurveOnlyOnMiddlePoints() {
        List<VerticalAlignmentEditor.PviDraft> drafts = new ArrayList<>();
        VerticalAlignmentEditor.PviDraft end = new VerticalAlignmentEditor.PviDraft();
        end.station = 100f;
        end.elevation = 100f;
        drafts.add(end);

        VerticalAlignmentEditor.PviDraft start = new VerticalAlignmentEditor.PviDraft();
        start.station = 0f;
        start.elevation = 80f;
        drafts.add(start);

        VerticalAlignmentEditor.PviDraft middle = new VerticalAlignmentEditor.PviDraft();
        middle.station = 50f;
        middle.elevation = 110f;
        middle.curveLength = 20f;
        drafts.add(middle);

        List<PointOfVerticalIntersection> pvis = VerticalAlignmentEditor.buildPvis(drafts);
        assertEquals(3, pvis.size());
        assertEquals(0.0, pvis.getFirst().getStation(), 1e-6);
        assertTrue(pvis.get(1).hasCurve());
        assertEquals(20.0, pvis.get(1).getCurveLength(), 1e-6);
        assertTrue(!pvis.getLast().hasCurve());
    }

    @Test
    void pvisEqualDetectsCurveLengthChanges() {
        List<PointOfVerticalIntersection> left = List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.withCurve(50.0, 100.0, 20.0),
            PointOfVerticalIntersection.of(100.0, 90.0));
        List<PointOfVerticalIntersection> right = List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.withCurve(50.0, 100.0, 30.0),
            PointOfVerticalIntersection.of(100.0, 90.0));

        assertTrue(!VerticalAlignmentEditor.pvisEqual(left, right));
        assertTrue(VerticalAlignmentEditor.pvisEqual(left, left));
    }

    @Test
    void defaultEntryCreatesRoadEndpointsForSecondPvi() {
        List<VerticalAlignmentEditor.PviDraft> drafts = new ArrayList<>();
        VerticalAlignmentEditor.PviDraft first = new VerticalAlignmentEditor.PviDraft();
        first.station = 0f;
        first.elevation = 70f;
        drafts.add(first);

        VerticalAlignmentEditor.PviDraft second =
            VerticalAlignmentEditor.PviDraft.defaultEntry(drafts, 100.0);
        assertEquals(100f, second.station, 1e-6);
        assertEquals(70f, second.elevation, 1e-6);
    }
}
