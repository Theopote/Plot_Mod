package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import imgui.ImDrawList;

/** 体素立面 2D 预览（轻量 2.5D 方块）。 */
public final class PoleVoxelElevationRenderer {
    public enum ElevationView {
        FRONT,
        SIDE
    }

    private PoleVoxelElevationRenderer() {
    }

    public static boolean drawFront(ImDrawList drawList, PoleDesign design, float x0, float y0, float x1, float y1) {
        return draw(drawList, PoleVoxelizer.voxelize(design), ElevationView.FRONT, x0, y0, x1, y1);
    }

    public static boolean drawSide(ImDrawList drawList, PoleDesign design, float x0, float y0, float x1, float y1) {
        return draw(drawList, PoleVoxelizer.voxelize(design), ElevationView.SIDE, x0, y0, x1, y1);
    }

    public static boolean draw(
            ImDrawList drawList,
            PoleVoxelPreviewModel model,
            ElevationView view,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (drawList == null || model == null || model.isEmpty()) {
            return false;
        }
        int columns = view == ElevationView.FRONT ? model.widthX() : model.widthZ();
        int rows = model.heightY();
        if (columns <= 0 || rows <= 0) {
            return false;
        }
        float pad = 2f;
        float availW = Math.max(1f, x1 - x0 - pad * 2f);
        float availH = Math.max(1f, y1 - y0 - pad * 2f);
        float blockSize = Math.min(availW / columns, availH / rows);
        if (blockSize < 1.5f) {
            return false;
        }
        float drawW = blockSize * columns;
        float drawH = blockSize * rows;
        float originX = x0 + (x1 - x0 - drawW) * 0.5f;
        float originY = y1 - pad - drawH;

        for (int row = 0; row < rows; row++) {
            int y = model.minY() + row;
            for (int col = 0; col < columns; col++) {
                String blockId = view == ElevationView.FRONT
                    ? model.blockAtFront(model.minX() + col, y)
                    : model.blockAtSide(model.minZ() + col, y);
                if (blockId == null) {
                    continue;
                }
                float bx = originX + col * blockSize;
                float by = originY + (rows - 1 - row) * blockSize;
                drawBlock(drawList, bx, by, blockSize, BlockPreviewColors.colorFor(blockId));
            }
        }
        return true;
    }

    private static void drawBlock(ImDrawList drawList, float x, float y, float size, int color) {
        float x1 = x + size;
        float y1 = y + size;
        drawList.addRectFilled(x, y, x1, y1, color);
        int hi = BlockPreviewColors.highlight(color);
        int sh = BlockPreviewColors.shadow(color);
        float edge = Math.max(1f, size * 0.12f);
        drawList.addLine(x, y, x1, y, hi, edge);
        drawList.addLine(x, y, x, y1, hi, edge);
        drawList.addLine(x1, y, x1, y1, sh, edge);
        drawList.addLine(x, y1, x1, y1, sh, edge);
    }
}
