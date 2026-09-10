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
    static final float MIN_PANE_HEIGHT = 192f;
    static final float MAX_PANE_HEIGHT = 660f;
    private static final float HEIGHT_PER_BLOCK = 5.2f;
    private static final float PANE_CHROME_HEIGHT = 24f;
    private static final float PANE_GAP = 8f;
    private static final float PANE_PADDING = 2f;
    private static final float PANE_LABEL_GAP = 4f;
    private static final float MIN_COLUMN_HEIGHT = 32f;
    private static final int PREVIEW_CANVAS_FLAGS =
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

    /**
     * 设计器左栏：预览框占满剩余高度，正视/侧视均分框内空间，不出现内部滚动条。
     */
    public static void renderVerticalStack(PoleDesign design, float width, float columnHeight) {
        if (width < 40f || design == null || columnHeight < MIN_COLUMN_HEIGHT) {
            return;
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.preview"));
        float titleHeight = ImGui.getTextLineHeightWithSpacing();
        float footerHeight = ImGui.getTextLineHeightWithSpacing();
        float viewportHeight = Math.max(0f, columnHeight - titleHeight - footerHeight);
        if (viewportHeight < 1f) {
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.total_height", design.totalHeight()));
            return;
        }

        ImGui.beginChild("##pole_design_preview_canvas", width, viewportHeight, true, PREVIEW_CANVAS_FLAGS);
        float contentWidth = ImGui.getContentRegionAvail().x;
        float contentHeight = ImGui.getContentRegionAvail().y;
        float eachPaneHeight = Math.max(28f, (contentHeight - PANE_GAP) * 0.5f);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design);

        float frontY0 = origin.y;
        float frontY1 = frontY0 + eachPaneHeight;
        float sideY0 = frontY1 + PANE_GAP;
        float sideY1 = sideY0 + eachPaneHeight;

        renderPane(
            drawList,
            design,
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            PlotI18n.tr("plugin.powerline.design.preview_front"),
            origin.x + PANE_PADDING,
            frontY0 + PANE_PADDING,
            origin.x + contentWidth - PANE_PADDING,
            frontY1 - PANE_PADDING);
        renderPane(
            drawList,
            design,
            model,
            PoleVoxelElevationRenderer.ElevationView.SIDE,
            PlotI18n.tr("plugin.powerline.design.preview_side"),
            origin.x + PANE_PADDING,
            sideY0 + PANE_PADDING,
            origin.x + contentWidth - PANE_PADDING,
            sideY1 - PANE_PADDING);

        ImGui.dummy(contentWidth, contentHeight);
        ImGui.endChild();

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.total_height", design.totalHeight()));
    }

    /** 按塔高估算理想单视图高度（供测试与其他 UI 参考，设计器内始终均分框高）。 */
    public static float resolveDesiredPaneHeight(PoleDesign design, PoleVoxelPreviewModel model) {
        if (design == null) {
            return MIN_PANE_HEIGHT;
        }
        float byBlocks = model != null && !model.isEmpty()
            ? model.heightY() * HEIGHT_PER_BLOCK + PANE_CHROME_HEIGHT
            : design.totalHeight() * HEIGHT_PER_BLOCK + 32f;
        return Math.min(MAX_PANE_HEIGHT, Math.max(MIN_PANE_HEIGHT, byBlocks));
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
