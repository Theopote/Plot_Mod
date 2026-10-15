package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.VerticalAlignmentViolation;
import com.plot.plugin.road.vertical.VerticalAlignmentViolationKind;
import com.plot.utils.PlotI18n;

/** Compact editor messages for {@link com.plot.plugin.road.vertical.VerticalAlignmentValidator} results. */
public final class ProfileAlignmentViolationFormatter {

    private ProfileAlignmentViolationFormatter() {
    }

    public static String format(VerticalAlignmentViolation violation) {
        if (violation == null) {
            return "";
        }
        int pviOne = violation.pviIndex() + 1;
        Integer related = violation.relatedPviIndex();
        int relatedOne = related != null ? related + 1 : pviOne;
        VerticalAlignmentViolationKind kind = violation.kind();
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case PVI_STATION_DUPLICATE -> PlotI18n.tr(
                "plugin.road.profile_violation.pvi_station_duplicate", pviOne, relatedOne);
            case PVI_STATION_NOT_INCREASING -> PlotI18n.tr(
                "plugin.road.profile_violation.pvi_station_not_increasing", pviOne);
            case VERTICAL_CURVE_OVERLAP -> PlotI18n.tr(
                "plugin.road.profile_violation.vertical_curve_overlap", pviOne, relatedOne);
            case VERTICAL_CURVE_OUT_OF_RANGE -> PlotI18n.tr(
                "plugin.road.profile_violation.vertical_curve_out_of_range", pviOne);
        };
    }
}
