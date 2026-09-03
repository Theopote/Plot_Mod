package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImVec2;

/**
 * 画布挖填热力图：红挖、蓝填、绿几乎不动。
 */
public final class EarthworkCutFillHeatmapRenderer {
    private static final int MAX_CELLS = 6000;
    private static final int CUT = ImColor.rgba(220, 64, 64, 150);
    private static final int FILL = ImColor.rgba(64, 140, 230, 150);
    private static final int UNCHANGED = ImColor.rgba(72, 176, 96, 70);

    private EarthworkCutFillHeatmapRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, DesignTerrainGrid grid) {
        if (drawList == null || camera == null || grid == null || grid.cellCount() == 0) {
            return;
        }
        int stride = 1;
        int count = grid.cellCount();
        if (count > MAX_CELLS) {
            stride = (int) Math.ceil(count / (double) MAX_CELLS);
        }
        int index = 0;
        ImVec2[] quad = new ImVec2[] {new ImVec2(), new ImVec2(), new ImVec2(), new ImVec2()};
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || !cell.participatesInEarthwork()) {
                continue;
            }
            if ((index++ % stride) != 0) {
                continue;
            }
            int color = colorFor(cell.deltaY());
            float x = cell.worldX();
            float z = cell.worldZ();
            toScreen(camera, x, z, quad[0]);
            toScreen(camera, x + 1f, z, quad[1]);
            toScreen(camera, x + 1f, z + 1f, quad[2]);
            toScreen(camera, x, z + 1f, quad[3]);
            drawList.addConvexPolyFilled(quad, 4, color);
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

    private static void toScreen(CanvasCamera camera, float worldX, float worldZ, ImVec2 out) {
        Vec2d screen = camera.worldToScreen(new Vec2d(worldX, worldZ));
        out.x = (float) screen.x;
        out.y = (float) screen.y;
    }
}
