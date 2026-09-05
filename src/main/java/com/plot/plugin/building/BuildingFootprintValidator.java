package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonUtils;
import com.plot.core.geometry.polygon.PolygonValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑轮廓认领前校验：复用 {@link PolygonValidator}，并容忍闭合环首尾重合点。
 */
public final class BuildingFootprintValidator {
    private BuildingFootprintValidator() {
    }

    public enum RejectReason {
        TOO_FEW_VERTICES("plugin.building.adopt_reject.too_few_vertices"),
        NON_FINITE("plugin.building.adopt_reject.non_finite"),
        DUPLICATE_VERTICES("plugin.building.adopt_reject.duplicate_vertices"),
        DEGENERATE_AREA("plugin.building.adopt_reject.degenerate_area"),
        SELF_INTERSECTION("plugin.building.adopt_reject.self_intersection"),
        EMPTY("plugin.building.adopt_reject.empty");

        private final String i18nKey;

        RejectReason(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String i18nKey() {
            return i18nKey;
        }

        static RejectReason fromIssue(String issue) {
            if (issue == null) {
                return EMPTY;
            }
            return switch (issue) {
                case "too_few_vertices" -> TOO_FEW_VERTICES;
                case "non_finite_coordinates" -> NON_FINITE;
                case "duplicate_vertices" -> DUPLICATE_VERTICES;
                case "degenerate_area" -> DEGENERATE_AREA;
                case "self_intersection" -> SELF_INTERSECTION;
                case "empty" -> EMPTY;
                default -> EMPTY;
            };
        }
    }

    public record Result(boolean valid, RejectReason reason, List<Vec2d> cleanedPoints) {
        public static Result ok(List<Vec2d> cleanedPoints) {
            return new Result(true, null, List.copyOf(cleanedPoints));
        }

        public static Result reject(RejectReason reason) {
            return new Result(false, reason, List.of());
        }
    }

    /**
     * 校验并返回清洗后的轮廓点（去掉闭合重复端点）。
     */
    public static Result validate(List<Vec2d> points) {
        if (points == null || points.isEmpty()) {
            return Result.reject(RejectReason.EMPTY);
        }
        List<Vec2d> cleaned = stripClosingDuplicate(points);
        PolygonValidator.ValidationResult validation =
            PolygonValidator.validateSimplePolygon(cleaned);
        if (validation.valid()) {
            return Result.ok(cleaned);
        }
        String firstIssue = validation.issues().isEmpty()
            ? "empty"
            : validation.issues().getFirst();
        return Result.reject(RejectReason.fromIssue(firstIssue));
    }

    public static boolean isAdoptable(List<Vec2d> points) {
        return validate(points).valid();
    }

    static List<Vec2d> stripClosingDuplicate(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            if (point != null) {
                copy.add(point);
            }
        }
        if (copy.size() >= 2) {
            Vec2d first = copy.getFirst();
            Vec2d last = copy.getLast();
            if (first.distance(last) <= PolygonUtils.CLOSE_RING_EPSILON) {
                copy.removeLast();
            }
        }
        return copy;
    }
}
