package com.plot.plugin.road.tunnel;

/**
 * Shape-specific tunnel cavity membership and inner/outer contour heights.
 * <p>
 * Road pavement at {@code roadY} is owned by the road generator; invert arches may
 * exist below the road datum for CIRCULAR / ELLIPTICAL only.
 */
public final class TunnelProfile {
    private TunnelProfile() {
    }

    /**
     * Derived invert (仰拱) depth below {@code roadY}. Zero for shapes without a lower arc.
     */
    public static int derivedInvertDepth(TunnelShape shape, int halfCavityWidth, int clearHeight) {
        if (shape == null || halfCavityWidth <= 0 || clearHeight <= 0) {
            return 0;
        }
        return switch (shape) {
            case CIRCULAR -> {
                int radius = circularRadius(halfCavityWidth);
                int clear = effectiveCircularClearHeight(halfCavityWidth, clearHeight);
                // Bottom of circle is centerY - radius = roadY + clear - 2*radius.
                yield Math.max(0, 2 * radius - clear);
            }
            // Deeper invert than a diameter-matched circle so ELLIPTICAL stays visually oval.
            // Clamp to half-width so narrow cavities (halfW < 2) stay legal.
            case ELLIPTICAL -> {
                int desired = Math.max(2, clearHeight / 2 + 1);
                yield Math.min(halfCavityWidth, desired);
            }
            default -> 0;
        };
    }

    /**
     * @param lateral signed offset from road center in blocks
     * @param y world elevation
     * @param roadY road surface elevation
     * @param halfCavityWidth half of interior cavity width in blocks
     * @param clearHeight interior clearance above road surface
     */
    public static boolean isCavityAir(
            TunnelShape shape,
            int lateral,
            int y,
            int roadY,
            int halfCavityWidth,
            int clearHeight) {
        return containsInner(shape, lateral, y, roadY, halfCavityWidth, clearHeight);
    }

    /** True when the cell lies inside the interior (air) contour. */
    public static boolean containsInner(
            TunnelShape shape,
            int lateral,
            int y,
            int roadY,
            int halfCavityWidth,
            int clearHeight) {
        if (shape == null || halfCavityWidth < 0 || clearHeight <= 0) {
            return false;
        }
        int invert = derivedInvertDepth(shape, halfCavityWidth, clearHeight);
        int floor = innerFloorY(shape, Math.abs(lateral), halfCavityWidth, roadY, clearHeight, invert);
        int ceiling = innerCeilingY(shape, Math.abs(lateral), halfCavityWidth, roadY, clearHeight);
        if (ceiling < floor) {
            return false;
        }
        // Outside lateral span of the cavity (including shape-specific wall insets).
        if (!withinInnerLateral(shape, Math.abs(lateral), y, roadY, halfCavityWidth, clearHeight, invert)) {
            return false;
        }
        return y >= floor && y <= ceiling;
    }

    /**
     * True when the cell lies inside the outer shell (inner + lining thickness).
     * Used by the mask builder; lateral uses halfCavityWidth + lining.
     */
    public static boolean containsOuter(
            TunnelShape shape,
            int lateral,
            int y,
            int roadY,
            int halfCavityWidth,
            int clearHeight,
            int liningThickness) {
        if (liningThickness <= 0) {
            return containsInner(shape, lateral, y, roadY, halfCavityWidth, clearHeight);
        }
        int halfOuter = halfCavityWidth + liningThickness;
        int invert = derivedInvertDepth(shape, halfCavityWidth, clearHeight);
        int floor = outerFloorY(shape, Math.abs(lateral), halfCavityWidth, roadY, clearHeight, liningThickness, invert);
        int ceiling = outerCeilingY(shape, Math.abs(lateral), halfCavityWidth, roadY, clearHeight, liningThickness);
        if (ceiling < floor) {
            return false;
        }
        if (Math.abs(lateral) > halfOuter) {
            return false;
        }
        // For columns outside the cavity half-width, still allow lining shell.
        if (Math.abs(lateral) > halfCavityWidth) {
            return y >= floor && y <= ceiling;
        }
        return y >= floor && y <= ceiling;
    }

    public static int innerCeilingY(
            TunnelShape shape,
            int distFromCenter,
            int halfCavityWidth,
            int roadY,
            int clearHeight) {
        if (halfCavityWidth <= 0) {
            return roadY + clearHeight;
        }
        int d = Math.min(distFromCenter, halfCavityWidth);
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case RECTANGULAR -> roadY + clearHeight;
            case D_SHAPE -> dShapeCeiling(d, halfCavityWidth, roadY, clearHeight);
            case ARCH -> archCeiling(d, halfCavityWidth, roadY, clearHeight);
            case HORSESHOE -> horseshoeCeiling(d, halfCavityWidth, roadY, clearHeight);
            case ELLIPTICAL -> ellipticalCeiling(d, halfCavityWidth, roadY, clearHeight);
            case CIRCULAR -> circularCeiling(d, halfCavityWidth, roadY, clearHeight);
            case PENTAGONAL -> pentagonalCeiling(d, halfCavityWidth, roadY, clearHeight);
        };
    }

    public static int innerFloorY(
            TunnelShape shape,
            int distFromCenter,
            int halfCavityWidth,
            int roadY,
            int clearHeight,
            int invertDepth) {
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case HORSESHOE -> horseshoeFloor(distFromCenter, halfCavityWidth, roadY);
            case CIRCULAR -> circularFloor(distFromCenter, halfCavityWidth, roadY, clearHeight, invertDepth);
            case ELLIPTICAL -> ellipticalFloor(distFromCenter, halfCavityWidth, roadY, clearHeight, invertDepth);
            default -> roadY + 1;
        };
    }

    public static int outerCeilingY(
            TunnelShape shape,
            int distFromCenter,
            int halfCavityWidth,
            int roadY,
            int clearHeight,
            int liningThickness) {
        int lining = Math.max(0, liningThickness);
        // For columns beyond cavity, use edge ceiling as base then add lining.
        int baseDist = Math.min(distFromCenter, halfCavityWidth);
        int inner = innerCeilingY(shape, baseDist, halfCavityWidth, roadY, clearHeight);
        if (distFromCenter > halfCavityWidth) {
            // Side shell: full height from near-road up to edge ceiling + lining.
            return inner + lining;
        }
        return inner + lining;
    }

    public static int outerFloorY(
            TunnelShape shape,
            int distFromCenter,
            int halfCavityWidth,
            int roadY,
            int clearHeight,
            int liningThickness,
            int invertDepth) {
        int lining = Math.max(0, liningThickness);
        int baseDist = Math.min(distFromCenter, halfCavityWidth);
        int inner = innerFloorY(shape, baseDist, halfCavityWidth, roadY, clearHeight, invertDepth);
        // Side shell columns sit on the same structural floor band.
        if (distFromCenter > halfCavityWidth) {
            return Math.min(inner, roadY) - lining;
        }
        return inner - lining;
    }

    private static boolean withinInnerLateral(
            TunnelShape shape,
            int distFromCenter,
            int y,
            int roadY,
            int halfCavityWidth,
            int clearHeight,
            int invertDepth) {
        if (distFromCenter > halfCavityWidth) {
            return false;
        }
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case HORSESHOE -> {
                // Lower walls inset; mid-height may reach full half-width (bulge).
                int inset = Math.max(1, halfCavityWidth / 3);
                int maxAtFloor = halfCavityWidth - inset;
                if (y <= roadY + 2) {
                    yield distFromCenter <= maxAtFloor;
                }
                yield distFromCenter <= halfCavityWidth;
            }
            case CIRCULAR -> {
                int radius = circularRadius(halfCavityWidth);
                int clear = effectiveCircularClearHeight(halfCavityWidth, clearHeight);
                int centerY = roadY + clear - radius;
                double dy = y - centerY;
                double maxDx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
                // Radius equals half-width, so the circle equation already respects cavity width.
                yield distFromCenter <= Math.floor(maxDx + 1e-9);
            }
            case ELLIPTICAL -> {
                int vertSemi = Math.max(1, (clearHeight + invertDepth + 1) / 2);
                int centerY = roadY - invertDepth + vertSemi;
                double ny = (y - (double) centerY) / vertSemi;
                if (Math.abs(ny) > 1.0) {
                    yield false;
                }
                double maxNx = Math.sqrt(Math.max(0.0, 1.0 - ny * ny));
                double maxDx = maxNx * halfCavityWidth;
                // Above the road datum, never exceed the design cavity half-width.
                if (y >= roadY) {
                    maxDx = Math.min(maxDx, halfCavityWidth);
                }
                yield distFromCenter <= Math.floor(maxDx + 1e-9);
            }
            default -> distFromCenter <= halfCavityWidth;
        };
    }

    /** Straight walls up to spring line, then circular arc roof. */
    private static int dShapeCeiling(int d, int halfW, int roadY, int clearHeight) {
        // Keep at least ~2 blocks of vertical wall when clear height allows, so D_SHAPE
        // stays distinct from continuous ARCH even at modest sizes.
        int radius = Math.max(1, Math.min(halfW, Math.max(1, clearHeight - 2)));
        int springY = roadY + clearHeight - radius;
        if (d >= halfW) {
            return springY;
        }
        double nx = (double) d / radius;
        if (nx >= 1.0) {
            return springY;
        }
        int rise = (int) Math.round(Math.sqrt(Math.max(0.0, 1.0 - nx * nx)) * radius);
        return springY + rise;
    }

    /** Continuous quadratic arch from near-road at the edges (no vertical wall segment). */
    private static int archCeiling(int d, int halfW, int roadY, int clearHeight) {
        double norm = (double) d / halfW;
        int archDepth = Math.max(1, clearHeight - 1);
        int drop = (int) Math.round(norm * norm * archDepth);
        return roadY + clearHeight - drop;
    }

    /** Round roof with fuller mid-height (flatter than CIRCULAR / ARCH near mid-sides). */
    private static int horseshoeCeiling(int d, int halfW, int roadY, int clearHeight) {
        double norm = (double) d / halfW;
        // Shallow roof drop — keeps a wide flat crown so horseshoe ≠ truncated circle.
        int archDepth = Math.max(1, clearHeight / 3);
        int drop = (int) Math.round(norm * norm * archDepth);
        return roadY + clearHeight - drop;
    }

    private static int horseshoeFloor(int distFromCenter, int halfW, int roadY) {
        int inset = Math.max(1, halfW / 3);
        if (distFromCenter > halfW - inset) {
            return roadY + 2;
        }
        return roadY + 1;
    }

    private static int ellipticalCeiling(int d, int halfW, int roadY, int clearHeight) {
        int invert = derivedInvertDepth(TunnelShape.ELLIPTICAL, halfW, clearHeight);
        // Ellipse spans [roadY - invert, roadY + clearHeight].
        int vertSemi = Math.max(1, (clearHeight + invert + 1) / 2);
        int centerY = roadY - invert + vertSemi;
        double nx = halfW > 0 ? (double) d / halfW : 0.0;
        if (nx >= 1.0) {
            return roadY + 1;
        }
        double ny = Math.sqrt(Math.max(0.0, 1.0 - nx * nx));
        return Math.min(roadY + clearHeight, (int) Math.round(centerY + ny * vertSemi));
    }

    private static int ellipticalFloor(int d, int halfW, int roadY, int clearHeight, int invertDepth) {
        if (invertDepth <= 0) {
            return roadY + 1;
        }
        int vertSemi = Math.max(1, (clearHeight + invertDepth + 1) / 2);
        int centerY = roadY - invertDepth + vertSemi;
        double nx = halfW > 0 ? (double) d / halfW : 0.0;
        if (nx >= 1.0) {
            return roadY + 1;
        }
        double ny = Math.sqrt(Math.max(0.0, 1.0 - nx * nx));
        int floor = (int) Math.round(centerY - ny * vertSemi);
        // Never raise floor above road+1 in the carriageway (road stays flat).
        return Math.min(floor, roadY + 1);
    }

    /**
     * True circle locked to cavity half-width. Clear height taller than the diameter is
     * clamped via {@link #effectiveCircularClearHeight} so the profile is never a
     * laterally truncated cylinder.
     */
    private static int circularRadius(int halfW) {
        return Math.max(1, halfW);
    }

    /**
     * Circular sections cannot honour an arbitrary clear height larger than the diameter
     * without ceasing to be circular; excess height is ignored.
     */
    public static int effectiveCircularClearHeight(int halfCavityWidth, int clearHeight) {
        int diameter = Math.max(2, 2 * Math.max(1, halfCavityWidth));
        return Math.clamp(clearHeight, 1, diameter);
    }

    private static int circularCeiling(int d, int halfW, int roadY, int clearHeight) {
        int radius = circularRadius(halfW);
        int clear = effectiveCircularClearHeight(halfW, clearHeight);
        int centerY = roadY + clear - radius;
        if (d > radius) {
            return roadY + 1;
        }
        int rise = (int) Math.round(Math.sqrt(Math.max(0.0, (double) radius * radius - (double) d * d)));
        return Math.min(roadY + clear, centerY + rise);
    }

    private static int circularFloor(int d, int halfW, int roadY, int clearHeight, int invertDepth) {
        if (invertDepth <= 0) {
            return roadY + 1;
        }
        int radius = circularRadius(halfW);
        int clear = effectiveCircularClearHeight(halfW, clearHeight);
        int centerY = roadY + clear - radius;
        if (d > radius) {
            return roadY + 1;
        }
        int rise = (int) Math.round(Math.sqrt(Math.max(0.0, (double) radius * radius - (double) d * d)));
        int floor = centerY - rise;
        return Math.min(floor, roadY + 1);
    }

    /** Vertical walls then two roof slopes meeting at center peak. */
    private static int pentagonalCeiling(int d, int halfW, int roadY, int clearHeight) {
        int wallHeight = Math.max(2, clearHeight * 2 / 3);
        int peakRise = clearHeight - wallHeight;
        if (d >= halfW) {
            return roadY + wallHeight;
        }
        double t = 1.0 - (double) d / halfW;
        int rise = (int) Math.round(t * peakRise);
        return roadY + wallHeight + rise;
    }
}
