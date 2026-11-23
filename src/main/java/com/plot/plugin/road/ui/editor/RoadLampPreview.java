package com.plot.plugin.road.ui.editor;

import com.plot.core.block.BlockSpec;
import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small block-space preview for a local structure or a cross-section with mounted lamps. */
final class RoadLampPreview {
    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float YAW_DRAG_DEGREES_PER_PIXEL = 0.45f;
    private static final float ZOOM_WHEEL_STEP = 0.12f;

    private List<RoadLampCompiler.Voxel> cachedStructure;
    private final List<Part> parts = new ArrayList<>();
    private float sortedYaw = Float.NaN;

    /**
     * @param yawDeg 可写长度 ≥1；悬停预览时左键左右拖动会改写
     * @param zoom   可写长度 ≥1；悬停预览时滚轮会改写，范围 [{@value MIN_ZOOM}, {@value MAX_ZOOM}]
     */
    void render(List<RoadLampCompiler.Voxel> structure, float height, float[] yawDeg, float[] zoom) {
        float width = ImGui.getContentRegionAvailX();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##road_lamp_preview_orbit", width, height);
        handleOrbitControls(yawDeg, zoom);
        float yaw = yawDeg[0];
        float zoomFactor = zoom[0];
        var draw = ImGui.getWindowDrawList();
        float right = left + width;
        float bottom = top + height;
        // 与隧道预览同色边框，直角无圆角。
        draw.addRectFilled(left, top, right, bottom, PluginUiColors.MAP_BG, 0f);
        draw.addRect(left, top, right, bottom, PluginUiColors.PANEL_BORDER, 0f, 0, 1f);
        float angle = (float) Math.toRadians(yaw);
        float sine = (float) Math.sin(angle);
        float cosine = (float) Math.cos(angle);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

        if (structure != cachedStructure) {
            cachedStructure = structure;
            rebuildParts(structure);
            sortedYaw = Float.NaN;
        }
        if (yaw != sortedYaw) {
            parts.sort(Comparator.comparingInt((Part part) -> part.y0 < 0f ? 0 : 1)
                .thenComparingDouble(part -> depth(part.x0 + part.x1, part.z0 + part.z1, sine, cosine) * 0.5
                    + (part.y0 + part.y1) * 0.275));
            sortedYaw = yaw;
        }
        for (var part : parts) {
            for (float x : new float[]{part.x0, part.x1}) {
                for (float z : new float[]{part.z0, part.z1}) {
                    float u = x * cosine - z * sine;
                    float v = depth(x, z, sine, cosine) * 0.34f;
                    minX = Math.min(minX, u);
                    maxX = Math.max(maxX, u);
                    minY = Math.min(minY, v - part.y1);
                    maxY = Math.max(maxY, v - part.y0);
                }
            }
        }
        if (parts.isEmpty()) return;
        float size = zoomFactor * Math.min((width - 16) / (maxX - minX), (height - 16) / (maxY - minY));
        if (size <= 0) return;
        var view = new View(sine, cosine, size,
            left + (width - (maxX + minX) * size) / 2f, top + (height - (maxY + minY) * size) / 2f);
        draw.pushClipRect(left, top, left + width, top + height, true);
        for (var part : parts) drawPart(draw, view, part);
        draw.popClipRect();
    }

    private static void handleOrbitControls(float[] yawDeg, float[] zoom) {
        if (!ImGui.isItemHovered()) {
            return;
        }
        UIUtils.setTooltip(PlotI18n.tr("plugin.road.lamp.preview_orbit_hint"));
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0f) {
            zoom[0] = Math.clamp(zoom[0] + wheel * ZOOM_WHEEL_STEP, MIN_ZOOM, MAX_ZOOM);
            ImGui.getIO().setMouseWheel(0f);
        }
        if (ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
            // 向左拖 → 模型随视角向左转（与拖动方向一致）。
            yawDeg[0] = normalizeYaw(yawDeg[0] - ImGui.getIO().getMouseDeltaX() * YAW_DRAG_DEGREES_PER_PIXEL);
        }
    }

    private static float normalizeYaw(float yaw) {
        float wrapped = yaw % 360f;
        if (wrapped > 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    private void rebuildParts(List<RoadLampCompiler.Voxel> structure) {
        parts.clear();
        var blocks = structure.stream().collect(Collectors.toMap(RoadLampCompiler.Voxel::position,
            RoadLampCompiler.Voxel::block, (previous, next) -> next));
        blocks.forEach((pos, block) -> {
            BlockSpec spec = BlockSpec.parse(block);
            if (pos.getY() == -1 && !spec.blockId().endsWith("_slab")
                    && !spec.blockId().endsWith("_wall") && !spec.blockId().endsWith("_fence")) {
                // The flat reference pavement can be drawn one strip at a time.
                if (block.equals(blocks.get(pos.north()))) return;
                int length = 1;
                while (block.equals(blocks.get(pos.south(length)))) length++;
                parts.add(Part.at(pos, BlockColorRegistry.colorFor(spec.blockId()), 0f, 0f, 0f, 1f, 1f, length));
                return;
            }
            addParts(parts, blocks, pos, spec);
        });
    }

    private static float depth(float x, float z, float sine, float cosine) {
        return x * sine + z * cosine;
    }

    private static void addParts(List<Part> parts, Map<BlockPos, String> blocks, BlockPos pos, BlockSpec block) {
        String id = block.blockId();
        int color = BlockColorRegistry.colorFor(id);
        if (id.equals("minecraft:iron_chain") || id.equals("minecraft:chain")) {
            parts.add(Part.at(pos, color, 0.41f, 0f, 0.41f, 0.59f, 1f, 0.59f));
        } else if (id.endsWith(":lantern") || id.endsWith(":soul_lantern")) {
            boolean hanging = "true".equals(block.property("hanging"));
            float base = hanging ? 0.12f : 0f;
            parts.add(Part.at(pos, color, 0.31f, base, 0.31f, 0.69f, base + 0.45f, 0.69f));
            parts.add(Part.at(pos, color, 0.39f, base + 0.45f, 0.39f, 0.61f, base + 0.58f, 0.61f));
            if (hanging) {
                parts.add(Part.at(pos, 0xff676767, 0.45f, 0.70f, 0.45f, 0.55f, 1f, 0.55f));
            }
        } else if (id.endsWith("_slab") && !"double".equals(block.property("type"))) {
            boolean top = "top".equals(block.property("type"));
            parts.add(Part.at(pos, color, 0f, top ? 0.5f : 0f, 0f, 1f, top ? 1f : 0.5f, 1f));
        } else if (id.endsWith("_fence") || id.endsWith("_wall")) {
            boolean wall = id.endsWith("_wall");
            float inset = wall ? 0.25f : 0.375f;
            parts.add(Part.at(pos, color, inset, 0f, inset, 1f - inset, 1f, 1f - inset));
            if (connects(blocks, pos.west())) parts.add(Part.at(pos, color, 0f, 0.28f, inset, inset, 0.88f, 1f - inset));
            if (connects(blocks, pos.east())) parts.add(Part.at(pos, color, 1f - inset, 0.28f, inset, 1f, 0.88f, 1f - inset));
            if (connects(blocks, pos.north())) parts.add(Part.at(pos, color, inset, 0.28f, 0f, 1f - inset, 0.88f, inset));
            if (connects(blocks, pos.south())) parts.add(Part.at(pos, color, inset, 0.28f, 1f - inset, 1f - inset, 0.88f, 1f));
        } else {
            parts.add(Part.at(pos, color, 0f, 0f, 0f, 1f, 1f, 1f));
        }
    }

    private static boolean connects(Map<BlockPos, String> blocks, BlockPos pos) {
        String block = blocks.get(pos);
        if (block == null) return false;
        String id = BlockSpec.parse(block).blockId();
        return !id.endsWith(":lantern") && !id.endsWith(":soul_lantern") && !id.endsWith("chain") && !id.endsWith("_slab");
    }

    private static void drawPart(ImDrawList draw, View view, Part p) {
        float fx = view.sine >= 0 ? p.x1 : p.x0;
        float fz = view.cosine >= 0 ? p.z1 : p.z0;
        face(draw, view, shade(p.color, 0.69f),
            fx, p.y0, p.z0, fx, p.y0, p.z1, fx, p.y1, p.z1, fx, p.y1, p.z0);
        face(draw, view, shade(p.color, 0.84f),
            p.x0, p.y0, fz, p.x1, p.y0, fz, p.x1, p.y1, fz, p.x0, p.y1, fz);
        face(draw, view, shade(p.color, 1f),
            p.x0, p.y1, p.z0, p.x1, p.y1, p.z0, p.x1, p.y1, p.z1, p.x0, p.y1, p.z1);
    }

    private static void face(ImDrawList draw, View v, int color, float x0, float y0, float z0,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        draw.addQuadFilled(v.x(x0, z0), v.y(x0, y0, z0), v.x(x1, z1), v.y(x1, y1, z1),
            v.x(x2, z2), v.y(x2, y2, z2), v.x(x3, z3), v.y(x3, y3, z3), color);
    }

    private record Part(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        static Part at(BlockPos p, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
            return new Part(p.getX() + x0, p.getY() + y0, p.getZ() + z0,
                p.getX() + x1, p.getY() + y1, p.getZ() + z1, color);
        }
    }

    private static int shade(int argb, float multiplier) {
        int red = (int) (((argb >> 16) & 255) * multiplier);
        int green = (int) (((argb >> 8) & 255) * multiplier);
        int blue = (int) ((argb & 255) * multiplier);
        return 0xff000000 | (blue << 16) | (green << 8) | red;
    }

    private record View(float sine, float cosine, float scale, float ox, float oy) {
        float x(float x, float z) { return ox + scale * (x * cosine - z * sine); }
        float y(float x, float y, float z) { return oy + scale * (depth(x, z, sine, cosine) * 0.34f - y); }
    }
}
