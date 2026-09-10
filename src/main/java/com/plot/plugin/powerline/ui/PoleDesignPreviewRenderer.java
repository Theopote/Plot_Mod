package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.preview.PoleVoxelElevationRenderer;
import com.plot.plugin.powerline.preview.PoleVoxelPreviewModel;
import com.plot.plugin.powerline.preview.PoleVoxelizer;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

/** 杆塔设计器预览：体素立面 + 设计辅助 overlay。 */
public final class PoleDesignPreviewRenderer {
    private static final float PREVIEW_HEIGHT = 200f;
    private static final float PANE_GAP = 8f;
    private static final float PANE_PADDING = 2f;
    private static final float PANE_LABEL_GAP = 4f;
    private static final int PREVIEW_CHILD_FLAGS =
        ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_ATTACHMENT = 0xE6FFD54F;
    private static final int COLOR_ATTACHMENT_RING = 0xFFFFD54F;
    private static final int COLOR_STATION_GUIDE = 0x9978909C;
    private static final int COLOR_ARM_GUIDE = 0x99546E7A;
    private static final int COLOR_LABEL = 0xFFE0E0E0;

    private PoleDesignPreviewRenderer() {
    }

    public static void render(PoleDesign design) {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.preview"));
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f || design == null) {
            return;
        }

        ImGui.beginChild("##pole_design_preview_canvas", 0, PREVIEW_HEIGHT, true, PREVIEW_CHILD_FLAGS);
        float contentWidth = ImGui.getContentRegionAvail().x;
        float contentHeight = ImGui.getContentRegionAvail().y;
        float paneWidth = Math.max(40f, (contentWidth - PANE_GAP) * 0.5f);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design);

        float y0 = origin.y;
        float y1 = y0 + contentHeight;
        float frontX0 = origin.x;
        float frontX1 = frontX0 + paneWidth;
        float sideX0 = frontX1 + PANE_GAP;
        float sideX1 = sideX0 + paneWidth;

        renderPane(
            drawList,
            design,
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            PlotI18n.tr("plugin.powerline.design.preview_front"),
            frontX0 + PANE_PADDING,
            y0 + PANE_PADDING,
            frontX1 - PANE_PADDING,
            y1 - PANE_PADDING);
        renderPane(
            drawList,
            design,
            model,
            PoleVoxelElevationRenderer.ElevationView.SIDE,
            PlotI18n.tr("plugin.powerline.design.preview_side"),
            sideX0 + PANE_PADDING,
            y0 + PANE_PADDING,
            sideX1 - PANE_PADDING,
            y1 - PANE_PADDING);

        ImGui.dummy(contentWidth, contentHeight);
        ImGui.endChild();
    }

    private static void renderPane(
            ImDrawList drawList,
            PoleDesign design,
            PoleVoxelPreviewModel model,
            PoleVoxelElevationRenderer.ElevationView view,
            String label,
            float x0,
            float y0,
            float x1,
            float y1) {
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);
        drawList.addText(x0 + 4f, y0 + 3f, COLOR_LABEL, label);

        float innerY0 = y0 + ImGui.getFontSize() + PANE_LABEL_GAP;
        if (model != null && !model.isEmpty()) {
            PoleVoxelElevationRenderer.draw(drawList, model, view, x0, innerY0, x1, y1);
            PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
                model, view, x0, innerY0, x1, y1);
            if (layout != null) {
                renderDesignerOverlay(drawList, design, model, view, layout, x0, x1);
            }
        }
    }

    private static void renderDesignerOverlay(
            ImDrawList drawList,
            PoleDesign design,
            PoleVoxelPreviewModel model,
            PoleVoxelElevationRenderer.ElevationView view,
            PoleVoxelElevationRenderer.ElevationLayout layout,
            float x0,
            float x1) {
        if (design.hasTowerStructure()) {
            renderStationGuides(drawList, design.getTowerStructure(), model, view, layout, x0, x1);
            renderArmGuides(drawList, design.getTowerStructure(), model, view, layout);
        }
        renderAttachmentMarkers(drawList, design, model, view, layout);
    }

    private static void renderStationGuides(
            ImDrawList drawList,
            TowerStructureDesign structure,
            PoleVoxelPreviewModel model,
            PoleVoxelElevationRenderer.ElevationView view,
            PoleVoxelElevationRenderer.ElevationLayout layout,
            float x0,
            float x1) {
        for (TowerStation station : structure.sortedStations()) {
            float y = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, station.getHeight());
            drawList.addLine(x0 + 2f, y, x1 - 2f, y, COLOR_STATION_GUIDE, 1f);
        }
    }

    private static void renderArmGuides(
            ImDrawList drawList,
            TowerStructureDesign structure,
            PoleVoxelPreviewModel model,
            PoleVoxelElevationRenderer.ElevationView view,
            PoleVoxelElevationRenderer.ElevationLayout layout) {
        for (TowerArm arm : structure.getArms()) {
            float y = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, arm.getBaseHeight());
            double horizontalStart = view == PoleVoxelElevationRenderer.ElevationView.FRONT
                ? -arm.getLateralReach()
                : -arm.getLongitudinalHalfWidth();
            double horizontalEnd = view == PoleVoxelElevationRenderer.ElevationView.FRONT
                ? arm.getLateralReach()
                : arm.getLongitudinalHalfWidth();
            float xStart = PoleVoxelElevationRenderer.mapHorizontalToScreen(layout, view, model, horizontalStart);
            float xEnd = PoleVoxelElevationRenderer.mapHorizontalToScreen(layout, view, model, horizontalEnd);
            drawList.addLine(xStart, y, xEnd, y, COLOR_ARM_GUIDE, 1.5f);
            String label = arm.getId() != null ? arm.getId() : "arm";
            drawList.addText(xEnd + 3f, y - ImGui.getFontSize() * 0.5f, COLOR_LABEL, label);
        }
    }

    private static void renderAttachmentMarkers(
            ImDrawList drawList,
            PoleDesign design,
            PoleVoxelPreviewModel model,
            PoleVoxelElevationRenderer.ElevationView view,
            PoleVoxelElevationRenderer.ElevationLayout layout) {
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            double horizontal = view == PoleVoxelElevationRenderer.ElevationView.FRONT
                ? attachment.getLateralOffset()
                : attachment.getLongitudinalOffset();
            float x = PoleVoxelElevationRenderer.mapHorizontalToScreen(layout, view, model, horizontal);
            float y = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, attachment.getVerticalOffset());
            drawList.addCircleFilled(x, y, 4f, COLOR_ATTACHMENT);
            drawList.addCircle(x, y, 4.5f, COLOR_ATTACHMENT_RING, 12, 1.2f);
            String name = attachment.getName();
            if (name != null && !name.isBlank()) {
                drawList.addText(x + 6f, y - ImGui.getFontSize() * 0.5f, COLOR_LABEL, name);
            }
        }
    }
}
