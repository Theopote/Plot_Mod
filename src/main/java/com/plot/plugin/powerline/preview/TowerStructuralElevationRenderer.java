package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmShape;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import imgui.ImDrawList;

import java.util.List;

/**
 * 大型塔体结构立面预览：由 {@link TowerStructureDesign} 直接投影为 ImGui 线条，
 * 保持完整真实比例，不经过体素化。
 */
public final class TowerStructuralElevationRenderer {
    private static final float PADDING = 4f;
    private static final float LEG_THICKNESS = 2.2f;
    private static final float ARM_THICKNESS = 1.7f;
    private static final float BRACE_THICKNESS = 1.1f;
    private static final float PEAK_THICKNESS = 1.5f;

    private static final int COLOR_LEG = 0xFFB0BEC5;
    private static final int COLOR_BRACE = 0xFF78909C;
    private static final int COLOR_ARM = 0xFF90A4AE;
    private static final int COLOR_PEAK = 0xFFECEFF1;

    private TowerStructuralElevationRenderer() {
    }

    public static final class StructuralLayout {
        private final float scale;
        private final float centerX;
        private final float baseY;

        StructuralLayout(float scale, float centerX, float baseY) {
            this.scale = scale;
            this.centerX = centerX;
            this.baseY = baseY;
        }

        public float scale() {
            return scale;
        }

        public float centerX() {
            return centerX;
        }

        public float baseY() {
            return baseY;
        }

        public float mapX(double lateral) {
            return centerX + (float) (lateral * scale);
        }

        public float mapY(double height) {
            return baseY - (float) (height * scale);
        }
    }

    public static boolean drawFront(ImDrawList drawList, PoleDesign design, float x0, float y0, float x1, float y1) {
        if (drawList == null || design == null || !design.hasTowerStructure()) {
            return false;
        }
        TowerStructureDesign structure = design.getTowerStructure();
        StructuralLayout layout = computeLayout(structure, x0, y0, x1, y1);
        if (layout == null) {
            return false;
        }
        drawList.addRectFilled(x0, y0, x1, y1, 0xFF141414);
        drawLegs(drawList, structure, layout);
        drawBays(drawList, structure, layout);
        drawArms(drawList, structure, layout);
        drawPeak(drawList, structure, layout);
        return true;
    }

    public static StructuralLayout computeLayout(
            TowerStructureDesign structure,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (structure == null) {
            return null;
        }
        List<TowerStation> stations = structure.sortedStations();
        if (stations.isEmpty()) {
            return null;
        }
        double maxHeight = structure.maxHeight();
        if (maxHeight <= 0) {
            maxHeight = stations.get(stations.size() - 1).getHeight();
        }
        double maxHalfWidth = 1.0;
        for (TowerStation station : stations) {
            maxHalfWidth = Math.max(maxHalfWidth, station.getHalfWidth());
        }
        for (TowerArm arm : structure.getArms()) {
            maxHalfWidth = Math.max(maxHalfWidth, arm.getLateralReach());
        }
        float availW = Math.max(1f, x1 - x0 - PADDING * 2f);
        float availH = Math.max(1f, y1 - y0 - PADDING * 2f);
        float scale = Math.min(availW / (float) (maxHalfWidth * 2.0), availH / (float) maxHeight);
        return new StructuralLayout(scale, (x0 + x1) * 0.5f, y1 - PADDING);
    }

    public static StructuralLayout computeLayout(PoleDesign design, float x0, float y0, float x1, float y1) {
        if (design == null || !design.hasTowerStructure()) {
            return null;
        }
        return computeLayout(design.getTowerStructure(), x0, y0, x1, y1);
    }

    private static void drawLegs(ImDrawList drawList, TowerStructureDesign structure, StructuralLayout layout) {
        List<TowerStation> stations = structure.sortedStations();
        for (int i = 1; i < stations.size(); i++) {
            TowerStation lower = stations.get(i - 1);
            TowerStation upper = stations.get(i);
            segment(drawList, layout, -lower.getHalfWidth(), lower.getHeight(), -upper.getHalfWidth(), upper.getHeight(), COLOR_LEG, LEG_THICKNESS);
            segment(drawList, layout, lower.getHalfWidth(), lower.getHeight(), upper.getHalfWidth(), upper.getHeight(), COLOR_LEG, LEG_THICKNESS);
        }
    }

    private static void drawBays(ImDrawList drawList, TowerStructureDesign structure, StructuralLayout layout) {
        for (TowerBay bay : structure.getBays()) {
            TowerStation lower = structure.findStation(bay.getLowerStationId());
            TowerStation upper = structure.findStation(bay.getUpperStationId());
            if (lower == null || upper == null) {
                continue;
            }
            BracingPattern pattern = bay.getFrontBackBracing();
            if (pattern == BracingPattern.NONE) {
                continue;
            }
            double xLL = -lower.getHalfWidth();
            double xLR = lower.getHalfWidth();
            double xUL = -upper.getHalfWidth();
            double xUR = upper.getHalfWidth();
            double yL = lower.getHeight();
            double yU = upper.getHeight();
            switch (pattern) {
                case X -> {
                    segment(drawList, layout, xLL, yL, xUR, yU, COLOR_BRACE, BRACE_THICKNESS);
                    segment(drawList, layout, xLR, yL, xUL, yU, COLOR_BRACE, BRACE_THICKNESS);
                }
                case SINGLE_DIAGONAL -> segment(drawList, layout, xLL, yL, xUR, yU, COLOR_BRACE, BRACE_THICKNESS);
                case K -> {
                    double midX = 0.0;
                    segment(drawList, layout, xLL, yL, midX, yU, COLOR_BRACE, BRACE_THICKNESS);
                    segment(drawList, layout, xLR, yL, midX, yU, COLOR_BRACE, BRACE_THICKNESS);
                }
                case V -> {
                    double midX = 0.0;
                    segment(drawList, layout, xUL, yU, midX, yL, COLOR_BRACE, BRACE_THICKNESS);
                    segment(drawList, layout, xUR, yU, midX, yL, COLOR_BRACE, BRACE_THICKNESS);
                }
                default -> { }
            }
        }
    }

    private static void drawArms(ImDrawList drawList, TowerStructureDesign structure, StructuralLayout layout) {
        List<TowerArm> arms = structure.getArms().stream()
            .sorted(java.util.Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
        for (TowerArm arm : arms) {
            drawArm(drawList, layout, arm);
        }
    }

    private static void drawArm(ImDrawList drawList, StructuralLayout layout, TowerArm arm) {
        double reach = arm.getLateralReach();
        double y = arm.getBaseHeight();
        double drop = Math.max(1.0, arm.getVerticalDrop());
        TowerArmShape shape = arm.getShape() != null ? arm.getShape() : TowerArmShape.FLAT;
        switch (shape) {
            case FLAT -> segment(drawList, layout, -reach, y, reach, y, COLOR_ARM, ARM_THICKNESS);
            case TRUSS -> {
                segment(drawList, layout, -reach, y, reach, y, COLOR_ARM, ARM_THICKNESS);
                double midY = y - drop * 0.55;
                segment(drawList, layout, -reach * 0.85, midY, 0, y, COLOR_BRACE, BRACE_THICKNESS);
                segment(drawList, layout, reach * 0.85, midY, 0, y, COLOR_BRACE, BRACE_THICKNESS);
                segment(drawList, layout, -reach * 0.85, midY, reach * 0.85, midY, COLOR_BRACE, BRACE_THICKNESS * 0.9f);
            }
            case TAPERED -> {
                double inner = reach * 0.72;
                double lowerY = y - drop * 0.65;
                segment(drawList, layout, -reach, y, reach, y, COLOR_ARM, ARM_THICKNESS);
                segment(drawList, layout, -reach, y, -inner, lowerY, COLOR_BRACE, BRACE_THICKNESS);
                segment(drawList, layout, reach, y, inner, lowerY, COLOR_BRACE, BRACE_THICKNESS);
                segment(drawList, layout, -inner, lowerY, inner, lowerY, COLOR_ARM, ARM_THICKNESS * 0.9f);
            }
            case UPSWEEP -> {
                double inner = reach * 0.78;
                double upperY = y + drop * 0.45;
                segment(drawList, layout, -inner, y, inner, y, COLOR_ARM, ARM_THICKNESS);
                segment(drawList, layout, -inner, y, -reach, upperY, COLOR_BRACE, BRACE_THICKNESS);
                segment(drawList, layout, inner, y, reach, upperY, COLOR_BRACE, BRACE_THICKNESS);
                segment(drawList, layout, -reach, upperY, reach, upperY, COLOR_ARM, ARM_THICKNESS);
            }
            default -> segment(drawList, layout, -reach, y, reach, y, COLOR_ARM, ARM_THICKNESS);
        }
    }

    private static void drawPeak(ImDrawList drawList, TowerStructureDesign structure, StructuralLayout layout) {
        for (TowerDecoration decoration : structure.getDecorations()) {
            if (decoration == null || !decoration.isEnabled()) {
                continue;
            }
            double baseHeight = decoration.getBaseHeight();
            double size = Math.max(1.0, decoration.getSize());
            float x = layout.mapX(decoration.getLateralOffset());
            float yTop = layout.mapY(baseHeight + size);
            float yBase = layout.mapY(baseHeight);
            drawList.addLine(x, yBase, x, yTop, COLOR_PEAK, PEAK_THICKNESS);
            drawList.addLine(x - 3f, yTop + 2f, x, yTop - 4f, COLOR_PEAK, PEAK_THICKNESS);
            drawList.addLine(x + 3f, yTop + 2f, x, yTop - 4f, COLOR_PEAK, PEAK_THICKNESS);
        }
    }

    private static void segment(
            ImDrawList drawList,
            StructuralLayout layout,
            double x0,
            double y0,
            double x1,
            double y1,
            int color,
            float thickness) {
        drawList.addLine(layout.mapX(x0), layout.mapY(y0), layout.mapX(x1), layout.mapY(y1), color, thickness);
    }
}
