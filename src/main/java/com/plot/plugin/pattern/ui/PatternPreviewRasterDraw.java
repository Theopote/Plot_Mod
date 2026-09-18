package com.plot.plugin.pattern.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;

/** 将逻辑方块桶绘制为连续色块预览（非 1px 点阵）。 */
final class PatternPreviewRasterDraw {
    private static final int COLOR_PREVIEW_BG = 0xFF1A1F24;

    private PatternPreviewRasterDraw() {
    }

    static int bucketWidth(int screenPixels, int worldCells) {
        return Math.max(1, Math.min(screenPixels, worldCells));
    }

    static int bucketHeight(int screenPixels, int worldCells) {
        return Math.max(1, Math.min(screenPixels, worldCells));
    }

    static void drawBuckets(
            ImDrawList drawList,
            PatternPreviewBuckets buckets,
            float x0,
            float y0,
            float x1,
            float y1) {
        drawBuckets(drawList, buckets, x0, y0, x1, y1, null);
    }

    static void drawBuckets(
            ImDrawList drawList,
            PatternPreviewBuckets buckets,
            float x0,
            float y0,
            float x1,
            float y1,
            String emptyLabelKey) {
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_PREVIEW_BG);
        if (buckets == null || !hasPixels(buckets)) {
            if (emptyLabelKey != null && !emptyLabelKey.isBlank()) {
                drawPlaceholder(drawList, x0, y0, x1, y1, emptyLabelKey);
            }
            return;
        }

        int bucketWidth = buckets.width();
        int bucketHeight = buckets.height();
        float plotWidth = Math.max(1f, x1 - x0);
        float plotHeight = Math.max(1f, y1 - y0);
        float cellW = plotWidth / bucketWidth;
        float cellH = plotHeight / bucketHeight;

        drawList.pushClipRect(x0, y0, x1, y1, true);
        for (int py = 0; py < bucketHeight; py++) {
            for (int px = 0; px < bucketWidth; px++) {
                if (buckets.countAt(px, py) == 0) {
                    continue;
                }
                int color = buckets.colorAt(px, py) | 0xFF000000;
                float cellX0 = x0 + px * cellW;
                float cellY0 = y0 + py * cellH;
                drawList.addRectFilled(cellX0, cellY0, cellX0 + cellW, cellY0 + cellH, color);
            }
        }
        drawList.popClipRect();
    }

    private static boolean hasPixels(PatternPreviewBuckets buckets) {
        for (int py = 0; py < buckets.height(); py++) {
            for (int px = 0; px < buckets.width(); px++) {
                if (buckets.countAt(px, py) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void drawPlaceholder(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            String labelKey) {
        drawList.addText(
            x0 + 4f,
            y0 + (y1 - y0) * 0.5f - 7f,
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(labelKey));
    }
}
