package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;

/**
 * 画布挖填热力图：红挖、蓝填、绿几乎不动。
 */
public final class EarthworkCutFillHeatmapRenderer {
    private static final int MAX_CELLS = 800;
    private static final int CUT = ImColor.rgba(220, 64, 64, 140);
    private static final int FILL = ImColor.rgba(64, 140, 230, 140);
    private static final int UNCHANGED = ImColor.rgba(72, 176, 96, 55);
    private static final float[] SCREEN = new float[2];

    private EarthworkCutFillHeatmapRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, DesignTerrainGrid grid) {
        if (drawList == null || camera == null || grid == null || grid.cellCount() == 0) {
            return;
        }
        int count = grid.cellCount();
        int stride = count > MAX_CELLS ? (int) Math.ceil(count / (double) MAX_CELLS) : 1;
        float cell = Math.max(1.5f, camera.getZoom());
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();
        int index = 0;
        for (DesignTerrainCell terrainCell : grid.cells().values()) {
            if (terrainCell == null || !terrainCell.participatesInEarthwork()) {
                continue;
            }
            if ((index++ % stride) != 0) {
                continue;
            }
            camera.worldToScreen(terrainCell.worldX() + 0.5, terrainCell.worldZ() + 0.5, SCREEN);
            float x = SCREEN[0];
            float y = SCREEN[1];
            if (x + cell < 0 || y + cell < 0 || x - cell > displayW || y - cell > displayH) {
                continue;
            }
            drawList.addRectFilled(x - cell * 0.5f, y - cell * 0.5f, x + cell * 0.5f, y + cell * 0.5f, colorFor(terrainCell.deltaY()));
        }
    }

    static int colorFor(int deltaY) {
        if (deltaY > 0) {
            return FILL;
        }
        if (deltaY < 0) {
            return CUT;
        }
        return UNCHANGED;
    }
}
