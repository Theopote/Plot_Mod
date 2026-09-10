package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.TowerLocalPoint;

/** 横担造型放置逻辑（生成器与预览共用）。 */
public final class TowerArmPlacement {
    private static final double TAPERED_INNER_SCALE = 0.72;
    private static final double UPSWEEP_RISE = 1.5;

    private TowerArmPlacement() {
    }

    @FunctionalInterface
    public interface ChordPlacer {
        void place(
                double lateralStart,
                double lateralEnd,
                double height,
                double longHalf,
                MaterialMix material);
    }

    @FunctionalInterface
    public interface BracePlacer {
        void place(TowerLocalPoint start, TowerLocalPoint end, MaterialMix material);
    }

    public static void placeArm(
            TowerArm arm,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            ChordPlacer chordPlacer,
            BracePlacer bracePlacer) {
        if (arm == null || chordPlacer == null) {
            return;
        }
        double topHeight = arm.getBaseHeight();
        double bottomHeight = Math.max(0.0, topHeight - effectiveVerticalDrop(arm));
        double reach = arm.getLateralReach();
        double longHalf = Math.max(0.5, arm.getLongitudinalHalfWidth());
        BracingPattern bracing = effectiveBracing(arm);
        TowerArmSide side = arm.getSide();

        switch (side) {
            case BOTH -> placeSpan(
                -reach, reach, topHeight, bottomHeight, longHalf, arm.getShape(), bracing,
                chordMaterial, braceMaterial, chordPlacer, bracePlacer);
            case LEFT -> placeSpan(
                -reach, 0, topHeight, bottomHeight, longHalf, arm.getShape(), bracing,
                chordMaterial, braceMaterial, chordPlacer, bracePlacer);
            case RIGHT -> placeSpan(
                0, reach, topHeight, bottomHeight, longHalf, arm.getShape(), bracing,
                chordMaterial, braceMaterial, chordPlacer, bracePlacer);
            default -> { }
        }
    }

    private static double effectiveVerticalDrop(TowerArm arm) {
        if (arm.getVerticalDrop() > 0) {
            return arm.getVerticalDrop();
        }
        return switch (arm.getShape()) {
            case TRUSS, TAPERED -> 3.0;
            case UPSWEEP -> 2.0;
            default -> 0.0;
        };
    }

    private static BracingPattern effectiveBracing(TowerArm arm) {
        if (arm.getBracing() != BracingPattern.NONE) {
            return arm.getBracing();
        }
        return switch (arm.getShape()) {
            case TRUSS, TAPERED -> BracingPattern.X;
            default -> BracingPattern.NONE;
        };
    }

    private static void placeSpan(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            TowerArmShape shape,
            BracingPattern bracing,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            ChordPlacer chordPlacer,
            BracePlacer bracePlacer) {
        if (Math.abs(lateralEnd - lateralStart) < 1e-6) {
            return;
        }

        switch (shape) {
            case TAPERED -> placeTaperedSpan(
                lateralStart, lateralEnd, topHeight, bottomHeight, longHalf, bracing,
                chordMaterial, braceMaterial, chordPlacer, bracePlacer);
            case UPSWEEP -> placeUpsweepSpan(
                lateralStart, lateralEnd, topHeight, bottomHeight, longHalf, bracing,
                chordMaterial, braceMaterial, chordPlacer, bracePlacer);
            default -> placeFlatSpan(
                lateralStart, lateralEnd, topHeight, bottomHeight, longHalf, bracing,
                chordMaterial, braceMaterial, chordPlacer, bracePlacer);
        }
    }

    private static void placeFlatSpan(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            BracingPattern bracing,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            ChordPlacer chordPlacer,
            BracePlacer bracePlacer) {
        chordPlacer.place(lateralStart, lateralEnd, topHeight, longHalf, chordMaterial);
        if (bottomHeight + 1e-6 < topHeight) {
            chordPlacer.place(lateralStart, lateralEnd, bottomHeight, longHalf, chordMaterial);
            placeBracing(
                lateralStart, lateralEnd, topHeight, bottomHeight, longHalf, bracing,
                braceMaterial, bracePlacer);
        }
    }

    private static void placeTaperedSpan(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            BracingPattern bracing,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            ChordPlacer chordPlacer,
            BracePlacer bracePlacer) {
        double center = (lateralStart + lateralEnd) / 2.0;
        double halfSpan = Math.abs(lateralEnd - lateralStart) / 2.0;
        double innerHalf = halfSpan * TAPERED_INNER_SCALE;
        chordPlacer.place(lateralStart, lateralEnd, topHeight, longHalf, chordMaterial);
        chordPlacer.place(center - innerHalf, center + innerHalf, bottomHeight, longHalf, chordMaterial);
        if (bracePlacer != null && bracing != BracingPattern.NONE) {
            placeBracing(
                lateralStart, lateralEnd, topHeight, bottomHeight, longHalf, bracing,
                braceMaterial, bracePlacer);
        }
    }

    private static void placeUpsweepSpan(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            BracingPattern bracing,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            ChordPlacer chordPlacer,
            BracePlacer bracePlacer) {
        double center = (lateralStart + lateralEnd) / 2.0;
        double endHeight = topHeight + UPSWEEP_RISE;
        placeUpsweepChord(lateralStart, center, endHeight, topHeight, longHalf, chordMaterial, chordPlacer);
        placeUpsweepChord(center, lateralEnd, topHeight, endHeight, longHalf, chordMaterial, chordPlacer);
        if (bottomHeight + 1e-6 < topHeight) {
            chordPlacer.place(lateralStart, lateralEnd, bottomHeight, longHalf, chordMaterial);
            placeBracing(
                lateralStart, lateralEnd, topHeight, bottomHeight, longHalf, bracing,
                braceMaterial, bracePlacer);
        }
    }

    private static void placeUpsweepChord(
            double lateralStart,
            double lateralEnd,
            double heightStart,
            double heightEnd,
            double longHalf,
            MaterialMix material,
            ChordPlacer chordPlacer) {
        if (longHalf <= 0) {
            return;
        }
        chordPlacer.place(lateralStart, lateralEnd, heightStart, longHalf, material);
        if (Math.abs(heightStart - heightEnd) > 1e-6) {
            // Approximate upsweep by placing a second rail at the opposite elevation.
            chordPlacer.place(lateralStart, lateralEnd, heightEnd, longHalf, material);
        }
    }

    private static void placeBracing(
            double lateralStart,
            double lateralEnd,
            double topHeight,
            double bottomHeight,
            double longHalf,
            BracingPattern pattern,
            MaterialMix braceMaterial,
            BracePlacer bracePlacer) {
        if (pattern == BracingPattern.NONE || bracePlacer == null || braceMaterial == null) {
            return;
        }
        double[] longitudes = longHalf <= 0 ? new double[] {0.0} : new double[] {-longHalf, longHalf};
        for (double longitudinal : longitudes) {
            TowerLocalPoint topLeft = TowerLocalPoint.of(lateralStart, topHeight, longitudinal);
            TowerLocalPoint topRight = TowerLocalPoint.of(lateralEnd, topHeight, longitudinal);
            TowerLocalPoint bottomLeft = TowerLocalPoint.of(lateralStart, bottomHeight, longitudinal);
            TowerLocalPoint bottomRight = TowerLocalPoint.of(lateralEnd, bottomHeight, longitudinal);

            if (pattern == BracingPattern.X) {
                bracePlacer.place(bottomLeft, topRight, braceMaterial);
                bracePlacer.place(bottomRight, topLeft, braceMaterial);
            } else if (pattern == BracingPattern.K) {
                TowerLocalPoint centerTop = midpoint(topLeft, topRight);
                bracePlacer.place(bottomLeft, centerTop, braceMaterial);
                bracePlacer.place(bottomRight, centerTop, braceMaterial);
            } else if (pattern == BracingPattern.SINGLE_DIAGONAL) {
                bracePlacer.place(bottomLeft, topRight, braceMaterial);
            } else if (pattern == BracingPattern.V) {
                TowerLocalPoint centerLower = TowerLocalPoint.of(
                    (bottomLeft.lateral() + bottomRight.lateral()) / 2.0,
                    bottomHeight,
                    longitudinal);
                bracePlacer.place(topLeft, centerLower, braceMaterial);
                bracePlacer.place(topRight, centerLower, braceMaterial);
            }
        }
    }

    private static TowerLocalPoint midpoint(TowerLocalPoint a, TowerLocalPoint b) {
        return TowerLocalPoint.of(
            (a.lateral() + b.lateral()) / 2.0,
            (a.vertical() + b.vertical()) / 2.0,
            (a.longitudinal() + b.longitudinal()) / 2.0);
    }
}
