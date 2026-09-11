package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.style.PreviewOverlay;
import com.plot.plugin.powerline.style.PreviewRepresentation;
import imgui.ImDrawList;

/**
 * 风格卡片缩略图装饰叠加层：挂点、绝缘子、短导线暗示、特殊标记。
 * <p>
 * Gallery 卡片专用；tooltip / 大预览仍走体素，不调用此类。
 */
public final class PowerLinePreviewOverlayRenderer {
    private static final float INSULATOR_MIN_PX = 2.5f;
    private static final float INSULATOR_MAX_PX = 5.0f;
    private static final float WIRE_HINT_MAX_PX = 8.0f;
    private static final float ATTACHMENT_DOT_RADIUS = 1.8f;

    private static final int COLOR_INSULATOR = 0xFFB0BEC5;
    private static final int COLOR_WIRE = 0xFF90A4AE;
    private static final int COLOR_TOP_WIRE = 0xFFECEFF1;
    private static final int COLOR_ROTOR = 0xFF78909C;
    private static final int COLOR_MARKER = 0xFF64B5F6;

    private PowerLinePreviewOverlayRenderer() {
    }

    public static void draw(
            ImDrawList drawList,
            PoleDesign design,
            PreviewRepresentation representation,
            PreviewOverlay overlay,
            boolean adaptiveHeightMarker,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (drawList == null || design == null) {
            return;
        }
        if (overlay != null && overlay != PreviewOverlay.NONE) {
            switch (overlay) {
                case ATTACHMENTS -> drawAttachments(drawList, design, representation, x0, y0, x1, y1);
                case DECORATIVE_CONDUCTORS -> drawDecorativeConductors(drawList, design, x0, y0, x1, y1);
                case WIND_ROTOR -> drawWindRotorOverlay(drawList, x0, y0, x1, y1);
                case ADAPTIVE_MARKER -> drawAdaptiveHeightMarker(drawList, x0, y0, x1, y1);
                default -> { }
            }
        }
        if (adaptiveHeightMarker && overlay != PreviewOverlay.ADAPTIVE_MARKER) {
            drawAdaptiveHeightMarker(drawList, x0, y0, x1, y1);
        }
    }

    private static void drawAttachments(
            ImDrawList drawList,
            PoleDesign design,
            PreviewRepresentation representation,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (representation == PreviewRepresentation.STRUCTURAL_FRONT && design.hasTowerStructure()) {
            TowerStructuralElevationRenderer.StructuralLayout layout =
                TowerStructuralElevationRenderer.computeLayout(design, x0, y0, x1, y1);
            if (layout == null) {
                return;
            }
            for (ConductorAttachment attachment : design.getAttachments()) {
                drawStructuralAttachment(drawList, attachment, layout);
            }
            return;
        }
        drawDecorativeConductors(drawList, design, x0, y0, x1, y1);
    }

    private static void drawStructuralAttachment(
            ImDrawList drawList,
            ConductorAttachment attachment,
            TowerStructuralElevationRenderer.StructuralLayout layout) {
        if (attachment == null || !attachment.isEnabled()) {
            return;
        }
        float x = layout.mapX(attachment.getLateralOffset());
        float yHang = layout.mapY(attachment.getVerticalOffset());
        AttachmentRole role = attachment.getRole();
        if (role == AttachmentRole.TOP_WIRE) {
            drawList.addCircleFilled(x, yHang, ATTACHMENT_DOT_RADIUS, COLOR_TOP_WIRE);
            return;
        }
        float insulatorLen = (float) Math.min(
            INSULATOR_MAX_PX,
            Math.max(INSULATOR_MIN_PX, attachment.getInsulatorLength() * layout.scale()));
        float yInsulatorEnd = yHang + insulatorLen;
        drawList.addLine(x, yHang, x, yInsulatorEnd, COLOR_INSULATOR, 1.2f);
        drawList.addCircleFilled(x, yInsulatorEnd, ATTACHMENT_DOT_RADIUS, COLOR_WIRE);
        float hintHalf = Math.min(WIRE_HINT_MAX_PX * 0.5f, 4.0f);
        drawList.addLine(x - hintHalf, yInsulatorEnd, x + hintHalf, yInsulatorEnd, COLOR_WIRE, 1.0f);
    }

    private static void drawDecorativeConductors(
            ImDrawList drawList,
            PoleDesign design,
            float x0,
            float y0,
            float x1,
            float y1) {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design);
        if (model == null || model.isEmpty()) {
            return;
        }
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            x0,
            y0,
            x1,
            y1);
        if (layout == null) {
            return;
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment == null || !attachment.isEnabled()) {
                continue;
            }
            float x = PoleVoxelElevationRenderer.mapHorizontalToScreen(
                layout,
                PoleVoxelElevationRenderer.ElevationView.FRONT,
                model,
                attachment.getLateralOffset());
            float yHang = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, attachment.getVerticalOffset());
            AttachmentRole role = attachment.getRole();
            if (role == AttachmentRole.TOP_WIRE) {
                drawList.addCircleFilled(x, yHang, ATTACHMENT_DOT_RADIUS, COLOR_TOP_WIRE);
                continue;
            }
            float insulatorLen = Math.min(INSULATOR_MAX_PX, Math.max(INSULATOR_MIN_PX, layout.blockSize() * 1.5f));
            float yEnd = yHang + insulatorLen;
            drawList.addLine(x, yHang, x, yEnd, COLOR_INSULATOR, 1.0f);
            drawList.addCircleFilled(x, yEnd, ATTACHMENT_DOT_RADIUS, COLOR_WIRE);
            float hintHalf = Math.min(WIRE_HINT_MAX_PX * 0.5f, layout.blockSize() * 1.2f);
            drawList.addLine(x - hintHalf, yEnd, x + hintHalf, yEnd, COLOR_WIRE, 0.9f);
        }
    }

    /** 废土风电：三叶桨叶暗示（不修改世界几何）。 */
    public static void drawWindRotorOverlay(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        float cx = (x0 + x1) * 0.5f;
        float cy = y0 + (y1 - y0) * 0.28f;
        float hubR = 2.5f;
        drawList.addCircleFilled(cx, cy, hubR, COLOR_ROTOR);
        float bladeLen = Math.min(14f, (x1 - x0) * 0.22f);
        drawBlade(drawList, cx, cy, -0.35f, bladeLen);
        drawBlade(drawList, cx, cy, 2.75f, bladeLen);
        drawBlade(drawList, cx, cy, 0.95f, bladeLen * 0.85f);
    }

    private static void drawBlade(ImDrawList drawList, float cx, float cy, float angle, float length) {
        float x2 = cx + (float) Math.cos(angle) * length;
        float y2 = cy + (float) Math.sin(angle) * length;
        drawList.addLine(cx, cy, x2, y2, COLOR_ROTOR, 1.4f);
    }

    /** 智能分级塔：角落三段高度刻度。 */
    public static void drawAdaptiveHeightMarker(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        float tickW = 3f;
        float gap = 2f;
        float baseX = x1 - 6f;
        float baseY = y1 - 6f;
        drawTick(drawList, baseX, baseY, tickW, 2f);
        drawTick(drawList, baseX - gap - tickW, baseY - 3f, tickW, 4f);
        drawTick(drawList, baseX - (gap + tickW) * 2f, baseY - 7f, tickW, 6f);
    }

    private static void drawTick(ImDrawList drawList, float x, float bottomY, float width, float height) {
        drawList.addRectFilled(x, bottomY - height, x + width, bottomY, COLOR_MARKER);
    }
}
