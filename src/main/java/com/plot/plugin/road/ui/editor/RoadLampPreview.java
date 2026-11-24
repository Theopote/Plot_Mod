package com.plot.plugin.road.ui.editor;

import com.plot.core.block.BlockSpec;
import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small block-space preview for a local structure or a cross-section with mounted lamps. */
public final class RoadLampPreview {
    public RoadLampPreview() {
    }
    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float DEFAULT_YAW = 30f;
    private static final float DEFAULT_ZOOM = 1f;
    private static final float YAW_DRAG_DEGREES_PER_PIXEL = 0.45f;
    private static final float ZOOM_WHEEL_STEP = 0.12f;

    private List<RoadLampCompiler.Voxel> cachedStructure;
    private final List<Part> parts = new ArrayList<>();
    private float sortedYaw = Float.NaN;

    /**
     * @param yawDeg 可写长度 ≥1；悬停预览时左键左右拖动会改写；双击重置
     * @param zoom   可写长度 ≥1；悬停预览时滚轮会改写，范围 [{@value MIN_ZOOM}, {@value MAX_ZOOM}]
     */
    public void render(List<RoadLampCompiler.Voxel> structure, float height, float[] yawDeg, float[] zoom) {
        render(structure, height, yawDeg, zoom, -1);
    }

    public void render(List<RoadLampCompiler.Voxel> structure, float height, float[] yawDeg, float[] zoom,
                int highlightLevelIndex) {
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
        // 与横断 2D 预览底色一致（PANEL_BG_DARK / 0xFF141414）。
        draw.addRectFilled(left, top, right, bottom, PluginUiColors.PANEL_BG_DARK, 0f);
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
            // 同深度用格子坐标作稳定次序，避免 Hash 遍历导致同帧闪烁/错叠。
            parts.sort(Comparator.comparingInt((Part part) -> part.y0 < 0f ? 0 : 1)
                .thenComparingDouble(part -> depth(part.x0 + part.x1, part.z0 + part.z1, sine, cosine) * 0.5
                    + (part.y0 + part.y1) * 0.275)
                .thenComparingDouble(part -> part.x0)
                .thenComparingDouble(part -> part.z0)
                .thenComparingDouble(part -> part.y0));
            sortedYaw = yaw;
        }
        for (var part : parts) {
            // 展开四角比较，避免每帧 new float[]。
            float u00 = part.x0 * cosine - part.z0 * sine;
            float u01 = part.x0 * cosine - part.z1 * sine;
            float u10 = part.x1 * cosine - part.z0 * sine;
            float u11 = part.x1 * cosine - part.z1 * sine;
            float v00 = depth(part.x0, part.z0, sine, cosine) * 0.34f;
            float v01 = depth(part.x0, part.z1, sine, cosine) * 0.34f;
            float v10 = depth(part.x1, part.z0, sine, cosine) * 0.34f;
            float v11 = depth(part.x1, part.z1, sine, cosine) * 0.34f;
            minX = Math.min(minX, Math.min(Math.min(u00, u01), Math.min(u10, u11)));
            maxX = Math.max(maxX, Math.max(Math.max(u00, u01), Math.max(u10, u11)));
            minY = Math.min(minY, Math.min(Math.min(v00, v01), Math.min(v10, v11)) - part.y1);
            maxY = Math.max(maxY, Math.max(Math.max(v00, v01), Math.max(v10, v11)) - part.y0);
        }
        if (parts.isEmpty()) return;
        float size = zoomFactor * Math.min((width - 16) / (maxX - minX), (height - 16) / (maxY - minY));
        if (size <= 0) return;
        var view = new View(sine, cosine, size,
            left + (width - (maxX + minX) * size) / 2f, top + (height - (maxY + minY) * size) / 2f);
        draw.pushClipRect(left, top, left + width, top + height, true);
        for (var part : parts) drawPart(draw, view, part, highlightLevelIndex);
        draw.popClipRect();
    }

    private static void handleOrbitControls(float[] yawDeg, float[] zoom) {
        if (!ImGui.isItemHovered()) {
            return;
        }
        UIUtils.setTooltip(PlotI18n.tr("plugin.road.lamp.preview_orbit_hint"));
        if (ImGui.isMouseDoubleClicked(0)) {
            yawDeg[0] = DEFAULT_YAW;
            zoom[0] = DEFAULT_ZOOM;
            return;
        }
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
        // LinkedHashMap：后写覆盖先写（标线盖路面），并保持稳定遍历序。
        var blocks = structure.stream().collect(Collectors.toMap(
            RoadLampCompiler.Voxel::position,
            RoadLampCompiler.Voxel::block,
            (previous, next) -> next,
            LinkedHashMap::new));
        var levelByPos = structure.stream().collect(Collectors.toMap(
            RoadLampCompiler.Voxel::position,
            RoadLampCompiler.Voxel::levelIndex,
            (previous, next) -> next,
            LinkedHashMap::new));
        // 不再沿 Z 合并长条：横断面预览纵深短，合并后画家算法会在斜视角出现阶梯/撕裂/错色条。
        // mixed 检测仍保留给调用方/测试，作为「路表是否含异材质」信号。
        blocks.forEach((pos, block) -> {
            BlockSpec spec = BlockSpec.parse(block);
            int levelIndex = levelByPos.getOrDefault(pos, -1);
            addParts(parts, blocks, pos, spec, levelIndex);
        });
    }

    /** 路表 y=-1 是否含多种材质（标线嵌入后为 true，禁止条带合并）。 */
    static boolean pavementLayerHasMixedMaterials(Map<BlockPos, String> blocks) {
        String sample = null;
        for (var entry : blocks.entrySet()) {
            if (entry.getKey().getY() != -1) {
                continue;
            }
            if (sample == null) {
                sample = entry.getValue();
            } else if (!sample.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static float depth(float x, float z, float sine, float cosine) {
        return x * sine + z * cosine;
    }

    private static void addParts(List<Part> parts, Map<BlockPos, String> blocks, BlockPos pos,
                                 BlockSpec block, int levelIndex) {
        String id = block.blockId();
        // 与 2D 横断预览一致：先规范化 block id 再取色，减少别名/脏 id 落到哈希杂色。
        int color = BlockColorRegistry.colorFor(RoadMaterialUtils.resolveBlockId(id));
        if (id.equals("minecraft:iron_chain") || id.equals("minecraft:chain")) {
            parts.add(Part.at(pos, color, levelIndex, 0.41f, 0f, 0.41f, 0.59f, 1f, 0.59f));
        } else if (isLanternFixture(id)) {
            boolean hanging = "true".equals(block.property("hanging"));
            float base = hanging ? 0.12f : 0f;
            parts.add(Part.at(pos, color, levelIndex, 0.31f, base, 0.31f, 0.69f, base + 0.45f, 0.69f));
            parts.add(Part.at(pos, color, levelIndex, 0.39f, base + 0.45f, 0.39f, 0.61f, base + 0.58f, 0.61f));
            if (hanging) {
                parts.add(Part.at(pos, 0xff676767, levelIndex, 0.45f, 0.70f, 0.45f, 0.55f, 1f, 0.55f));
            }
        } else if (id.endsWith(":end_rod")) {
            parts.add(Part.at(pos, color, levelIndex, 0.375f, 0f, 0.375f, 0.625f, 1f, 0.625f));
        } else if (id.endsWith("_torch") || id.endsWith(":torch")) {
            parts.add(Part.at(pos, 0xff6b5840, levelIndex, 0.44f, 0f, 0.44f, 0.56f, 0.62f, 0.56f));
            parts.add(Part.at(pos, color, levelIndex, 0.38f, 0.55f, 0.38f, 0.62f, 0.88f, 0.62f));
        } else if (id.endsWith("_candle") || id.endsWith(":candle")) {
            parts.add(Part.at(pos, color, levelIndex, 0.42f, 0f, 0.42f, 0.58f, 0.42f, 0.58f));
            parts.add(Part.at(pos, 0xfff0d878, levelIndex, 0.45f, 0.42f, 0.45f, 0.55f, 0.62f, 0.55f));
        } else if (id.endsWith("_campfire") || id.endsWith(":campfire")) {
            parts.add(Part.at(pos, 0xff6b5840, levelIndex, 0.05f, 0f, 0.05f, 0.95f, 0.22f, 0.95f));
            parts.add(Part.at(pos, color, levelIndex, 0.28f, 0.18f, 0.28f, 0.72f, 0.72f, 0.72f));
        } else if (id.endsWith(":light")) {
            // 半透明小块，表示隐形光源而非实心一格。
            parts.add(Part.at(pos, 0x66a8e8ff, levelIndex, 0.28f, 0.28f, 0.28f, 0.72f, 0.72f, 0.72f));
        } else if (id.endsWith("_slab") && !"double".equals(block.property("type"))) {
            boolean top = "top".equals(block.property("type"));
            parts.add(Part.at(pos, color, levelIndex, 0f, top ? 0.5f : 0f, 0f, 1f, top ? 1f : 0.5f, 1f));
        } else if (id.endsWith("_fence") || id.endsWith("_wall")) {
            boolean wall = id.endsWith("_wall");
            float inset = wall ? 0.25f : 0.375f;
            parts.add(Part.at(pos, color, levelIndex, inset, 0f, inset, 1f - inset, 1f, 1f - inset));
            if (connects(blocks, pos.west())) {
                parts.add(Part.at(pos, color, levelIndex, 0f, 0.28f, inset, inset, 0.88f, 1f - inset));
            }
            if (connects(blocks, pos.east())) {
                parts.add(Part.at(pos, color, levelIndex, 1f - inset, 0.28f, inset, 1f, 0.88f, 1f - inset));
            }
            if (connects(blocks, pos.north())) {
                parts.add(Part.at(pos, color, levelIndex, inset, 0.28f, 0f, 1f - inset, 0.88f, inset));
            }
            if (connects(blocks, pos.south())) {
                parts.add(Part.at(pos, color, levelIndex, inset, 0.28f, 1f - inset, 1f - inset, 0.88f, 1f));
            }
        } else {
            parts.add(Part.at(pos, color, levelIndex, 0f, 0f, 0f, 1f, 1f, 1f));
        }
    }

    private static boolean isLanternFixture(String id) {
        return (id.endsWith(":lantern") || id.endsWith("_lantern"))
            && !id.endsWith(":sea_lantern")
            && !id.endsWith(":jack_o_lantern");
    }

    private static boolean connects(Map<BlockPos, String> blocks, BlockPos pos) {
        String block = blocks.get(pos);
        if (block == null) return false;
        String id = BlockSpec.parse(block).blockId();
        return !isLanternFixture(id) && !id.endsWith("chain") && !id.endsWith("_slab");
    }

    /** 侧/前略提亮，避免黑混凝土等在 PANEL_BG_DARK 上几乎看不见。 */
    private static final float FACE_SHADE_SIDE = 0.68f;
    private static final float FACE_SHADE_FRONT = 0.86f;
    private static final float FACE_SHADE_TOP = 1.05f;
    private static final float EDGE_THICKNESS = 1.05f;
    /** 通道最低亮度，防止乘暗后变成「缺块」。 */
    private static final int SHADE_RGB_FLOOR = 28;

    private static void drawPart(ImDrawList draw, View view, Part p, int highlightLevelIndex) {
        float brightness;
        float alphaMul;
        if (highlightLevelIndex < 0 || p.levelIndex < 0) {
            brightness = 1f;
            alphaMul = 1f;
        } else if (p.levelIndex == highlightLevelIndex) {
            brightness = 1.28f;
            alphaMul = 1f;
        } else {
            brightness = 0.72f;
            alphaMul = 0.55f;
        }
        // 半透明光源等跳过棱边，避免边框把体素“弄脏”。
        boolean bevel = ((p.color >>> 24) & 255) >= 200;
        float fx = view.sine >= 0 ? p.x1 : p.x0;
        float fz = view.cosine >= 0 ? p.z1 : p.z0;
        face(draw, view, p.color, FACE_SHADE_SIDE * brightness, alphaMul, bevel,
            fx, p.y0, p.z0, fx, p.y0, p.z1, fx, p.y1, p.z1, fx, p.y1, p.z0);
        face(draw, view, p.color, FACE_SHADE_FRONT * brightness, alphaMul, bevel,
            p.x0, p.y0, fz, p.x1, p.y0, fz, p.x1, p.y1, fz, p.x0, p.y1, fz);
        face(draw, view, p.color, FACE_SHADE_TOP * brightness, alphaMul, bevel,
            p.x0, p.y1, p.z0, p.x1, p.y1, p.z0, p.x1, p.y1, p.z1, p.x0, p.y1, p.z1);
        if (highlightLevelIndex >= 0 && p.levelIndex == highlightLevelIndex) {
            // 选中层描边：顶面外轮廓，便于与亮度提升一起辨认。
            int outline = shade(0xffffffff, 1f, 0.85f);
            float x0 = view.x(p.x0, p.z0);
            float y0 = view.y(p.x0, p.y1, p.z0);
            float x1 = view.x(p.x1, p.z0);
            float y1 = view.y(p.x1, p.y1, p.z0);
            float x2 = view.x(p.x1, p.z1);
            float y2 = view.y(p.x1, p.y1, p.z1);
            float x3 = view.x(p.x0, p.z1);
            float y3 = view.y(p.x0, p.y1, p.z1);
            draw.addQuad(x0, y0, x1, y1, x2, y2, x3, y3, outline, 1.25f);
        }
    }

    private static void face(ImDrawList draw, View v, int argb, float shadeMul, float alphaMul, boolean bevel,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        float sx0 = v.x(x0, z0);
        float sy0 = v.y(x0, y0, z0);
        float sx1 = v.x(x1, z1);
        float sy1 = v.y(x1, y1, z1);
        float sx2 = v.x(x2, z2);
        float sy2 = v.y(x2, y2, z2);
        float sx3 = v.x(x3, z3);
        float sy3 = v.y(x3, y3, z3);
        draw.addQuadFilled(sx0, sy0, sx1, sy1, sx2, sy2, sx3, sy3, shade(argb, shadeMul, alphaMul));
        if (!bevel) {
            return;
        }
        // 对齐杆塔 DETAILED LOD：靠上的边高光、靠下的边阴影（高光/阴影在 ARGB 上算再转 ABGR）。
        float cy = (sy0 + sy1 + sy2 + sy3) * 0.25f;
        int hi = shade(liftArgb(argb, 28), shadeMul, alphaMul);
        int sh = shade(liftArgb(argb, -36), shadeMul, alphaMul);
        bevelEdge(draw, sx0, sy0, sx1, sy1, cy, hi, sh);
        bevelEdge(draw, sx1, sy1, sx2, sy2, cy, hi, sh);
        bevelEdge(draw, sx2, sy2, sx3, sy3, cy, hi, sh);
        bevelEdge(draw, sx3, sy3, sx0, sy0, cy, hi, sh);
    }

    private static void bevelEdge(ImDrawList draw, float ax, float ay, float bx, float by,
                                  float faceCenterY, int highlight, int shadow) {
        int color = (ay + by) * 0.5f <= faceCenterY ? highlight : shadow;
        draw.addLine(ax, ay, bx, by, color, EDGE_THICKNESS);
    }

    /** ARGB 通道加减，用于棱边高光/阴影。 */
    private static int liftArgb(int argb, int delta) {
        int a = (argb >>> 24) & 255;
        int r = Math.clamp(((argb >> 16) & 255) + delta, 0, 255);
        int g = Math.clamp(((argb >> 8) & 255) + delta, 0, 255);
        int b = Math.clamp((argb & 255) + delta, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record Part(float x0, float y0, float z0, float x1, float y1, float z1, int color, int levelIndex) {
        static Part at(BlockPos p, int color, int levelIndex,
                       float x0, float y0, float z0, float x1, float y1, float z1) {
            return new Part(p.getX() + x0, p.getY() + y0, p.getZ() + z0,
                p.getX() + x1, p.getY() + y1, p.getZ() + z1, color, levelIndex);
        }
    }

    private static int shade(int argb, float multiplier, float alphaMul) {
        int alpha = Math.clamp(Math.round(((argb >>> 24) & 255) * alphaMul), 0, 255);
        int red = Math.clamp(Math.round(((argb >> 16) & 255) * multiplier), 0, 255);
        int green = Math.clamp(Math.round(((argb >> 8) & 255) * multiplier), 0, 255);
        int blue = Math.clamp(Math.round((argb & 255) * multiplier), 0, 255);
        if (alpha >= 200) {
            red = Math.max(red, SHADE_RGB_FLOOR);
            green = Math.max(green, SHADE_RGB_FLOOR);
            blue = Math.max(blue, SHADE_RGB_FLOOR);
        }
        // ImGui 颜色为 ABGR 打包：保留 alpha，便于隐形光源半透明预览。
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private record View(float sine, float cosine, float scale, float ox, float oy) {
        float x(float x, float z) { return ox + scale * (x * cosine - z * sine); }
        float y(float x, float y, float z) { return oy + scale * (depth(x, z, sine, cosine) * 0.34f - y); }
    }
}
