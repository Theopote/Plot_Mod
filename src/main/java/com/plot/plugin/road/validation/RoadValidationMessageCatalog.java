package com.plot.plugin.road.validation;

import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.alignment.HorizontalAlignmentViolationKind;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.model.RoadTopologyViolationKind;
import com.plot.plugin.road.vertical.VerticalAlignmentViolationKind;

import java.util.HashMap;
import java.util.Map;

/**
 * 工程校验码 / 报告键 → 用户可读消息。底层枚举与 messageKey 保持不变。
 */
public final class RoadValidationMessageCatalog {

    private static final Map<String, IssueTemplate> REPORT_TEMPLATES = buildReportTemplates();

    private RoadValidationMessageCatalog() {
    }

    public static RoadValidationMessage fromReportItem(RoadNetworkValidationReport.Item item) {
        if (item == null) {
            return null;
        }
        IssueTemplate template = REPORT_TEMPLATES.get(item.messageKey());
        if (template != null) {
            return template.toMessage(item.level(), item.args());
        }
        return RoadValidationMessage.of(item.level(), fallbackIssueId(item.messageKey()), item.args());
    }

    public static RoadValidationMessage fromTopologyKind(RoadTopologyViolationKind kind) {
        return switch (kind) {
            case ROAD_DISCONNECTED -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING,
                "road_disconnected_single",
                RoadValidationAction.REPAIR_ROAD_TOPOLOGY);
            case ROAD_BRANCHING -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING,
                "road_branching_single",
                RoadValidationAction.REPAIR_ROAD_TOPOLOGY);
            case ROAD_CYCLE -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "road_cycle_single");
            case ROAD_ORDER_MISMATCH -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING,
                "road_order_mismatch_single",
                RoadValidationAction.SYNC_SEGMENT_ORDER);
        };
    }

    public static RoadValidationMessage fromVerticalKind(VerticalAlignmentViolationKind kind) {
        return switch (kind) {
            case PVI_STATION_DUPLICATE -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "vertical_pvi_duplicate");
            case PVI_STATION_NOT_INCREASING -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "vertical_pvi_order");
            case VERTICAL_CURVE_OVERLAP -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "vertical_curve_overlap");
            case VERTICAL_CURVE_OUT_OF_RANGE -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "vertical_curve_out_of_range");
        };
    }

    public static RoadValidationMessage fromHorizontalKind(HorizontalAlignmentViolationKind kind) {
        if (kind == HorizontalAlignmentViolationKind.ALIGNMENT_TOPOLOGY_MISMATCH) {
            return RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING,
                "alignment_topology_mismatch_single");
        }
        return null;
    }

    public static RoadValidationMessage fromCenterlineStatus(CenterlineEditStatus status) {
        return switch (status) {
            case JUNCTION_ENDPOINT_CONFLICT -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.ERROR,
                "junction_endpoint_conflict",
                RoadValidationAction.SNAP_TO_JUNCTION);
            case HORIZONTAL_ALIGNMENT_NOT_DEFINED -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "horizontal_alignment_missing");
            case ROAD_NOT_STATIONABLE -> RoadValidationMessage.of(
                RoadNetworkValidationReport.Level.WARNING, "road_not_stationable");
            default -> null;
        };
    }

    private static Map<String, IssueTemplate> buildReportTemplates() {
        Map<String, IssueTemplate> map = new HashMap<>();
        map.put("plugin.road.validation.roads_connected",
            IssueTemplate.ok("network_connected"));
        map.put("plugin.road.validation.roads_partially_connected",
            IssueTemplate.warning("network_partially_connected"));
        map.put("plugin.road.validation.disconnected_components",
            IssueTemplate.warning("network_disconnected_components"));
        map.put("plugin.road.validation.no_dead_ends", IssueTemplate.ok("no_dead_ends"));
        map.put("plugin.road.validation.dead_ends", IssueTemplate.warning("dead_ends"));
        map.put("plugin.road.validation.no_slope_overlap", IssueTemplate.ok("no_slope_overlap"));
        map.put("plugin.road.validation.slope_override_overlap",
            IssueTemplate.warning("slope_override_overlap"));
        map.put("plugin.road.validation.junctions_within_grade",
            IssueTemplate.ok("junctions_within_grade"));
        map.put("plugin.road.validation.junctions_exceed_grade",
            IssueTemplate.warning("junctions_exceed_grade"));
        map.put("plugin.road.validation.intersections_resolved",
            IssueTemplate.ok("intersections_resolved"));
        map.put("plugin.road.validation.intersections_pending",
            IssueTemplate.warning("intersections_pending", RoadValidationAction.RECONCILE_INTERSECTIONS));
        map.put("plugin.road.validation.intersections_incomplete",
            IssueTemplate.error("intersections_incomplete", RoadValidationAction.RECONCILE_INTERSECTIONS));
        map.put("plugin.road.validation.topology_issues",
            IssueTemplate.warning("topology_issues", RoadValidationAction.REPAIR_ROAD_TOPOLOGY));
        map.put("plugin.road.validation.road_topology_ok", IssueTemplate.ok("road_topology_ok"));
        map.put("plugin.road.validation.road_disconnected",
            IssueTemplate.warning("road_disconnected", RoadValidationAction.REPAIR_ROAD_TOPOLOGY));
        map.put("plugin.road.validation.road_branching",
            IssueTemplate.warning("road_branching", RoadValidationAction.REPAIR_ROAD_TOPOLOGY));
        map.put("plugin.road.validation.road_cycle",
            IssueTemplate.warning("road_cycle"));
        map.put("plugin.road.validation.road_order_mismatch",
            IssueTemplate.warning("road_order_mismatch", RoadValidationAction.SYNC_SEGMENT_ORDER));
        map.put("plugin.road.validation.vertical_alignment_length_ok",
            IssueTemplate.ok("vertical_length_ok"));
        map.put("plugin.road.validation.vertical_alignment_length_mismatch",
            IssueTemplate.warning("vertical_length_mismatch"));
        map.put("plugin.road.validation.vertical_alignment_grade_ok",
            IssueTemplate.ok("vertical_grade_ok"));
        map.put("plugin.road.validation.vertical_alignment_grade_exceeds",
            IssueTemplate.warning("vertical_grade_exceeds", RoadValidationAction.SMOOTH_GRADE));
        map.put("plugin.road.validation.vertical_alignment_curve_ok",
            IssueTemplate.ok("vertical_curve_ok"));
        map.put("plugin.road.validation.vertical_alignment_curve_overlap",
            IssueTemplate.warning("vertical_curve_overlap"));
        map.put("plugin.road.validation.vertical_alignment_curve_range_ok",
            IssueTemplate.ok("vertical_curve_range_ok"));
        map.put("plugin.road.validation.vertical_alignment_curve_out_of_range",
            IssueTemplate.warning("vertical_curve_out_of_range"));
        map.put("plugin.road.validation.vertical_alignment_station_order_ok",
            IssueTemplate.ok("vertical_pvi_order_ok"));
        map.put("plugin.road.validation.vertical_alignment_station_order_invalid",
            IssueTemplate.warning("vertical_pvi_order_invalid"));
        map.put("plugin.road.validation.horizontal_alignment_length_ok",
            IssueTemplate.ok("horizontal_length_ok"));
        map.put("plugin.road.validation.horizontal_alignment_length_mismatch",
            IssueTemplate.warning("horizontal_length_mismatch"));
        map.put("plugin.road.validation.horizontal_alignment_centerline_ok",
            IssueTemplate.ok("horizontal_centerline_ok"));
        map.put("plugin.road.validation.horizontal_alignment_centerline_deviation",
            IssueTemplate.warning("horizontal_centerline_deviation", RoadValidationAction.MATERIALIZE_ALIGNMENT));
        map.put("plugin.road.validation.horizontal_alignment_junction_ok",
            IssueTemplate.ok("horizontal_junction_ok"));
        map.put("plugin.road.validation.horizontal_alignment_junction_conflict",
            IssueTemplate.warning("junction_endpoint_conflict", RoadValidationAction.SNAP_TO_JUNCTION));
        map.put("plugin.road.validation.horizontal_alignment_topology_ok",
            IssueTemplate.ok("horizontal_topology_ok"));
        map.put("plugin.road.validation.horizontal_alignment_topology_mismatch",
            IssueTemplate.warning("alignment_topology_mismatch"));
        return Map.copyOf(map);
    }

    private static String fallbackIssueId(String messageKey) {
        if (messageKey == null || messageKey.isBlank()) {
            return "unknown";
        }
        String prefix = "plugin.road.validation.";
        if (messageKey.startsWith(prefix)) {
            return messageKey.substring(prefix.length());
        }
        return "unknown";
    }

    private record IssueTemplate(
            RoadNetworkValidationReport.Level level,
            String issueId,
            RoadValidationAction action) {

        static IssueTemplate ok(String issueId) {
            return new IssueTemplate(RoadNetworkValidationReport.Level.OK, issueId, null);
        }

        static IssueTemplate warning(String issueId) {
            return new IssueTemplate(RoadNetworkValidationReport.Level.WARNING, issueId, null);
        }

        static IssueTemplate warning(String issueId, RoadValidationAction action) {
            return new IssueTemplate(RoadNetworkValidationReport.Level.WARNING, issueId, action);
        }

        static IssueTemplate error(String issueId, RoadValidationAction action) {
            return new IssueTemplate(RoadNetworkValidationReport.Level.ERROR, issueId, action);
        }

        RoadValidationMessage toMessage(RoadNetworkValidationReport.Level itemLevel, Object[] args) {
            RoadNetworkValidationReport.Level severity = itemLevel != null ? itemLevel : level;
            if (action != null) {
                return RoadValidationMessage.of(severity, issueId, action, args);
            }
            return RoadValidationMessage.of(severity, issueId, args);
        }
    }
}
