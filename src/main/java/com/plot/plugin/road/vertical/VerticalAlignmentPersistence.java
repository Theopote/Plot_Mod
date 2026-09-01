package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RoadVerticalAlignment} 与 sidecar JSON DTO 互转。
 */
public final class VerticalAlignmentPersistence {

    public static final class PviData {
        public double station;
        public double elevation;
        public Double curveLength;
        public VerticalControlPointConstraint constraint;
    }

    public static final class VerticalAlignmentData {
        public List<PviData> pvis = new ArrayList<>();
    }

    private VerticalAlignmentPersistence() {
    }

    public static VerticalAlignmentData toData(RoadVerticalAlignment alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return null;
        }
        VerticalAlignmentData data = new VerticalAlignmentData();
        for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
            PviData pviData = new PviData();
            pviData.station = pvi.getStation();
            pviData.elevation = pvi.getElevation();
            if (pvi.hasCurve()) {
                pviData.curveLength = pvi.getCurveLength();
            }
            if (pvi.getConstraint() != VerticalControlPointConstraint.FREE) {
                pviData.constraint = pvi.getConstraint();
            }
            data.pvis.add(pviData);
        }
        return data.pvis.isEmpty() ? null : data;
    }

    public static RoadVerticalAlignment fromData(VerticalAlignmentData data) {
        if (data == null || data.pvis == null || data.pvis.isEmpty()) {
            return null;
        }
        RoadVerticalAlignment alignment = new RoadVerticalAlignment();
        for (PviData pviData : data.pvis) {
            if (pviData == null) {
                continue;
            }
            Double curveLength = pviData.curveLength;
            if (curveLength != null && curveLength <= 0.0) {
                curveLength = null;
            }
            alignment.addPvi(new PointOfVerticalIntersection(
                pviData.station,
                pviData.elevation,
                curveLength,
                pviData.constraint
            ));
        }
        return alignment.isEmpty() ? null : alignment;
    }
}
