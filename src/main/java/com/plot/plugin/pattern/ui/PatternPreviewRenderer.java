package com.plot.plugin.pattern.ui;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** 生成 Tab 内的俯视图案预览。 */
public final class PatternPreviewRenderer {
    private static final float PREVIEW_HEIGHT = 220f;
    private static final float PADDING = 10f;
    private static final int COLOR_BACKGROUND = 0xFF20252A;
    private static final int COLOR_BORDER = 0xFF56616B;
    private static final int COLOR_LABEL = 0xFFD5D9DC;

    private PatternPreviewRenderer() {
    }

    public static void render(PatternGenerationResult result) {
        float width = Math.max(160f, ImGui.getContentRegionAvailX());
        ImGui.beginChild(
            "##pattern_preview_area",
            width,
            PREVIEW_HEIGHT,
            true,
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        ImVec2 origin = ImGui.getCursorScreenPos();
        float contentWidth = ImGui.getContentRegionAvail().x;
        float contentHeight = ImGui.getContentRegionAvail().y;
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x + PADDING;
        float y0 = origin.y + PADDING;
        float x1 = origin.x + contentWidth - PADDING;
        float y1 = origin.y + contentHeight - PADDING;

        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BACKGROUND);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);
        drawList.addText(x0 + 6f, y0 + 5f, COLOR_LABEL, PlotI18n.tr("plugin.pattern.preview_area"));

        if (result == null || result.placementRecords.isEmpty()) {
            drawList.addText(
                x0 + 6f,
                y0 + 28f,
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.preview_area_empty"));
            ImGui.dummy(width, PREVIEW_HEIGHT);
            ImGui.endChild();
            return;
        }

        Bounds bounds = Bounds.from(result.placementRecords);
        float plotX0 = x0 + 6f;
        float plotY0 = y0 + 24f;
        float plotX1 = x1 - 6f;
        float plotY1 = y1 - 6f;
        int bucketWidth = Math.max(1, (int) Math.floor(plotX1 - plotX0));
        int bucketHeight = Math.max(1, (int) Math.floor(plotY1 - plotY0));
        PatternPreviewBuckets buckets = PatternPreviewBucketMapper.aggregate(
            result.placementRecords,
            bounds,
            bucketWidth,
            bucketHeight);

        drawList.pushClipRect(plotX0, plotY0, plotX1, plotY1);
        for (int py = 0; py < bucketHeight; py++) {
            float cellY = plotY0 + py;
            for (int px = 0; px < bucketWidth; px++) {
                if (buckets.countAt(px, py) == 0) {
                    continue;
                }
                int color = buckets.colorAt(px, py);
                float cellX = plotX0 + px;
                drawList.addRectFilled(cellX, cellY, cellX + 1f, cellY + 1f, color);
            }
        }
        drawList.popClipRect();

        ImGui.dummy(width, PREVIEW_HEIGHT);
        ImGui.endChild();
    }

    static final class Bounds {
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        Bounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        static Bounds from(Map<BlockPos, BlockRecord> records) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : records.keySet()) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, maxX, minZ, maxZ);
        }

        float width() {
            return maxX - minX + 1f;
        }

        float depth() {
            return maxZ - minZ + 1f;
        }
    }
}
