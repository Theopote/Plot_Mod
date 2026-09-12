package com.plot.plugin.powerline.ui;

import com.plot.core.model.Shape;
import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.path.PowerLineSourceDescriptor;
import com.plot.plugin.powerline.path.PowerLineSourceSync;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 电力线路路径认领（Route Tab）。 */
public final class PowerLineAdoptPanel {
    private final PowerLineUiContext ctx;

    public PowerLineAdoptPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render(PowerLineFootprint line) {
        ctx.updateSelectedPaths();
        PowerLinePathSelectionAnalysis selection = ctx.pathSelection();

        if (line != null && PowerLineSourceSync.hasLinkedSource(line)) {
            renderLinkedSource(line);
            renderSelectionErrors(selection);
            if (ctx.isPathRelinkActive(line)) {
                renderPathRelinkActions(line, selection);
            }
            return;
        }

        if (ctx.project().getLineCount() == 0) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.no_lines"));
            renderSelectionErrors(selection);
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.create_from_canvas"), 0, 0)) {
                if (selection.canAdopt()) {
                    ctx.adoptSelectedPaths();
                } else {
                    ctx.activatePathPickTool();
                }
            }
            return;
        }

        renderSelectionErrors(selection);
        if (selection.canAdopt()) {
            ImGui.spacing();
            String label = selection.adoptable().size() > 1
                ? PlotI18n.tr("plugin.powerline.adopt_batch", selection.adoptable().size())
                : PlotI18n.tr("plugin.powerline.adopt");
            if (ImGui.button(label, 0, 0)) {
                ctx.adoptSelectedPaths();
            }
        } else {
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.pick_path"), 0, 0)) {
                ctx.activatePathPickTool();
            }
        }
    }

    private void renderLinkedSource(PowerLineFootprint line) {
        String label = resolveLinkedSourceLabel(line);
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.path.linked", label));
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.change"), 0, 0)) {
            ctx.beginPathRelink(line);
        }
    }

    private void renderPathRelinkActions(
            PowerLineFootprint line,
            PowerLinePathSelectionAnalysis selection) {
        if (!selection.canAdopt()) {
            return;
        }
        if (selection.adoptable().size() != 1) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.path.relink_select_one"));
            return;
        }
        Shape candidate = selection.adoptable().getFirst();
        if (candidate.getId().equals(line.getSourceShapeId())) {
            return;
        }
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.apply_selection"), 0, 0)) {
            ctx.applyPathRelink(line);
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
        PowerLineSourceDescriptor descriptor = line.getSourceDescriptor();
        if (descriptor != null) {
            return descriptorKindLabel(descriptor.kind());
        }
        return PlotI18n.tr("plugin.powerline.path.unknown_source");
    }

    private static String descriptorKindLabel(PowerLineSourceDescriptor.Kind kind) {
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
