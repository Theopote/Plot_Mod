package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.vertical.VerticalAlignmentGradeSmoother;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalProfileDesignRules;
import com.plot.plugin.road.validation.RoadValidationAction;
import com.plot.plugin.road.validation.RoadValidationDrillDown;
import com.plot.plugin.road.validation.RoadValidationMessage;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * 将 {@link RoadValidationMessage} 渲染为 ImGui 人话提示与可选修复按钮。
 */
public final class RoadValidationMessageUi {

    private RoadValidationMessageUi() {
    }

    public static void render(RoadValidationMessage message) {
        render(message, null, null, null, "");
    }

    public static void render(
            RoadValidationMessage message,
            RoadUiContext ctx,
            RoadNetwork network,
            Road road) {
        render(message, ctx, network, road, "");
    }

    public static void render(
            RoadValidationMessage message,
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            String imguiSuffix) {
        if (message == null) {
            return;
        }
        String suffix = imguiSuffix != null ? imguiSuffix : "";
        boolean networkLevel = road == null && ctx != null && network != null;
        String issueId = message.issueId();
        List<String> affectedRoadIds = networkLevel && issueId != null && RoadValidationDrillDown.supports(issueId)
            ? RoadValidationDrillDown.affectedRoadIds(issueId, network, ctx.networkManager().getConfig())
            : List.of();

        if (networkLevel && affectedRoadIds.size() == 1) {
            renderDrillDownTitle(message, ctx, network, affectedRoadIds.getFirst(), suffix);
        } else if (networkLevel && affectedRoadIds.size() > 1) {
            renderDrillDownTree(message, ctx, network, affectedRoadIds, suffix);
        } else {
            renderTitle(message);
        }

        if (message.hasDetail()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(message.detailKey(), message.args()));
        }
        if (message.hasAction() && ctx != null) {
            ImGui.indent();
            String actionLabel = PlotI18n.tr(message.actionKey());
            if (ImGui.smallButton(actionLabel + "##val_action_" + message.action().name() + suffix)) {
                executeAction(message.action(), ctx, network, road);
            }
            ImGui.unindent();
        }
    }

    private static void renderTitle(RoadValidationMessage message) {
        int color = severityColor(message.severity());
        String prefix = severityPrefix(message.severity());
        ImGui.textColored(color, prefix + PlotI18n.tr(message.titleKey(), message.args()));
    }

    private static void renderDrillDownTitle(
            RoadValidationMessage message,
            RoadUiContext ctx,
            RoadNetwork network,
            String roadId,
            String suffix) {
        int color = severityColor(message.severity());
        String prefix = severityPrefix(message.severity());
        String title = prefix + PlotI18n.tr(message.titleKey(), message.args());
        Road road = network.getRoad(roadId);
        String roadLabel = road != null
            ? RoadEdgeListHelper.formatRoadLabel(network, road)
            : roadId;
        if (ImGui.selectable(title + "##val_drill_title_" + roadId + suffix, false)) {
            ctx.requestEditRoad(roadId);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.validation_drill_down_single", roadLabel));
        }
        ImGui.sameLine();
        ImGui.textColored(PluginUiColors.HINT_GRAY, "\u2192 " + roadLabel);
    }

    private static void renderDrillDownTree(
            RoadValidationMessage message,
            RoadUiContext ctx,
            RoadNetwork network,
            List<String> affectedRoadIds,
            String suffix) {
        int color = severityColor(message.severity());
        String prefix = severityPrefix(message.severity());
        String title = prefix + PlotI18n.tr(message.titleKey(), message.args());
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.FramePadding;
        if (ImGui.treeNodeEx(title + "##val_drill_tree" + suffix, flags)) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, color);
            for (String roadId : affectedRoadIds) {
                Road road = network.getRoad(roadId);
                if (road == null) {
                    continue;
                }
                String label = RoadEdgeListHelper.formatRoadLabel(network, road);
                if (ImGui.selectable(label + "##val_drill_road_" + roadId + suffix)) {
                    ctx.requestEditRoad(roadId);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PlotI18n.tr("plugin.road.validation_drill_down_road"));
                }
            }
            ImGui.popStyleColor();
            ImGui.treePop();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.validation_drill_down_expand"));
        }
    }

    public static boolean executeAction(
            RoadValidationAction action,
            RoadUiContext ctx,
            RoadNetwork network,
            Road road) {
        if (action == null || ctx == null) {
            return false;
        }
        return switch (action) {
            case RECONCILE_INTERSECTIONS -> {
                RoadTopologyWorkflow.reconcileIntersections(ctx, true);
                yield true;
            }
            case SYNC_SEGMENT_ORDER -> {
                if (network == null || road == null) {
                    yield false;
                }
                ctx.networkManager().pushHistory();
                boolean synced = RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, road);
                if (synced) {
                    ctx.status().success(PlotI18n.tr("plugin.road.sync_segment_order_success"));
                }
                yield synced;
            }
            case SNAP_TO_JUNCTION, MATERIALIZE_ALIGNMENT -> {
                if (road == null || network == null) {
                    yield false;
                }
                ctx.networkManager().pushHistory();
                CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
                if (result.isSuccess()) {
                    ctx.status().success(PlotI18n.tr("plugin.road.horizontal_alignment_materialize_success"));
                    yield true;
                }
                ctx.status().warning(PlotI18n.tr("plugin.road.horizontal_alignment_materialize_failed"));
                yield false;
            }
            case SMOOTH_GRADE -> {
                var config = ctx.networkManager().getConfig();
                var net = network != null ? network : ctx.networkManager().getNetwork();
                ctx.networkManager().pushHistory();
                if (road != null) {
                    if (VerticalAlignmentGradeSmoother.smoothRoad(net, road, config)) {
                        ctx.onGenerationConfigChanged();
                        float limit = road.getEffectiveMaxSlope(config);
                        if (VerticalAlignmentGradeSmoother.exceedsGradeLimit(road.getVerticalAlignment(), limit)) {
                            ctx.status().warning(PlotI18n.tr("plugin.road.smooth_grade_partial"));
                        } else {
                            ctx.status().success(PlotI18n.tr("plugin.road.smooth_grade_success", 1));
                        }
                        yield true;
                    }
                } else {
                    int count = VerticalAlignmentGradeSmoother.smoothAllExceeding(net, config);
                    if (count > 0) {
                        ctx.onGenerationConfigChanged();
                        ctx.status().success(PlotI18n.tr("plugin.road.smooth_grade_success", count));
                        yield true;
                    }
                }
                ctx.status().warning(PlotI18n.tr("plugin.road.smooth_grade_failed"));
                yield false;
            }
            case MAKE_SHORT_ROADS_FLAT -> {
                RoadNetwork net = network != null ? network : ctx.networkManager().getNetwork();
                int invalidCount = com.plot.plugin.road.RoadNetworkEngineeringValidator
                    .countShortRoadsWithNonFlatActiveAlignment(net);
                if (invalidCount == 0) {
                    ctx.status().warning(PlotI18n.tr("plugin.road.short_roads_flat_failed"));
                    yield false;
                }
                int changed = 0;
                ctx.networkManager().pushHistory();
                for (Road candidate : net.getRoads().values()) {
                    var alignment = candidate.getVerticalAlignment();
                    if ((candidate.getVerticalMode() == RoadVerticalMode.FLAT
                            || candidate.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE)
                            && RoadStationing.isStationable(net, candidate)
                            && VerticalAlignmentGeometry.isEvaluable(alignment)
                            && !VerticalProfileDesignRules.slopeAllowed(
                                RoadStationing.canonicalLength(net, candidate))
                            && !VerticalProfileDesignRules.isFlat(alignment)) {
                        double length = RoadStationing.canonicalLength(net, candidate);
                        double elevation = alignment.getPvis().getFirst().getElevation();
                        candidate.setVerticalAlignment(
                            VerticalProfileDesignRules.flatAlignment(length, elevation));
                        candidate.setVerticalMode(RoadVerticalMode.FLAT);
                        changed++;
                    }
                }
                if (changed > 0) {
                    ctx.onGenerationConfigChanged();
                    ctx.status().success(PlotI18n.tr("plugin.road.short_roads_flat_success", changed));
                    yield true;
                }
                yield false;
            }
            case REPAIR_ROAD_TOPOLOGY -> RoadTopologyWorkflow.repairTopology(ctx, road);
        };
    }

    private static int severityColor(RoadNetworkValidationReport.Level severity) {
        return switch (severity) {
            case OK -> PluginUiColors.STATUS_OK;
            case WARNING -> PluginUiColors.WARNING;
            case ERROR -> PluginUiColors.ERROR;
        };
    }

    private static String severityPrefix(RoadNetworkValidationReport.Level severity) {
        return switch (severity) {
            case OK -> "\u2713 ";
            case WARNING -> "\u26a0 ";
            case ERROR -> "\u2717 ";
        };
    }
}
