package com.plot.plugin.road;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;

/**
 * 道路纵断面预览（ImGui 绘制）。
 */
public final class RoadLongitudinalProfileRenderer {
    private static final float PREVIEW_HEIGHT = 120f;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_AXIS = 0xFF888888;
    private static final int COLOR_GROUND = 0xFF8B5A2B;
    private static final int COLOR_GUIDE = 0xFF4DA3FF;
    private static final int COLOR_TARGET = 0xFFB0B0B0;
    private static final int COLOR_LABEL = 0xFFAAAAAA;

    private RoadLongitudinalProfileRenderer() {
    }

    public static void render(RoadGenerationResult result) {
        render(result, true);
    }

    public static void render(RoadGenerationResult result, boolean showTitle) {
        if (result == null || !result.hasProfileData()) {
            return;
        }

        if (showTitle) {
            ImGui.text(PlotI18n.tr("plugin.road.longitudinal_profile"));
        }
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + PREVIEW_HEIGHT;

        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);

        drawProfile(
            drawList,
            result.profileDistances,
            result.profileGroundHeights,
            result.profileGuideLine,
            result.profileTargetHeights,
            x0,
            y0,
            width,
            PREVIEW_HEIGHT);

        ImGui.dummy(width, PREVIEW_HEIGHT);
        ImGui.textColored(COLOR_GROUND, "■ " + PlotI18n.tr("plugin.road.profile_ground"));
        ImGui.sameLine();
        ImGui.textColored(COLOR_GUIDE, "--- " + PlotI18n.tr("plugin.road.profile_guide"));
        ImGui.sameLine();
        ImGui.textColored(COLOR_TARGET, "■ " + PlotI18n.tr("plugin.road.profile_target"));
    }

    static void drawProfile(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> groundHeights,
            List<Integer> guideLine,
            List<Integer> targetHeights,
            float x0,
            float y0,
            float width,
            float height) {
        if (distances == null || distances.isEmpty()) {
            return;
        }

        float padding = 10f;
        float plotX0 = x0 + padding;
        float plotY0 = y0 + padding;
        float plotX1 = x0 + width - padding;
        float plotY1 = y0 + height - padding;
        float plotWidth = plotX1 - plotX0;
        float plotHeight = plotY1 - plotY0;
        if (plotWidth <= 1f || plotHeight <= 1f) {
            return;
        }

        double maxDistance = distances.getLast();
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (int i = 0; i < distances.size(); i++) {
            minHeight = Math.min(minHeight, groundHeights.get(i));
            minHeight = Math.min(minHeight, guideLine.get(i));
            maxHeight = Math.max(maxHeight, groundHeights.get(i));
            maxHeight = Math.max(maxHeight, guideLine.get(i));
            if (i < targetHeights.size()) {
                minHeight = Math.min(minHeight, targetHeights.get(i));
                maxHeight = Math.max(maxHeight, targetHeights.get(i));
            }
        }
        if (minHeight == maxHeight) {
            minHeight -= 2;
            maxHeight += 2;
        } else {
            minHeight -= 1;
            maxHeight += 1;
        }

        drawList.addLine(plotX0, plotY1, plotX1, plotY1, COLOR_AXIS, 1f);
        drawList.addLine(plotX0, plotY0, plotX0, plotY1, COLOR_AXIS, 1f);

        drawPolyline(drawList, distances, groundHeights, maxDistance, minHeight, maxHeight,
            plotX0, plotY0, plotWidth, plotHeight, COLOR_GROUND, 1.8f, false);
        drawPolyline(drawList, distances, guideLine, maxDistance, minHeight, maxHeight,
            plotX0, plotY0, plotWidth, plotHeight, COLOR_GUIDE, 1.4f, true);

        List<Double> targetDistances = targetHeights.size() == distances.size()
            ? distances
            : buildTargetDistances(distances, targetHeights.size());
        drawPolyline(drawList, targetDistances, targetHeights, maxDistance, minHeight, maxHeight,
            plotX0, plotY0, plotWidth, plotHeight, COLOR_TARGET, 2.4f, false);

        drawList.addText(plotX0, plotY0 - 2f, COLOR_LABEL, "Y=" + maxHeight);
        drawList.addText(plotX0, plotY1 - ImGui.getTextLineHeight(), COLOR_LABEL, "Y=" + minHeight);
        drawList.addText(plotX1 - 36f, plotY1 + 2f, COLOR_LABEL, String.format("%.0fm", maxDistance));
    }

    private static List<Double> buildTargetDistances(List<Double> distances, int targetCount) {
        if (targetCount <= 1 || distances.isEmpty()) {
            return distances;
        }
        double total = distances.getLast();
        java.util.ArrayList<Double> targetDistances = new java.util.ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            double ratio = (double) i / (targetCount - 1);
            targetDistances.add(total * ratio);
        }
        return targetDistances;
    }

    private static void drawPolyline(
            ImDrawList drawList,
            List<Double> distances,
            List<Integer> heights,
            double maxDistance,
            int minHeight,
            int maxHeight,
            float plotX0,
            float plotY0,
            float plotWidth,
            float plotHeight,
            int color,
            float thickness,
            boolean dashed) {
        if (distances.size() < 2 || heights.size() < 2) {
            return;
        }

        float previousX = 0f;
        float previousY = 0f;
        boolean hasPrevious = false;
        for (int i = 0; i < Math.min(distances.size(), heights.size()); i++) {
            float x = toPlotX(distances.get(i), maxDistance, plotX0, plotWidth);
            float y = toPlotY(heights.get(i), minHeight, maxHeight, plotY0, plotHeight);
            if (hasPrevious) {
                if (!dashed || i % 2 == 0) {
                    drawList.addLine(previousX, previousY, x, y, color, thickness);
                }
            }
            previousX = x;
            previousY = y;
            hasPrevious = true;
        }
    }

    private static float toPlotX(double distance, double maxDistance, float plotX0, float plotWidth) {
        if (maxDistance <= 1e-9) {
            return plotX0;
        }
        return plotX0 + (float) (distance / maxDistance) * plotWidth;
    }

    private static float toPlotY(int height, int minHeight, int maxHeight, float plotY0, float plotHeight) {
        float ratio = (height - minHeight) / (float) (maxHeight - minHeight);
        return plotY0 + plotHeight * (1f - ratio);
    }
}
