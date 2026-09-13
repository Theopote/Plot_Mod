package com.plot.plugin.powerline.ui;

import com.plot.core.model.Shape;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.path.PowerLineSourceSync;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 电力线路路径认领（Route Tab）。 */
public final class PowerLineAdoptPanel {
    private enum PickingMode {
        ADOPT,
        RELINK
    }

    private final PowerLineUiContext ctx;

    public PowerLineAdoptPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render(PowerLineFootprint line) {
        PowerLinePathSelectionAnalysis selection = ctx.pathSelection();

        if (line != null && PowerLineSourceSync.hasLinkedSource(line)) {
            renderLinkedSource(line);
            if (ctx.isPathRelinkActive(line)) {
                if (ctx.pathPickSession().isActive()) {
                    renderPickingState(selection, PickingMode.RELINK);
                } else {
                    renderRelinkReadyState(line, selection);
                }
            }
            return;
        }

        if (ctx.pathPickSession().isActive()) {
            renderPickingState(selection, PickingMode.ADOPT);
            return;
        }
        if (selection.canAdopt()) {
            renderReadyState(selection);
            return;
        }
        renderIdleAdoptState(selection);
    }

    private void renderIdleAdoptState(PowerLinePathSelectionAnalysis selection) {
        renderSelectionErrors(selection);
        if (ImGui.button(PlotI18n.tr("plugin.powerline.pick_path"), 0, 0)) {
            ctx.activatePathPickTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.pick_path_hint"));
        }
        if (ctx.project().getLineCount() == 0) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.no_lines"));
        }
    }

    private void renderPickingState(PowerLinePathSelectionAnalysis selection, PickingMode mode) {
        int count = ctx.pathPickSession().getAccumulatedCount();
        String statusKey = mode == PickingMode.RELINK
            ? (count > 0
                ? "plugin.powerline.path.relink_picking_count"
                : "plugin.powerline.path.relink_picking_active")
            : (count > 0
                ? "plugin.powerline.path.picking_count"
                : "plugin.powerline.path.picking_active");
        if (count > 0) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr(statusKey, count));
        } else {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr(statusKey));
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.path.right_click_finish"));
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_pick"), 0, 0)) {
            ctx.cancelPathPick();
        }
        renderSelectionErrors(selection);
    }

    private void renderReadyState(PowerLinePathSelectionAnalysis selection) {
        renderSelectionErrors(selection);
        if (!selection.canAdopt()) {
            return;
        }
        ImGui.spacing();
        PowerLineUiWidgets.textColored(
            PluginUiColors.STATUS_INFO,
            PlotI18n.tr(
                "plugin.powerline.path.selection_summary",
                selection.adoptable().size()));
        ImGui.spacing();
        String adoptLabel = selection.adoptable().size() > 1
            ? PlotI18n.tr("plugin.powerline.adopt_batch", selection.adoptable().size())
            : PlotI18n.tr("plugin.powerline.adopt");
        if (ImGui.button(adoptLabel, 0, 0)) {
            ctx.adoptSelectedPaths();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.repick"), 0, 0)) {
            ctx.activatePathPickTool();
        }
    }

    private void renderRelinkReadyState(
            PowerLineFootprint line,
            PowerLinePathSelectionAnalysis selection) {
        renderSelectionErrors(selection);
        if (!selection.canAdopt()) {
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.repick"), 0, 0)) {
                ctx.activatePathPickTool();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_pick"), 0, 0)) {
                ctx.cancelPathPick();
            }
            return;
        }
        if (selection.adoptable().size() != 1) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.path.relink_select_one"));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.repick"), 0, 0)) {
                ctx.activatePathPickTool();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_pick"), 0, 0)) {
                ctx.cancelPathPick();
            }
            return;
        }
        Shape candidate = selection.adoptable().getFirst();
        if (candidate.getId().equals(line.getSourceShapeId())) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.relink_same_source"));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.repick"), 0, 0)) {
                ctx.activatePathPickTool();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_pick"), 0, 0)) {
                ctx.cancelPathPick();
            }
            return;
        }
        ImGui.spacing();
        PowerLineUiWidgets.textColored(
            PluginUiColors.STATUS_INFO,
            PlotI18n.tr("plugin.powerline.path.relink_ready"));
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.apply_selection"), 0, 0)) {
            ctx.applyPathRelink(line);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.repick"), 0, 0)) {
            ctx.activatePathPickTool();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_pick"), 0, 0)) {
            ctx.cancelPathPick();
        }
    }

    private void renderLinkedSource(PowerLineFootprint line) {
        String label = resolveLinkedSourceLabel(line);
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.path.linked", label));
        if (ctx.isPathRelinkActive(line)) {
            return;
        }
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.change"), 0, 0)) {
            ctx.beginPathRelink(line);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.detach"), 0, 0)) {
            ctx.detachSourceAndKeepLayout(line);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.path.detach_keep_snapshot_hint"));
        }
    }

    private void renderSelectionErrors(PowerLinePathSelectionAnalysis selection) {
        if (!selection.rejectedCurves().isEmpty()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.path.invalid_selection"));
            return;
        }
        if (!selection.unsupported().isEmpty() && !selection.canAdopt()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.path.invalid_selection"));
        }
    }

    private String resolveLinkedSourceLabel(PowerLineFootprint line) {
        List<Shape> canvasShapes = ctx.host().appState().getShapes();
        Shape liveShape = PowerLineSourceSync.findShape(canvasShapes, line.getSourceShapeId());
        if (liveShape != null) {
            return PlotI18n.shapeTypeLabel(liveShape.getClass().getSimpleName());
        }
        var descriptor = line.getSourceDescriptor();
        if (descriptor != null) {
            return descriptorKindLabel(descriptor.kind());
        }
        return PlotI18n.tr("plugin.powerline.path.unknown_source");
    }

    private static String descriptorKindLabel(
            com.plot.plugin.powerline.path.PowerLineSourceDescriptor.Kind kind) {
        if (kind == null) {
            return PlotI18n.tr("plugin.powerline.path.unknown_source");
        }
        return switch (kind) {
            case POLYLINE -> PlotI18n.shapeTypeLabel("PolylineShape");
            case BEZIER -> PlotI18n.shapeTypeLabel("BezierCurveShape");
            case ELLIPSE -> PlotI18n.shapeTypeLabel("EllipseShape");
            case ARC -> PlotI18n.shapeTypeLabel("ArcShape");
            case ELLIPTICAL_ARC -> PlotI18n.shapeTypeLabel("EllipticalArcShape");
        };
    }
}
