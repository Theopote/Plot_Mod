package com.plot.plugin.road.ui;

import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentEditorTest {

    @Test
    void buildPvisPreservesDraftOrderAndAppliesCurveOnlyOnMiddlePoints() {
        List<VerticalAlignmentPviDrafts.PviDraft> drafts = new ArrayList<>();
        VerticalAlignmentPviDrafts.PviDraft start = new VerticalAlignmentPviDrafts.PviDraft();
        start.station = 0f;
        start.elevation = 80f;
        drafts.add(start);

        VerticalAlignmentPviDrafts.PviDraft middle = new VerticalAlignmentPviDrafts.PviDraft();
        middle.station = 50f;
        middle.elevation = 110f;
        middle.curveLength = 20f;
        drafts.add(middle);

        VerticalAlignmentPviDrafts.PviDraft end = new VerticalAlignmentPviDrafts.PviDraft();
        end.station = 100f;
        end.elevation = 100f;
        drafts.add(end);

        List<PointOfVerticalIntersection> pvis = VerticalAlignmentPviDrafts.buildPvis(drafts);
        assertEquals(3, pvis.size());
        assertEquals(0.0, pvis.get(0).getStation(), 1e-6);
        assertEquals(50.0, pvis.get(1).getStation(), 1e-6);
        assertEquals(100.0, pvis.get(2).getStation(), 1e-6);
        assertTrue(pvis.get(1).hasCurve());
        assertEquals(20.0, pvis.get(1).getCurveLength(), 1e-6);
        assertTrue(!pvis.getLast().hasCurve());
    }

    @Test
    void buildPvisDoesNotSortByStation() {
        List<VerticalAlignmentPviDrafts.PviDraft> drafts = new ArrayList<>();
        VerticalAlignmentPviDrafts.PviDraft end = new VerticalAlignmentPviDrafts.PviDraft();
        end.station = 100f;
        end.elevation = 100f;
        drafts.add(end);

        VerticalAlignmentPviDrafts.PviDraft start = new VerticalAlignmentPviDrafts.PviDraft();
        start.station = 0f;
        start.elevation = 80f;
        drafts.add(start);

        List<PointOfVerticalIntersection> pvis = VerticalAlignmentPviDrafts.buildPvis(drafts);
        assertEquals(2, pvis.size());
        assertEquals(100.0, pvis.getFirst().getStation(), 1e-6);
        assertEquals(0.0, pvis.getLast().getStation(), 1e-6);
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

        assertTrue(!VerticalAlignmentPviDrafts.pvisEqual(left, right));
        assertTrue(VerticalAlignmentPviDrafts.pvisEqual(left, left));
    }

    @Test
    void defaultEntryCreatesRoadEndpointsForSecondPvi() {
        List<VerticalAlignmentPviDrafts.PviDraft> drafts = new ArrayList<>();
        VerticalAlignmentPviDrafts.PviDraft first = new VerticalAlignmentPviDrafts.PviDraft();
        first.station = 0f;
        first.elevation = 70f;
        drafts.add(first);

        VerticalAlignmentPviDrafts.PviDraft second =
            VerticalAlignmentPviDrafts.PviDraft.defaultEntry(drafts, 100.0);
        assertEquals(100f, second.station, 1e-6);
        assertEquals(70f, second.elevation, 1e-6);
    }
}
