package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.JunctionMarkingSetting;
import com.plot.plugin.road.model.RailwayJunctionMode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadModelUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;

/**
 * 交叉口几何属性（圆角/标线）：路径 Tab 与属性侧栏共用。
 * 平交圆角直接读写 {@link RoadCrossing}；标线/广场仍需要真实节点。
 */
public final class RoadJunctionPanel {
    private final RoadUiContext ctx;

    public RoadJunctionPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    /** 指定节点的路口几何（圆角/标线）；编辑 Tab 与 Crossing 选中时复用。 */
    public void renderForNode(RoadNode node, boolean compact) {
        if (node == null) {
            return;
        }
        renderControls(node, compact);
    }

    /** 当前选中路口：无共享节点的普通 RoadCrossing 也可编辑圆角。 */
    public void renderSelection(boolean compact) {
        RoadNetworkManager manager = ctx.networkManager();
        if (!RoadIntersectionPropertySupport.hasIntersectionSelection(manager)) {
            return;
        }
        RoadNetwork network = manager.getNetwork();
        RoadNode node = RoadIntersectionPropertySupport.resolveJunctionNode(network, manager);
        if (node != null) {
            renderControls(node, compact);
            return;
        }
        renderCrossingOnlyControls(compact);
    }

    private void renderCrossingOnlyControls(boolean compact) {
        RoadNetworkManager manager = ctx.networkManager();
        RoadCrossing crossing = manager.getSelectedCrossing();
        if (crossing == null || crossing.position() == null) {
            return;
        }
        boolean gradeSeparated = crossing.type() == com.plot.plugin.road.crossing.CrossingType.GRADE_SEPARATED
            || RoadIntersectionPropertySupport.isGradeSeparatedSelection(manager);
        Vec2d pos = crossing.position();
        ImGui.text(PlotI18n.tr(
            "plugin.road.junction_selected",
            PlotI18n.tr(gradeSeparated
                ? "plugin.road.crossing_type_grade_separated"
                : "plugin.road.crossing_type_at_grade"),
            pos.x, pos.y, 2));

        if (!gradeSeparated) {
            if (!compact) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.junction_corner_hint"));
            }
            renderFilletControls(compact);
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_clear_selection"))) {
            manager.clearCanvasSelection();
        }
    }

    private void renderControls(RoadNode node, boolean compact) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        boolean gradeSeparated = RoadIntersectionPropertySupport.isGradeSeparatedSelection(ctx.networkManager())
            || node.isGradeSeparated();
        Vec2d pos = node.getPosition();
        String typeLabel = gradeSeparated
            ? PlotI18n.tr("plugin.road.crossing_type_grade_separated")
            : RoadNetworkManager.junctionTypeLabel(
                ctx.networkManager().getNetworkBuilder().classify(node));
        ImGui.text(PlotI18n.tr("plugin.road.junction_selected",
            typeLabel, pos.x, pos.y, node.getDegree()));

        if (!gradeSeparated) {
            if (!compact) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.junction_corner_hint"));
            }
            renderFilletControls(compact);
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        List<RoadEdge> connected = connectedEdges(network, node);
        double basePlaza = RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS;
        double plazaBase = node.getEffectivePlazaRadius(basePlaza);
        double resolvedPlaza = RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            connected,
            edge -> RoadDimensionUtils.halfExtentFromCenter(
                RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()),
            plazaBase);
        float[] plazaRadius = {(float) (node.getPlazaRadius() != null
            ? node.getPlazaRadius()
            : basePlaza)};
        boolean plazaChanged = ImGui.sliderFloat(
            PlotI18n.tr(
                "plugin.road.junction_plaza_radius",
                RoadUiFormat.format(plazaBase),
                RoadUiFormat.format(resolvedPlaza)),
            plazaRadius,
            (float) RoadNode.MIN_PLAZA_RADIUS,
            (float) RoadNode.MAX_PLAZA_RADIUS,
            RoadUiFormat.SLIDER_METERS
        );
        if (ImGui.isItemActivated()) {
            ctx.networkManager().pushHistory();
        }
        if (plazaChanged) {
            node.setPlazaRadius((double) plazaRadius[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_plaza_radius"));
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_apply_default_plaza"))) {
            ctx.networkManager().pushHistory();
            node.setPlazaRadius(null);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_fit_plaza"))) {
            ctx.networkManager().pushHistory();
            double fitted = RoadJunctionGeometry.fitPlazaRadiusToApproaches(
                connected,
                edge -> RoadDimensionUtils.halfExtentFromCenter(
                    RoadModelUtils.resolveCrossSection(network, edge, config).totalHardSurfaceWidthBlocks()));
            node.setPlazaRadius(fitted);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_fit_plaza"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_clear_selection"))) {
            ctx.networkManager().clearNodeSelection();
        }

        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.road.junction_markings_auto_status"));
        if (!compact) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.junction_markings_auto_detail"));
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.junction_markings_advanced"))) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.junction_markings_hint"));
            renderMarkingSetting("stop_lines", PlotI18n.tr("plugin.road.junction_stop_lines"), node.getStopLines(),
                node::setStopLines);
            renderMarkingSetting("continued_markings", PlotI18n.tr("plugin.road.junction_continued_markings"),
                node.getContinuedMarkings(), node::setContinuedMarkings);
            renderMarkingSetting("crosswalks", PlotI18n.tr("plugin.road.junction_crosswalks"), node.getCrosswalks(),
                node::setCrosswalks);
            renderMarkingSetting("turn_arrows", PlotI18n.tr("plugin.road.junction_turn_arrows"), node.getTurnArrows(),
                node::setTurnArrows);

            if (hasRailwayApproach(network, node, config)) {
                renderRailwayJunctionMode(node);
            }

            if (ImGui.button(PlotI18n.tr("plugin.road.junction_reset_markings"))) {
                ctx.networkManager().pushHistory();
                node.setStopLines(JunctionMarkingSetting.AUTO);
                node.setContinuedMarkings(JunctionMarkingSetting.AUTO);
                node.setCrosswalks(JunctionMarkingSetting.AUTO);
                node.setTurnArrows(JunctionMarkingSetting.AUTO);
                node.setRailwayJunctionMode(RailwayJunctionMode.DECORATIVE);
            }
        }
    }

    /** 仅平交显示；立交路口不渲染任何圆角相关控件。 */
    private void renderFilletControls(boolean compact) {
        RoadNetworkManager manager = ctx.networkManager();
        if (RoadIntersectionPropertySupport.isGradeSeparatedSelection(manager)) {
            return;
        }
        RoadSystemConfig config = manager.getConfig();
        Double stored = RoadIntersectionPropertySupport.resolveStoredCornerRadius(manager);
        imgui.type.ImBoolean filletsEnabled = new imgui.type.ImBoolean(
            stored == null || stored > 1e-6);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.junction_enable_fillets"), filletsEnabled)) {
            manager.pushHistory();
            RoadIntersectionPropertySupport.applyCornerRadius(
                manager, filletsEnabled.get() ? null : 0.0);
            ctx.requestJunctionFilletPreviewRefresh();
            if (ctx.previewManager() != null) {
                ctx.previewManager().invalidatePreview();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_corner_radius"));
        }

        float[] cornerRadius = {(float) (stored != null ? stored : config.getDefaultCornerRadius())};
        ImGui.beginDisabled(!filletsEnabled.get());
        // 固定 ##id：标签不得含变化数值，否则 ImGui 每帧换控件 ID，拖动会卡顿/跳动。
        boolean radiusChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.junction_corner_radius") + "##junction_corner_radius",
            cornerRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            RoadUiFormat.SLIDER_METERS
        );
        if (ImGui.isItemActivated()) {
            manager.pushUndoSnapshot();
        }
        if (radiusChanged) {
            RoadIntersectionPropertySupport.applyCornerRadius(manager, (double) cornerRadius[0]);
            ctx.requestJunctionFilletPreviewRefresh();
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            manager.commitNetworkChange();
            if (ctx.previewManager() != null) {
                ctx.previewManager().invalidatePreview();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_corner_radius"));
        }
        EffectiveRadiusDisplay effective = resolveDisplayedEffectiveRadii(manager, config);
        if (filletsEnabled.get() && cornerRadius[0] > 1e-3 && effective.max() <= 1e-6) {
            ImGui.textDisabled(PlotI18n.tr("plugin.road.junction_corner_radius_unavailable"));
        } else if (effective.max() > effective.min() + 1e-3) {
            ImGui.textDisabled(PlotI18n.tr(
                "plugin.road.junction_corner_radius_effective_range",
                RoadUiFormat.format(effective.min()),
                RoadUiFormat.format(effective.max())));
        } else {
            ImGui.textDisabled(PlotI18n.tr(
                "plugin.road.junction_corner_radius_effective",
                RoadUiFormat.format(effective.min())));
        }
        if (!compact) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.junction_fillet_auto_clamp"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.junction_apply_default_radius"))) {
            manager.pushHistory();
            RoadIntersectionPropertySupport.applyCornerRadius(manager, null);
            ctx.requestJunctionFilletPreviewRefresh();
            if (ctx.previewManager() != null) {
                ctx.previewManager().invalidatePreview();
            }
        }
        ImGui.endDisabled();

        ImGui.checkbox(
            PlotI18n.tr("plugin.road.junction_show_fillet_preview"),
            ctx.showJunctionFilletPreview());
        if (ImGui.isItemEdited()) {
            ctx.requestJunctionFilletPreviewRefresh();
        }
    }

    private EffectiveRadiusDisplay resolveDisplayedEffectiveRadii(
            RoadNetworkManager manager,
            RoadSystemConfig config) {
        if (RoadIntersectionPropertySupport.isGradeSeparatedSelection(manager)) {
            return new EffectiveRadiusDisplay(0.0, 0.0);
        }
        var geometry = com.plot.plugin.road.JunctionPreviewGeometry.resolve(
            manager.getNetwork(),
            config,
            manager.getSelectedNodeId(),
            manager.getSelectedCrossingId());
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        for (var fillet : geometry.fillets()) {
            if (fillet == null || fillet.radius() <= 1e-9) {
                continue;
            }
            min = Math.min(min, fillet.radius());
            max = Math.max(max, fillet.radius());
        }
        if (Double.isFinite(min) && max > 1e-6) {
            return new EffectiveRadiusDisplay(min, max);
        }
        if (geometry.effectiveCornerRadius() > 1e-6) {
            double value = geometry.effectiveCornerRadius();
            return new EffectiveRadiusDisplay(value, value);
        }
        // 无有效圆弧时显示 0，不回退到请求值（避免 R=12 全部失败仍显示 12）
        return new EffectiveRadiusDisplay(0.0, 0.0);
    }

    private record EffectiveRadiusDisplay(double min, double max) {
    }

    private static boolean hasRailwayApproach(
            RoadNetwork network,
            RoadNode node,
            RoadSystemConfig config) {
        if (network == null || node == null) {
            return false;
        }
        for (RoadEdge edge : connectedEdges(network, node)) {
            if (RoadModelUtils.resolveCrossSection(network, edge, config).centerLineStyle
                    == CenterLineStyle.RAIL) {
                return true;
            }
        }
        return false;
    }

    private void renderRailwayJunctionMode(RoadNode node) {
        String[] labels = {
            PlotI18n.tr("plugin.road.junction_railway_mode.decorative"),
            PlotI18n.tr("plugin.road.junction_railway_mode.passable")
        };
        ImInt index = new ImInt(
            node.getRailwayJunctionMode() == RailwayJunctionMode.PASSABLE ? 1 : 0);
        if (ImGui.combo(
                PlotI18n.tr("plugin.road.junction_railway_mode") + "##railway_junction_mode",
                index,
                labels)) {
            ctx.networkManager().pushHistory();
            node.setRailwayJunctionMode(
                index.get() == 1 ? RailwayJunctionMode.PASSABLE : RailwayJunctionMode.DECORATIVE);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_railway_mode"));
        }
    }

    private static List<RoadEdge> connectedEdges(RoadNetwork network, RoadNode node) {
        List<RoadEdge> edges = new ArrayList<>();
        if (network == null || node == null) {
            return edges;
        }
        for (String edgeId : node.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                edges.add(edge);
            }
        }
        return edges;
    }

    private void renderMarkingSetting(
            String id,
            String label,
            JunctionMarkingSetting current,
            java.util.function.Consumer<JunctionMarkingSetting> onChange) {
        String[] labels = {
            PlotI18n.tr("plugin.road.junction_marking.auto"),
            PlotI18n.tr("plugin.road.junction_marking.on"),
            PlotI18n.tr("plugin.road.junction_marking.off")
        };
        ImInt index = new ImInt(switch (current) {
            case ON -> 1;
            case OFF -> 2;
            default -> 0;
        });
        if (ImGui.combo(label + "##junction_marking_" + id, index, labels)) {
            ctx.networkManager().pushHistory();
            onChange.accept(switch (index.get()) {
                case 1 -> JunctionMarkingSetting.ON;
                case 2 -> JunctionMarkingSetting.OFF;
                default -> JunctionMarkingSetting.AUTO;
            });
        }
    }
}
