package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadNetworkBuilder;
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
 * 标线默认全自动生成，高级覆盖折叠收起，减少日常操作负担。
 */
public final class RoadJunctionPanel {
    private final RoadUiContext ctx;

    public RoadJunctionPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderSummary() {
        renderForNode(resolveSelectedJunctionNode(), false);
    }

    public void renderEditor() {
        RoadNode node = resolveSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.separator();
        renderForNode(node, false);
    }

    public void renderPropertySection() {
        renderForNode(resolveSelectedJunctionNode(), true);
    }

    private RoadNode resolveSelectedJunctionNode() {
        return RoadIntersectionPropertySupport.resolveJunctionNode(
            ctx.networkManager().getNetwork(),
            ctx.networkManager());
    }

    /** 指定节点的路口几何（圆角/标线）；编辑 Tab 与 Crossing 选中时复用。 */
    public void renderForNode(RoadNode node, boolean compact) {
        if (node == null) {
            return;
        }
        renderControls(node, compact);
    }

    private void renderControls(RoadNode node, boolean compact) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNetworkBuilder.JunctionType type = ctx.networkManager().getNetworkBuilder().classify(node);
        Vec2d pos = node.getPosition();
        ImGui.text(PlotI18n.tr("plugin.road.junction_selected",
            RoadNetworkManager.junctionTypeLabel(type), pos.x, pos.y, node.getDegree()));

        if (!compact) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.junction_corner_hint"));
        }

        boolean gradeSeparatedSelection = RoadIntersectionPropertySupport.isGradeSeparatedSelection(ctx.networkManager());
        imgui.type.ImBoolean filletsEnabled = new imgui.type.ImBoolean(
            !gradeSeparatedSelection
                && (node.getCornerRadius() == null || node.getCornerRadius() > 1e-6));
        ImGui.beginDisabled(gradeSeparatedSelection);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.junction_enable_fillets"), filletsEnabled)) {
            ctx.networkManager().pushHistory();
            applyCornerRadius(node, filletsEnabled.get() ? null : 0.0);
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(
                gradeSeparatedSelection
                    ? "hint.plot.road.junction_fillets_grade_sep"
                    : "hint.plot.road.junction_corner_radius"));
        }

        double effectiveRadius = gradeSeparatedSelection
            ? 0.0
            : node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        float[] cornerRadius = {(float) (node.getCornerRadius() != null
            ? node.getCornerRadius()
            : config.getDefaultCornerRadius())};
        ImGui.beginDisabled(gradeSeparatedSelection || !filletsEnabled.get());
        boolean radiusChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.junction_corner_radius", RoadUiFormat.format(effectiveRadius)),
            cornerRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            RoadUiFormat.SLIDER_METERS
        );
        if (ImGui.isItemActivated()) {
            ctx.networkManager().pushHistory();
        }
        if (radiusChanged) {
            applyCornerRadius(node, (double) cornerRadius[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_corner_radius"));
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.junction_fillet_auto_clamp"));

        if (ImGui.button(PlotI18n.tr("plugin.road.junction_apply_default_radius"))) {
            ctx.networkManager().pushHistory();
            applyCornerRadius(node, null);
        }
        ImGui.endDisabled();

        ImGui.checkbox(
            PlotI18n.tr("plugin.road.junction_show_fillet_preview"),
            ctx.showJunctionFilletPreview());

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
        // 默认自动：只展示说明，不强迫用户点四个下拉框
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

    private void applyCornerRadius(RoadNode node, Double cornerRadius) {
        if (node != null) {
            node.setCornerRadius(cornerRadius);
        }
        var crossing = ctx.networkManager().getSelectedCrossing();
        if (crossing != null && crossing.type() == com.plot.plugin.road.crossing.CrossingType.AT_GRADE) {
            ctx.networkManager().getNetwork().setCrossingCornerRadius(crossing.id(), cornerRadius);
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
