package com.plot.plugin.pattern.ui;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** 生成 Tab 内的俯视图案预览。 */
public final class PatternPreviewRenderer {
    private static final float PLOT_INSET = 1f;
    private static final int COLOR_BACKGROUND = 0xFF20252A;
    private static final int PREVIEW_CANVAS_FLAGS =
        ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

    private PatternPreviewRenderer() {
    }

    public static void render(PatternGenerationResult result) {
        float width = Math.max(160f, ImGui.getContentRegionAvailX());
        float previewHeight = PatternOverviewRenderer.mapHeightForWidth(width);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.beginChild(
            "##pattern_preview_area",
            width,
            previewHeight,
            true,
            PREVIEW_CANVAS_FLAGS);

        ImVec2 origin = ImGui.getCursorScreenPos();
        float contentWidth = ImGui.getContentRegionAvail().x;
        float contentHeight = ImGui.getContentRegionAvail().y;
        ImDrawList drawList = ImGui.getWindowDrawList();
        float plotX0 = origin.x + PLOT_INSET;
        float plotY0 = origin.y + PLOT_INSET;
        float plotX1 = origin.x + contentWidth - PLOT_INSET;
        float plotY1 = origin.y + contentHeight - PLOT_INSET;

        drawList.addRectFilled(plotX0, plotY0, plotX1, plotY1, COLOR_BACKGROUND);

        if (result == null || result.placementRecords.isEmpty()) {
            drawCenteredText(
                drawList,
                plotX0,
                plotY0,
                plotX1,
                plotY1,
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.preview_area_empty"));
            ImGui.dummy(contentWidth, contentHeight);
            ImGui.endChild();
            ImGui.popStyleVar();
            return;
        }

        Bounds bounds = Bounds.from(result.placementRecords);
        float[] fitted = fitAspectRect(
            plotX0,
            plotY0,
            plotX1,
            plotY1,
            bounds.worldWidth() / (float) Math.max(1, bounds.worldDepth()));
        float drawX0 = fitted[0];
        float drawY0 = fitted[1];
        float drawX1 = fitted[2];
        float drawY1 = fitted[3];
        int screenW = Math.max(1, (int) Math.floor(drawX1 - drawX0));
        int screenH = Math.max(1, (int) Math.floor(drawY1 - drawY0));
        int worldW = bounds.worldWidth();
        int worldD = bounds.worldDepth();
        int bucketWidth = PatternPreviewRasterDraw.bucketWidth(screenW, worldW);
        int bucketHeight = PatternPreviewRasterDraw.bucketHeight(screenH, worldD);
        PatternPreviewBuckets buckets = PatternPreviewBucketMapper.aggregate(
            result.placementRecords,
            bounds,
            bucketWidth,
            bucketHeight);

        PatternPreviewRasterDraw.drawBuckets(drawList, buckets, drawX0, drawY0, drawX1, drawY1);

        ImGui.dummy(contentWidth, contentHeight);
        ImGui.endChild();
        ImGui.popStyleVar();
    }

    private static float[] fitAspectRect(
            float x0,
            float y0,
            float x1,
            float y1,
            float contentAspect) {
        float boxW = x1 - x0;
        float boxH = y1 - y0;
        if (boxW <= 0f || boxH <= 0f || contentAspect <= 0f) {
            return new float[] {x0, y0, x1, y1};
        }
        float boxAspect = boxW / boxH;
        float drawW;
        float drawH;
        if (contentAspect > boxAspect) {
            drawW = boxW;
            drawH = boxW / contentAspect;
        } else {
            drawH = boxH;
            drawW = boxH * contentAspect;
        }
        float insetX = (boxW - drawW) * 0.5f;
        float insetY = (boxH - drawH) * 0.5f;
        return new float[] {x0 + insetX, y0 + insetY, x0 + insetX + drawW, y0 + insetY + drawH};
    }

    private static void drawCenteredText(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            String text) {
        ImVec2 textSize = ImGui.calcTextSize(text);
        float textX = x0 + Math.max(0f, (x1 - x0 - textSize.x) * 0.5f);
        float textY = y0 + Math.max(0f, (y1 - y0 - textSize.y) * 0.5f);
        drawList.addText(textX, textY, color, text);
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

        int worldWidth() {
            return maxX - minX + 1;
        }

        int worldDepth() {
            return maxZ - minZ + 1;
        }

        float width() {
            return worldWidth();
        }

        float depth() {
            return worldDepth();
        }
    }
}
