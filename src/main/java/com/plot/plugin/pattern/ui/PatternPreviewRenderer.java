package com.plot.plugin.pattern.ui;

import com.plot.core.command.BlockRecord;
import com.plot.core.material.BlockColorRegistry;
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
    private static final int MAX_RENDERED_CELLS = 12_000;
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
        float scale = Math.min(
            (plotX1 - plotX0) / Math.max(1f, bounds.width()),
            (plotY1 - plotY0) / Math.max(1f, bounds.depth()));
        float offsetX = plotX0 + ((plotX1 - plotX0) - bounds.width() * scale) * 0.5f;
        float offsetY = plotY0 + ((plotY1 - plotY0) - bounds.depth() * scale) * 0.5f;
        int stride = Math.max(1, (result.placementRecords.size() + MAX_RENDERED_CELLS - 1) / MAX_RENDERED_CELLS);

        int index = 0;
        drawList.pushClipRect(plotX0, plotY0, plotX1, plotY1);
        for (BlockRecord record : result.placementRecords.values()) {
            if (index++ % stride != 0) {
                continue;
            }
            BlockPos pos = record.pos;
            float cellX = offsetX + (pos.getX() - bounds.minX) * scale;
            float cellY = offsetY + (pos.getZ() - bounds.minZ) * scale;
            float cellSize = Math.max(1f, scale);
            drawList.addRectFilled(cellX, cellY, cellX + cellSize, cellY + cellSize, colorFor(record.newBlockId));
        }
        drawList.popClipRect();

        ImGui.dummy(width, PREVIEW_HEIGHT);
        ImGui.endChild();
    }

    private static int colorFor(String blockId) {
        return BlockColorRegistry.colorFor(blockId);
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
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