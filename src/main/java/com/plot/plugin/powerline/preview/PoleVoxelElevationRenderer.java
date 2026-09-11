package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import imgui.ImDrawList;

/** 体素立面 2D 预览（轻量 2.5D 方块，三级 LOD）。 */
public final class PoleVoxelElevationRenderer {
    private static final float LOD_DETAILED_MIN_BLOCK = 2.0f;
    private static final float LOD_DOWNSAMPLE_THRESHOLD = 0.8f;
    private static final int MAX_DOWNSAMPLE_GROUP = 8;

    public enum ElevationView {
        FRONT,
        SIDE
    }

    enum PreviewLod {
        DETAILED,
        FILLED,
        DOWNSAMPLED
    }

    public static final class ElevationLayout {
        private final int groupSize;
        private final int effectiveColumns;
        private final int effectiveRows;
        private final float blockSize;
        private final float originX;
        private final float originY;
        private final PreviewLod lod;

        ElevationLayout(
                int groupSize,
                int effectiveColumns,
                int effectiveRows,
                float blockSize,
                float originX,
                float originY,
                PreviewLod lod) {
            this.groupSize = groupSize;
            this.effectiveColumns = effectiveColumns;
            this.effectiveRows = effectiveRows;
            this.blockSize = blockSize;
            this.originX = originX;
            this.originY = originY;
            this.lod = lod;
        }

        public int groupSize() {
            return groupSize;
        }

        public int effectiveColumns() {
            return effectiveColumns;
        }

        public int effectiveRows() {
            return effectiveRows;
        }

        public float blockSize() {
            return blockSize;
        }

        public float originX() {
            return originX;
        }

        public float originY() {
            return originY;
        }

        public PreviewLod lod() {
            return lod;
        }
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
        ElevationLayout layout = computeLayout(model, view, x0, y0, x1, y1);
        if (layout == null) {
            return false;
        }

        for (int effRow = 0; effRow < layout.effectiveRows(); effRow++) {
            for (int effCol = 0; effCol < layout.effectiveColumns(); effCol++) {
                String blockId = resolveGroupBlock(model, view, effCol, effRow, layout.groupSize());
                if (blockId == null) {
                    continue;
                }
                float bx = layout.originX() + effCol * layout.blockSize();
                float by = layout.originY() + (layout.effectiveRows() - 1 - effRow) * layout.blockSize();
                if (layout.lod() == PreviewLod.DETAILED) {
                    drawDetailedBlock(drawList, bx, by, layout.blockSize(), BlockPreviewColors.colorFor(blockId));
                } else {
                    drawFilledBlock(drawList, bx, by, layout.blockSize(), BlockPreviewColors.colorFor(blockId));
                }
            }
        }
        return true;
    }

    public static ElevationLayout computeLayout(
            PoleVoxelPreviewModel model,
            ElevationView view,
            float x0,
            float y0,
            float x1,
            float y1) {
        int columns = view == ElevationView.FRONT ? model.widthX() : model.widthZ();
        int rows = model.heightY();
        if (columns <= 0 || rows <= 0) {
            return null;
        }

        float pad = 2f;
        float availW = Math.max(1f, x1 - x0 - pad * 2f);
        float availH = Math.max(1f, y1 - y0 - pad * 2f);

        int groupSize = 1;
        float blockSize = Math.min(availW / columns, availH / rows);
        while (blockSize < LOD_DOWNSAMPLE_THRESHOLD && groupSize < MAX_DOWNSAMPLE_GROUP) {
            groupSize *= 2;
            int effectiveColumns = (columns + groupSize - 1) / groupSize;
            int effectiveRows = (rows + groupSize - 1) / groupSize;
            blockSize = Math.min(availW / effectiveColumns, availH / effectiveRows);
        }

        int effectiveColumns = (columns + groupSize - 1) / groupSize;
        int effectiveRows = (rows + groupSize - 1) / groupSize;
        float drawW = blockSize * effectiveColumns;
        float drawH = blockSize * effectiveRows;
        float originX = x0 + (x1 - x0 - drawW) * 0.5f;
        float originY = y1 - pad - drawH;

        PreviewLod lod;
        if (groupSize > 1) {
            lod = PreviewLod.DOWNSAMPLED;
        } else if (blockSize >= LOD_DETAILED_MIN_BLOCK) {
            lod = PreviewLod.DETAILED;
        } else {
            lod = PreviewLod.FILLED;
        }

        return new ElevationLayout(
            groupSize,
            effectiveColumns,
            effectiveRows,
            blockSize,
            originX,
            originY,
            lod);
    }

    private static String resolveGroupBlock(
            PoleVoxelPreviewModel model,
            ElevationView view,
            int effCol,
            int effRow,
            int groupSize) {
        int startCol = effCol * groupSize;
        int startRow = effRow * groupSize;
        String chosen = null;
        for (int dr = 0; dr < groupSize; dr++) {
            int row = startRow + dr;
            if (row >= model.heightY()) {
                continue;
            }
            int y = model.minY() + row;
            for (int dc = 0; dc < groupSize; dc++) {
                int col = startCol + dc;
                if (col >= (view == ElevationView.FRONT ? model.widthX() : model.widthZ())) {
                    continue;
                }
                String blockId = view == ElevationView.FRONT
                    ? model.blockAtFront(model.minX() + col, y)
                    : model.blockAtSide(model.minZ() + col, y);
                if (blockId != null) {
                    chosen = blockId;
                }
            }
        }
        return chosen;
    }

    private static void drawFilledBlock(ImDrawList drawList, float x, float y, float size, int color) {
        drawList.addRectFilled(x, y, x + size, y + size, color);
    }

    private static void drawDetailedBlock(ImDrawList drawList, float x, float y, float size, int color) {
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

    /** 将体素水平坐标映射到屏幕 X（正视 = lateral/X，侧视 = longitudinal/Z）。 */
    public static float mapHorizontalToScreen(
            ElevationLayout layout,
            ElevationView view,
            PoleVoxelPreviewModel model,
            double horizontalCoord) {
        double min = view == ElevationView.FRONT ? model.minX() : model.minZ();
        return layout.originX() + (float) (horizontalCoord - min) * layout.blockSize() + layout.blockSize() * 0.5f;
    }

    /** 将体素高度映射到屏幕 Y（保留小数，挂点 / 轮毂不必贴格）。 */
    public static float mapVerticalToScreen(
            ElevationLayout layout,
            PoleVoxelPreviewModel model,
            double verticalCoord) {
        double min = model.minY();
        double max = model.maxY();
        double y = Math.max(min, Math.min(max, verticalCoord));
        return layout.originY() + (float) ((max - y) * layout.blockSize() + layout.blockSize() * 0.5f);
    }
}
