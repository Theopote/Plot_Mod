package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.JunctionMarkingSetting;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * 交叉口属性编辑（概览/编辑 Tab 与 PropertyPanel 共用）。
 */
public final class RoadJunctionPanel {
    private final RoadUiContext ctx;

    public RoadJunctionPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderSummary() {
        RoadNode node = ctx.networkManager().getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.spacing();
        renderControls(node, false);
    }

    public void renderEditor() {
        RoadNode node = ctx.networkManager().getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.separator();
        renderControls(node, false);
    }

    public void renderPropertySection() {
        RoadNode node = ctx.networkManager().getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        renderControls(node, true);
    }

    private void renderControls(RoadNode node, boolean compact) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNetworkBuilder.JunctionType type = ctx.networkManager().getNetworkBuilder().classify(node);
        Vec2d pos = node.getPosition();
        ImGui.text(PlotI18n.tr("plugin.road.junction_selected",
            RoadNetworkManager.junctionTypeLabel(type), pos.x, pos.y, node.getDegree()));

        if (!compact) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.junction_corner_hint"));
        }

        double effectiveRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        float[] cornerRadius = {(float) (node.getCornerRadius() != null
            ? node.getCornerRadius()
            : config.getDefaultCornerRadius())};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.junction_corner_radius", effectiveRadius),
            cornerRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            "%.1f m"
        )) {
            ctx.networkManager().pushHistory();
            node.setCornerRadius((double) cornerRadius[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_corner_radius"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.junction_apply_default_radius"))) {
            ctx.networkManager().pushHistory();
            node.setCornerRadius(null);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_clear_selection"))) {
            ctx.networkManager().setSelectedNodeId("");
        }

        ImGui.spacing();
        if (!compact) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.junction_markings_hint"));
        }
        renderMarkingSetting("stop_lines", PlotI18n.tr("plugin.road.junction_stop_lines"), node.getStopLines(),
            value -> node.setStopLines(value));
        renderMarkingSetting("continued_markings", PlotI18n.tr("plugin.road.junction_continued_markings"),
            node.getContinuedMarkings(), value -> node.setContinuedMarkings(value));
        renderMarkingSetting("crosswalks", PlotI18n.tr("plugin.road.junction_crosswalks"), node.getCrosswalks(),
            value -> node.setCrosswalks(value));
        renderMarkingSetting("turn_arrows", PlotI18n.tr("plugin.road.junction_turn_arrows"), node.getTurnArrows(),
            value -> node.setTurnArrows(value));

        if (ImGui.button(PlotI18n.tr("plugin.road.junction_reset_markings"))) {
            ctx.networkManager().pushHistory();
            node.setStopLines(JunctionMarkingSetting.AUTO);
            node.setContinuedMarkings(JunctionMarkingSetting.AUTO);
            node.setCrosswalks(JunctionMarkingSetting.AUTO);
            node.setTurnArrows(JunctionMarkingSetting.AUTO);
        }
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
