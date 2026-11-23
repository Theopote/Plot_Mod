package com.plot.plugin.road.ui.editor;

import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small block-space preview for a local structure or a cross-section with mounted lamps. */
final class RoadLampPreview {
    private RoadLampPreview() {
    }

    static void render(List<RoadLampCompiler.Voxel> structure, float height, float yaw, float zoom) {
        float width = ImGui.getContentRegionAvailX();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.dummy(width, height);
        var draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + width, top + height, 0xff20242b, 4f);
        float angle = (float) Math.toRadians(yaw);
        float sine = (float) Math.sin(angle);
        float cosine = (float) Math.cos(angle);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

        var sorted = new ArrayList<>(structure);
        sorted.sort(Comparator.comparingDouble(voxel -> {
            BlockPos p = voxel.position();
            return depth(p.getX(), p.getZ(), sine, cosine) + p.getY() * 0.55;
        }));
        for (var voxel : sorted) {
            var p = voxel.position();
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    float x = p.getX() + dx;
                    float z = p.getZ() + dz;
                    float u = x * cosine - z * sine;
                    float v = depth(x, z, sine, cosine) * 0.34f - p.getY();
                    minX = Math.min(minX, u);
                    maxX = Math.max(maxX, u);
                    minY = Math.min(minY, v - 1f);
                    maxY = Math.max(maxY, v);
                }
            }
        }
        if (sorted.isEmpty()) return;
        float size = zoom * Math.min((width - 16) / (maxX - minX), (height - 16) / (maxY - minY));
        if (size <= 0) return;
        var view = new View(sine, cosine, size,
            left + (width - (maxX + minX) * size) / 2f, top + (height - (maxY + minY) * size) / 2f);
        draw.pushClipRect(left, top, left + width, top + height, true);
        for (var voxel : sorted) {
            var p = voxel.position();
            cube(draw, view, p.getX(), p.getY(), p.getZ(), BlockColorRegistry.colorFor(voxel.block()));
        }
        draw.popClipRect();
    }

    private static float depth(float x, float z, float sine, float cosine) {
        return x * sine + z * cosine;
    }

    private static void cube(ImDrawList draw, View view, int x, int y, int z, int color) {
        int fx = x + (view.sine >= 0 ? 1 : 0);
        int fz = z + (view.cosine >= 0 ? 1 : 0);
        face(draw, view, shade(color, 0.69f), fx, y, z, fx, y, z + 1, fx, y + 1, z + 1, fx, y + 1, z);
        face(draw, view, shade(color, 0.84f), x, y, fz, x + 1, y, fz, x + 1, y + 1, fz, x, y + 1, fz);
        face(draw, view, shade(color, 1f),
            x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
    }

    private static void face(ImDrawList draw, View v, int color, int x0, int y0, int z0,
                             int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
        draw.addQuadFilled(v.x(x0, z0), v.y(x0, y0, z0), v.x(x1, z1), v.y(x1, y1, z1),
            v.x(x2, z2), v.y(x2, y2, z2), v.x(x3, z3), v.y(x3, y3, z3), color);
    }

    private static int shade(int argb, float multiplier) {
        int red = (int) (((argb >> 16) & 255) * multiplier);
        int green = (int) (((argb >> 8) & 255) * multiplier);
        int blue = (int) ((argb & 255) * multiplier);
        return 0xff000000 | (blue << 16) | (green << 8) | red;
    }

    private record View(float sine, float cosine, float scale, float ox, float oy) {
        float x(int x, int z) { return ox + scale * (x * cosine - z * sine); }
        float y(int x, int y, int z) { return oy + scale * (depth(x, z, sine, cosine) * 0.34f - y); }
    }
}
