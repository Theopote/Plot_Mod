package com.plot.plugin.powerline.preview;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmShape;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
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

    private static final int FALLBACK_LEG = 0xFFB0BEC5;
    private static final int FALLBACK_BRACE = 0xFF78909C;
    private static final int FALLBACK_ARM = 0xFF90A4AE;
    private static final int FALLBACK_ANTENNA = 0xFFECEFF1;
    private static final int COLOR_BEACON = 0xFF81D4FA;
    private static final int COLOR_WARNING_LIGHT = 0xFFFFF59D;
    private static final int COLOR_PLATFORM = 0xFF90A4AE;
    private static final double TAPERED_INNER_SCALE = 0.72;
    private static final double UPSWEEP_INNER_SCALE = 0.78;

    private TowerStructuralElevationRenderer() {
    }

    /** 结构线预览配色：来自塔体 {@link MaterialMix}，保留材质身份。 */
    record StructuralPalette(int leg, int brace, int defaultArm) {
        static StructuralPalette from(TowerStructureDesign structure) {
            return new StructuralPalette(
                BlockPreviewColors.previewColor(structure.getPrimaryMaterial(), FALLBACK_LEG),
                BlockPreviewColors.previewColor(structure.getBraceMaterial(), FALLBACK_BRACE),
                BlockPreviewColors.previewColor(structure.getBraceMaterial(), FALLBACK_ARM));
        }

        int armColor(TowerArm arm) {
            MaterialMix material = arm != null ? arm.getMaterial() : null;
            return BlockPreviewColors.previewColor(material, defaultArm);
        }

        int decorationColor(TowerDecoration decoration, int fallback) {
            MaterialMix material = decoration != null ? decoration.getMaterial() : null;
            return BlockPreviewColors.previewColor(material, fallback);
        }
    }

    /** 横担正视投影的横向范围，与 {@link com.plot.plugin.powerline.design.structure.TowerArmPlacement} 一致。 */
    record ArmLateralSpan(double start, double end) {
        double center() {
            return (start + end) * 0.5;
        }

        double halfSpan() {
            return Math.abs(end - start) * 0.5;
        }

        boolean includesNegativeSide() {
            return start < -1e-6;
        }

        boolean includesPositiveSide() {
            return end > 1e-6;
        }
    }

    static ArmLateralSpan lateralSpan(TowerArm arm) {
        double reach = arm.getLateralReach();
        return switch (arm.getSide()) {
            case LEFT -> new ArmLateralSpan(-reach, 0.0);
            case RIGHT -> new ArmLateralSpan(0.0, reach);
            default -> new ArmLateralSpan(-reach, reach);
        };
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
        StructuralPalette palette = StructuralPalette.from(structure);
        drawLegs(drawList, structure, layout, palette);
        drawBays(drawList, structure, layout, palette);
        drawArms(drawList, structure, layout, palette);
        drawDecorations(drawList, structure, layout, palette);
        return true;
    }

    /** 预览中会绘制的装饰类型；未知类型跳过，避免误画成塔尖。 */
    static boolean isRenderableDecorationKind(TowerDecorationKind kind) {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case ANTENNA, BEACON, WARNING_LIGHT, PLATFORM -> true;
        };
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
            maxHeight = stations.getLast().getHeight();
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

    private static void drawLegs(
            ImDrawList drawList,
            TowerStructureDesign structure,
            StructuralLayout layout,
            StructuralPalette palette) {
        List<TowerStation> stations = structure.sortedStations();
        for (int i = 1; i < stations.size(); i++) {
            TowerStation lower = stations.get(i - 1);
            TowerStation upper = stations.get(i);
            segment(drawList, layout, -lower.getHalfWidth(), lower.getHeight(), -upper.getHalfWidth(), upper.getHeight(), palette.leg, LEG_THICKNESS);
            segment(drawList, layout, lower.getHalfWidth(), lower.getHeight(), upper.getHalfWidth(), upper.getHeight(), palette.leg, LEG_THICKNESS);
        }
    }

    private static void drawBays(
            ImDrawList drawList,
            TowerStructureDesign structure,
            StructuralLayout layout,
            StructuralPalette palette) {
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
                    segment(drawList, layout, xLL, yL, xUR, yU, palette.brace, BRACE_THICKNESS);
                    segment(drawList, layout, xLR, yL, xUL, yU, palette.brace, BRACE_THICKNESS);
                }
                case SINGLE_DIAGONAL -> segment(drawList, layout, xLL, yL, xUR, yU, palette.brace, BRACE_THICKNESS);
                case K -> {
                    double midX = 0.0;
                    segment(drawList, layout, xLL, yL, midX, yU, palette.brace, BRACE_THICKNESS);
                    segment(drawList, layout, xLR, yL, midX, yU, palette.brace, BRACE_THICKNESS);
                }
                case V -> {
                    double midX = 0.0;
                    segment(drawList, layout, xUL, yU, midX, yL, palette.brace, BRACE_THICKNESS);
                    segment(drawList, layout, xUR, yU, midX, yL, palette.brace, BRACE_THICKNESS);
                }
                default -> { }
            }
        }
    }

    private static void drawArms(
            ImDrawList drawList,
            TowerStructureDesign structure,
            StructuralLayout layout,
            StructuralPalette palette) {
        List<TowerArm> arms = structure.getArms().stream()
            .sorted(java.util.Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
        for (TowerArm arm : arms) {
            drawArm(drawList, layout, palette, arm);
        }
    }

    private static void drawArm(ImDrawList drawList, StructuralLayout layout, StructuralPalette palette, TowerArm arm) {
        ArmLateralSpan span = lateralSpan(arm);
        if (span.halfSpan() < 1e-6) {
            return;
        }
        double reach = arm.getLateralReach();
        double y = arm.getBaseHeight();
        double drop = Math.max(1.0, arm.getVerticalDrop());
        int armColor = palette.armColor(arm);
        TowerArmShape shape = arm.getShape() != null ? arm.getShape() : TowerArmShape.FLAT;
        switch (shape) {
            case FLAT -> segment(drawList, layout, span.start, y, span.end, y, armColor, ARM_THICKNESS);
            case TRUSS -> drawTrussArm(drawList, layout, palette, span, reach, y, drop, armColor);
            case TAPERED -> drawTaperedArm(drawList, layout, palette, span, y, drop, armColor);
            case UPSWEEP -> drawUpsweepArm(drawList, layout, palette, span, reach, y, drop, armColor);
            default -> segment(drawList, layout, span.start, y, span.end, y, armColor, ARM_THICKNESS);
        }
    }

    private static void drawTrussArm(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            ArmLateralSpan span,
            double reach,
            double y,
            double drop,
            int armColor) {
        segment(drawList, layout, span.start, y, span.end, y, armColor, ARM_THICKNESS);
        double midY = y - drop * 0.55;
        if (span.includesNegativeSide()) {
            double braceX = -reach * 0.85;
            segment(drawList, layout, braceX, midY, 0, y, palette.brace, BRACE_THICKNESS);
        }
        if (span.includesPositiveSide()) {
            double braceX = reach * 0.85;
            segment(drawList, layout, braceX, midY, 0, y, palette.brace, BRACE_THICKNESS);
        }
        double bottomStart = span.includesNegativeSide() ? -reach * 0.85 : 0.0;
        double bottomEnd = span.includesPositiveSide() ? reach * 0.85 : 0.0;
        if (Math.abs(bottomEnd - bottomStart) > 1e-6) {
            segment(drawList, layout, bottomStart, midY, bottomEnd, midY, palette.brace, BRACE_THICKNESS * 0.9f);
        }
    }

    private static void drawTaperedArm(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            ArmLateralSpan span,
            double y,
            double drop,
            int armColor) {
        double innerHalf = span.halfSpan() * TAPERED_INNER_SCALE;
        double center = span.center();
        double innerStart = center - innerHalf;
        double innerEnd = center + innerHalf;
        double lowerY = y - drop * 0.65;
        segment(drawList, layout, span.start, y, span.end, y, armColor, ARM_THICKNESS);
        if (span.includesNegativeSide()) {
            segment(drawList, layout, span.start, y, innerStart, lowerY, palette.brace, BRACE_THICKNESS);
        }
        if (span.includesPositiveSide()) {
            segment(drawList, layout, span.end, y, innerEnd, lowerY, palette.brace, BRACE_THICKNESS);
        }
        segment(drawList, layout, innerStart, lowerY, innerEnd, lowerY, armColor, ARM_THICKNESS * 0.9f);
    }

    private static void drawUpsweepArm(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            ArmLateralSpan span,
            double reach,
            double y,
            double drop,
            int armColor) {
        double innerHalf = span.halfSpan() * UPSWEEP_INNER_SCALE;
        double center = span.center();
        double innerStart = center - innerHalf;
        double innerEnd = center + innerHalf;
        double upperY = y + drop * 0.45;
        segment(drawList, layout, innerStart, y, innerEnd, y, armColor, ARM_THICKNESS);
        if (span.includesNegativeSide()) {
            double outerX = span.start;
            segment(drawList, layout, innerStart, y, outerX, upperY, palette.brace, BRACE_THICKNESS);
            segment(drawList, layout, outerX, upperY, center, upperY, armColor, ARM_THICKNESS);
        }
        if (span.includesPositiveSide()) {
            double outerX = span.end;
            segment(drawList, layout, innerEnd, y, outerX, upperY, palette.brace, BRACE_THICKNESS);
            segment(drawList, layout, center, upperY, outerX, upperY, armColor, ARM_THICKNESS);
        }
        if (span.includesNegativeSide() && span.includesPositiveSide()) {
            segment(drawList, layout, -reach, upperY, reach, upperY, armColor, ARM_THICKNESS);
        }
    }

    private static void drawDecorations(
            ImDrawList drawList,
            TowerStructureDesign structure,
            StructuralLayout layout,
            StructuralPalette palette) {
        for (TowerDecoration decoration : structure.getDecorations()) {
            if (decoration == null || !decoration.isEnabled()) {
                continue;
            }
            TowerDecorationKind kind = decoration.getKind();
            if (!isRenderableDecorationKind(kind)) {
                continue;
            }
            switch (kind) {
                case ANTENNA -> drawAntennaDecoration(drawList, layout, palette, decoration);
                case BEACON -> drawBeaconDecoration(drawList, layout, palette, decoration);
                case WARNING_LIGHT -> drawWarningLightDecoration(drawList, layout, palette, decoration);
                case PLATFORM -> drawPlatformDecoration(drawList, layout, palette, decoration);
                default -> { }
            }
        }
    }

    private static void drawAntennaDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration) {
        int color = palette.decorationColor(decoration, FALLBACK_ANTENNA);
        double baseHeight = decoration.getBaseHeight();
        double mastHeight = Math.max(2.0, decoration.getSize());
        float x = layout.mapX(decoration.getLateralOffset());
        float yTop = layout.mapY(baseHeight + mastHeight);
        float yBase = layout.mapY(baseHeight);
        drawList.addLine(x, yBase, x, yTop, color, PEAK_THICKNESS);
        drawList.addLine(x - 3f, yTop + 2f, x, yTop - 4f, color, PEAK_THICKNESS);
        drawList.addLine(x + 3f, yTop + 2f, x, yTop - 4f, color, PEAK_THICKNESS);
    }

    private static void drawBeaconDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration) {
        int color = palette.decorationColor(decoration, COLOR_BEACON);
        float x = layout.mapX(decoration.getLateralOffset());
        float y = layout.mapY(decoration.getBaseHeight() + 1.0);
        float half = 2.5f;
        drawList.addRectFilled(x - half, y - half, x + half, y + half, color);
    }

    private static void drawWarningLightDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration) {
        int color = palette.decorationColor(decoration, COLOR_WARNING_LIGHT);
        float x = layout.mapX(decoration.getLateralOffset());
        float y = layout.mapY(decoration.getBaseHeight() + 1.0);
        drawList.addCircleFilled(x, y, 2.2f, color);
    }

    private static void drawPlatformDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration) {
        int color = palette.decorationColor(decoration, palette.defaultArm);
        double halfWidth = Math.max(1.0, decoration.getSize());
        double y = decoration.getBaseHeight();
        double lateral = decoration.getLateralOffset();
        segment(
            drawList,
            layout,
            lateral - halfWidth,
            y,
            lateral + halfWidth,
            y,
            color,
            ARM_THICKNESS * 0.85f);
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
