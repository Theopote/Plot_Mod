package com.plot.plugin.powerline.preview;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
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
    private static final float PADDING = 8f;
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

    public enum StructuralView {
        FRONT,
        SIDE
    }

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
        return draw(drawList, design, StructuralView.FRONT, x0, y0, x1, y1);
    }

    public static boolean drawSide(ImDrawList drawList, PoleDesign design, float x0, float y0, float x1, float y1) {
        return draw(drawList, design, StructuralView.SIDE, x0, y0, x1, y1);
    }

    private static boolean draw(
            ImDrawList drawList,
            PoleDesign design,
            StructuralView view,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (drawList == null || design == null || !design.hasTowerStructure()) {
            return false;
        }
        TowerStructureDesign structure = design.getTowerStructure();
        StructuralLayout layout = computeLayout(design, view, x0, y0, x1, y1);
        if (layout == null) {
            return false;
        }
        drawList.addRectFilled(x0, y0, x1, y1, 0xFF141414);
        StructuralPalette palette = StructuralPalette.from(structure);
        drawLegs(drawList, structure, layout, palette, view);
        drawBays(drawList, structure, layout, palette, view);
        drawArms(drawList, structure, layout, palette, view);
        drawDecorations(drawList, structure, layout, palette, view);
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
        return computeLayout(structure, StructuralView.FRONT, x0, y0, x1, y1);
    }

    public static StructuralLayout computeLayout(
            TowerStructureDesign structure,
            StructuralView view,
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
            maxHalfWidth = Math.max(
                maxHalfWidth,
                view == StructuralView.FRONT ? station.getHalfWidth() : station.getHalfDepth());
        }
        for (TowerArm arm : structure.getArms()) {
            maxHalfWidth = Math.max(
                maxHalfWidth,
                view == StructuralView.FRONT ? arm.getLateralReach() : arm.getLongitudinalHalfWidth());
        }
        for (TowerDecoration decoration : structure.getDecorations()) {
            if (decoration == null || !decoration.isEnabled()) {
                continue;
            }
            double offset = Math.abs(decorationOffset(decoration, view));
            double halfSize = decoration.getKind() == TowerDecorationKind.PLATFORM ? decoration.getSize() : 0.0;
            maxHalfWidth = Math.max(maxHalfWidth, offset + halfSize);
            if (decoration.getKind() == TowerDecorationKind.ANTENNA) {
                maxHeight = Math.max(maxHeight, decoration.getBaseHeight() + Math.max(2.0, decoration.getSize()));
            } else {
                maxHeight = Math.max(maxHeight, decoration.getBaseHeight() + 3.5);
            }
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

    public static StructuralLayout computeLayout(
            PoleDesign design,
            StructuralView view,
            float x0,
            float y0,
            float x1,
            float y1) {
        if (design == null || !design.hasTowerStructure()) {
            return null;
        }
        TowerStructureDesign structure = design.getTowerStructure();
        StructuralLayout structureLayout = computeLayout(structure, view, x0, y0, x1, y1);
        if (structureLayout == null) {
            return null;
        }
        double maxHalfSpan = 1.0;
        double maxHeight = structure.maxHeight();
        for (TowerStation station : structure.sortedStations()) {
            maxHalfSpan = Math.max(maxHalfSpan, stationHalfSpan(station, view));
        }
        for (TowerArm arm : structure.getArms()) {
            maxHalfSpan = Math.max(
                maxHalfSpan,
                view == StructuralView.FRONT ? arm.getLateralReach() : arm.getLongitudinalHalfWidth());
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            TowerArmAttachmentBinding.ResolvedLocalOffsets local =
                TowerArmAttachmentBinding.resolveLocalOffsets(attachment, structure);
            double offset = view == StructuralView.FRONT
                ? local.lateral()
                : local.longitudinal();
            maxHalfSpan = Math.max(maxHalfSpan, Math.abs(offset) + 0.75);
            maxHeight = Math.max(maxHeight, local.vertical() + 0.75);
        }
        if (maxHalfSpan <= 1.0 && maxHeight <= 0.0) {
            return structureLayout;
        }
        float availW = Math.max(1f, x1 - x0 - PADDING * 2f);
        float availH = Math.max(1f, y1 - y0 - PADDING * 2f);
        float scale = Math.min(availW / (float) (maxHalfSpan * 2.0), availH / (float) maxHeight);
        return new StructuralLayout(scale, (x0 + x1) * 0.5f, y1 - PADDING);
    }

    private static void drawLegs(
            ImDrawList drawList,
            TowerStructureDesign structure,
            StructuralLayout layout,
            StructuralPalette palette,
            StructuralView view) {
        List<TowerStation> stations = structure.sortedStations();
        for (int i = 1; i < stations.size(); i++) {
            TowerStation lower = stations.get(i - 1);
            TowerStation upper = stations.get(i);
            double lowerHalfSpan = stationHalfSpan(lower, view);
            double upperHalfSpan = stationHalfSpan(upper, view);
            segment(drawList, layout, -lowerHalfSpan, lower.getHeight(), -upperHalfSpan, upper.getHeight(), palette.leg, LEG_THICKNESS);
            segment(drawList, layout, lowerHalfSpan, lower.getHeight(), upperHalfSpan, upper.getHeight(), palette.leg, LEG_THICKNESS);
        }
    }

    private static void drawBays(
            ImDrawList drawList,
            TowerStructureDesign structure,
            StructuralLayout layout,
            StructuralPalette palette,
            StructuralView view) {
        for (TowerBay bay : structure.getBays()) {
            TowerStation lower = structure.findStation(bay.getLowerStationId());
            TowerStation upper = structure.findStation(bay.getUpperStationId());
            if (lower == null || upper == null) {
                continue;
            }
            BracingPattern pattern = view == StructuralView.FRONT
                ? bay.getFrontBackBracing()
                : bay.getSideBracing();
            if (pattern == BracingPattern.NONE) {
                continue;
            }
            double xLL = -stationHalfSpan(lower, view);
            double xLR = stationHalfSpan(lower, view);
            double xUL = -stationHalfSpan(upper, view);
            double xUR = stationHalfSpan(upper, view);
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
            StructuralPalette palette,
            StructuralView view) {
        List<TowerArm> arms = structure.getArms().stream()
            .sorted(java.util.Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
        for (TowerArm arm : arms) {
            if (view == StructuralView.FRONT) {
                drawArm(drawList, layout, palette, arm);
            } else {
                drawSideArm(drawList, layout, palette, arm);
            }
        }
    }

    private static void drawSideArm(ImDrawList drawList, StructuralLayout layout, StructuralPalette palette, TowerArm arm) {
        double halfWidth = arm.getLongitudinalHalfWidth();
        if (halfWidth < 1e-6) {
            return;
        }
        segment(
            drawList,
            layout,
            -halfWidth,
            arm.getBaseHeight(),
            halfWidth,
            arm.getBaseHeight(),
            palette.armColor(arm),
            ARM_THICKNESS);
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
            StructuralPalette palette,
            StructuralView view) {
        for (TowerDecoration decoration : structure.getDecorations()) {
            if (decoration == null || !decoration.isEnabled()) {
                continue;
            }
            TowerDecorationKind kind = decoration.getKind();
            if (!isRenderableDecorationKind(kind)) {
                continue;
            }
            switch (kind) {
                case ANTENNA -> drawAntennaDecoration(drawList, layout, palette, decoration, view);
                case BEACON -> drawBeaconDecoration(drawList, layout, palette, decoration, view);
                case WARNING_LIGHT -> drawWarningLightDecoration(drawList, layout, palette, decoration, view);
                case PLATFORM -> drawPlatformDecoration(drawList, layout, palette, decoration, view);
                default -> { }
            }
        }
    }

    private static void drawAntennaDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration,
            StructuralView view) {
        int color = palette.decorationColor(decoration, FALLBACK_ANTENNA);
        double baseHeight = decoration.getBaseHeight();
        double mastHeight = Math.max(2.0, decoration.getSize());
        float x = layout.mapX(decorationOffset(decoration, view));
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
            TowerDecoration decoration,
            StructuralView view) {
        int color = palette.decorationColor(decoration, COLOR_BEACON);
        float x = layout.mapX(decorationOffset(decoration, view));
        float y = layout.mapY(decoration.getBaseHeight() + 1.0);
        float half = 2.5f;
        drawList.addRectFilled(x - half, y - half, x + half, y + half, color);
    }

    private static void drawWarningLightDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration,
            StructuralView view) {
        int color = palette.decorationColor(decoration, COLOR_WARNING_LIGHT);
        float x = layout.mapX(decorationOffset(decoration, view));
        float y = layout.mapY(decoration.getBaseHeight() + 1.0);
        drawList.addCircleFilled(x, y, 2.2f, color);
    }

    private static void drawPlatformDecoration(
            ImDrawList drawList,
            StructuralLayout layout,
            StructuralPalette palette,
            TowerDecoration decoration,
            StructuralView view) {
        int color = palette.decorationColor(decoration, palette.defaultArm);
        double halfWidth = Math.max(1.0, decoration.getSize());
        double y = decoration.getBaseHeight();
        double lateral = decorationOffset(decoration, view);
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

    private static double stationHalfSpan(TowerStation station, StructuralView view) {
        return view == StructuralView.FRONT ? station.getHalfWidth() : station.getHalfDepth();
    }

    private static double decorationOffset(TowerDecoration decoration, StructuralView view) {
        return view == StructuralView.FRONT ? decoration.getLateralOffset() : decoration.getLongitudinalOffset();
    }
}
