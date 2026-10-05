package com.plot.plugin.road.ui;

import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;

import java.util.ArrayList;
import java.util.List;

/** Legacy PVI draft helpers retained for unit tests (UI editing moved to ProfileEditSession). */
final class VerticalAlignmentPviDrafts {

    private VerticalAlignmentPviDrafts() {
    }

    static List<PointOfVerticalIntersection> buildPvis(List<PviDraft> drafts) {
        List<PviDraft> valid = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            PviDraft draft = drafts.get(i);
            if (!isStructurallyValid(draft, drafts, i)) {
                continue;
            }
            valid.add(draft);
        }
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            boolean middle = i > 0 && i < valid.size() - 1;
            try {
                pvis.add(valid.get(i).toPvi(middle));
            } catch (IllegalArgumentException ignored) {
                // Skip until user fixes invalid values.
            }
        }
        return pvis;
    }

    static boolean pvisEqual(List<PointOfVerticalIntersection> left, List<PointOfVerticalIntersection> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            PointOfVerticalIntersection a = left.get(i);
            PointOfVerticalIntersection b = right.get(i);
            if (Double.compare(a.getStation(), b.getStation()) != 0
                || Double.compare(a.getElevation(), b.getElevation()) != 0
                || a.getConstraint() != b.getConstraint()) {
                return false;
            }
            Double curveA = a.getCurveLength();
            Double curveB = b.getCurveLength();
            if (curveA == null && curveB == null) {
                continue;
            }
            if (curveA == null || curveB == null
                || Double.compare(curveA, curveB) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStructurallyValid(PviDraft draft, List<PviDraft> all, int index) {
        if (draft.station < 0.0f) {
            return false;
        }
        for (int i = 0; i < index; i++) {
            if (Math.abs(all.get(i).station - draft.station) < 1e-6f) {
                return false;
            }
        }
        return true;
    }

    static final class PviDraft {
        float station;
        float elevation;
        float curveLength;
        VerticalControlPointConstraint constraint = VerticalControlPointConstraint.FREE;

        static PviDraft from(PointOfVerticalIntersection pvi, int index, int total) {
            PviDraft draft = new PviDraft();
            draft.station = (float) pvi.getStation();
            draft.elevation = (float) pvi.getElevation();
            boolean middle = index > 0 && index < total - 1;
            draft.curveLength = middle && pvi.hasCurve() ? pvi.getCurveLength().floatValue() : 0f;
            draft.constraint = pvi.getConstraint();
            return draft;
        }

        static PviDraft defaultEntry(List<PviDraft> existing, double roadLength) {
            PviDraft draft = new PviDraft();
            if (existing.isEmpty()) {
                draft.station = 0f;
                draft.elevation = 64f;
            } else if (existing.size() == 1) {
                draft.station = (float) roadLength;
                draft.elevation = existing.getFirst().elevation;
            } else {
                float maxStation = 0f;
                for (PviDraft entry : existing) {
                    maxStation = Math.max(maxStation, entry.station);
                }
                draft.station = Math.min((float) roadLength, maxStation + 10f);
                draft.elevation = 64f;
            }
            draft.curveLength = 0f;
            return draft;
        }

        PointOfVerticalIntersection toPvi(boolean allowCurve) {
            Double curve = allowCurve && curveLength > 0f ? (double) curveLength : null;
            return new PointOfVerticalIntersection(station, elevation, curve, constraint);
        }
    }
}
