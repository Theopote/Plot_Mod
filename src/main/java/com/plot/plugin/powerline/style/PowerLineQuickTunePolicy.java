package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** Quick Tune 数值策略（相对 base preset 默认杆塔几何）。 */
public final class PowerLineQuickTunePolicy {
    public enum PoleHeightBand {
        SMALL(0.78),
        MEDIUM(1.0),
        TALL(1.28);

        private final double scale;

        PoleHeightBand(double scale) {
            this.scale = scale;
        }

        double scale() {
            return scale;
        }
    }

    public enum CrossarmWidthBand {
        NARROW(0.72),
        NORMAL(1.0),
        WIDE(1.42);

        private final double scale;

        CrossarmWidthBand(double scale) {
            this.scale = scale;
        }

        double scale() {
            return scale;
        }
    }

    private static final double LEGACY_POLE_HEIGHT_MEDIUM = 10.0;
    private static final double LEGACY_POLE_HEIGHT_SMALL = 8.0;
    private static final double LEGACY_POLE_HEIGHT_TALL = 14.0;
    private static final double BAND_MATCH_TOLERANCE = 0.08;

    private PowerLineQuickTunePolicy() {
    }

    public static boolean supportsPoleHeightTune(PowerLineFootprint line) {
        if (line == null || line.hasTowerFamily()) {
            return false;
        }
        return !line.hasPoleDesign() || hasColumnLayers(resolveDesign(line, null));
    }

    public static boolean supportsCrossarmTune(PowerLineFootprint line, PoleDesignResolver resolver) {
        if (line == null || line.hasTowerFamily()) {
            return false;
        }
        PoleDesign design = resolveDesign(line, resolver);
        return design != null && hasCrossarmLayers(design);
    }

    public static PoleHeightBand detectPoleHeightBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PoleDesignResolver resolver) {
        if (!supportsPoleHeightTune(line)) {
            return null;
        }
        if (!line.hasPoleDesign()) {
            return detectLegacyPoleHeightBand(line.getPoleHeight());
        }
        PoleDesign design = resolveDesign(line, resolver);
        if (design == null) {
            return null;
        }
        int baseline = baselineColumnHeight(base, design);
        if (baseline <= 0) {
            return PoleHeightBand.MEDIUM;
        }
        int current = sumColumnHeight(design);
        return closestHeightBand((double) current / baseline);
    }

    public static CrossarmWidthBand detectCrossarmWidthBand(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PoleDesignResolver resolver) {
        if (!supportsCrossarmTune(line, resolver)) {
            return null;
        }
        PoleDesign design = resolveDesign(line, resolver);
        if (design == null) {
            return null;
        }
        int baseline = baselineCrossarmLength(base, design);
        if (baseline <= 0) {
            return CrossarmWidthBand.NORMAL;
        }
        int current = maxCrossarmLength(design);
        return closestCrossarmBand((double) current / baseline);
    }

    public static void applyPoleHeightBand(
            PoleDesign design,
            PowerLineStylePreset base,
            PoleHeightBand band) {
        if (design == null || band == null) {
            return;
        }
        int baseline = baselineColumnHeight(base, design);
        if (baseline <= 0) {
            baseline = sumColumnHeight(design);
        }
        int target = Math.max(1, (int) Math.round(baseline * band.scale()));
        scaleColumnLayers(design, target);
    }

    public static void applyCrossarmWidthBand(
            PoleDesign design,
            PowerLineStylePreset base,
            CrossarmWidthBand band) {
        if (design == null || band == null) {
            return;
        }
        int baseline = baselineCrossarmLength(base, design);
        if (baseline <= 0) {
            baseline = maxCrossarmLength(design);
        }
        int target = normalizeCrossarmLength((int) Math.round(baseline * band.scale()));
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                layer.setCrossarmLength(target);
            }
        }
    }

    public static void applyLegacyPoleHeight(PowerLineFootprint line, PoleHeightBand band) {
        if (line == null || band == null) {
            return;
        }
        line.setPoleHeight(switch (band) {
            case SMALL -> LEGACY_POLE_HEIGHT_SMALL;
            case MEDIUM -> LEGACY_POLE_HEIGHT_MEDIUM;
            case TALL -> LEGACY_POLE_HEIGHT_TALL;
        });
    }

    public static int conductorCount(PowerLineFootprint line, PowerLineStylePreset base) {
        if (line == null) {
            return 1;
        }
        return PowerLineStylePreset.resolveConductorCount(line);
    }

    private static PoleDesign resolveDesign(PowerLineFootprint line, PoleDesignResolver resolver) {
        if (line == null || !line.hasPoleDesign()) {
            return null;
        }
        if (resolver != null) {
            return resolver.find(line.getPoleDesignId());
        }
        return PoleDesignCatalog.findBuiltin(line.getPoleDesignId());
    }

    private static PoleHeightBand detectLegacyPoleHeightBand(double poleHeight) {
        if (poleHeight <= LEGACY_POLE_HEIGHT_SMALL + 0.5) {
            return PoleHeightBand.SMALL;
        }
        if (poleHeight >= LEGACY_POLE_HEIGHT_TALL - 0.5) {
            return PoleHeightBand.TALL;
        }
        return PoleHeightBand.MEDIUM;
    }

    private static PoleHeightBand closestHeightBand(double ratio) {
        PoleHeightBand closest = PoleHeightBand.MEDIUM;
        double best = Math.abs(ratio - closest.scale());
        for (PoleHeightBand band : PoleHeightBand.values()) {
            double diff = Math.abs(ratio - band.scale());
            if (diff < best) {
                best = diff;
                closest = band;
            }
        }
        return best <= BAND_MATCH_TOLERANCE + 0.05 ? closest : null;
    }

    private static CrossarmWidthBand closestCrossarmBand(double ratio) {
        CrossarmWidthBand closest = CrossarmWidthBand.NORMAL;
        double best = Math.abs(ratio - closest.scale());
        for (CrossarmWidthBand band : CrossarmWidthBand.values()) {
            double diff = Math.abs(ratio - band.scale());
            if (diff < best) {
                best = diff;
                closest = band;
            }
        }
        return best <= BAND_MATCH_TOLERANCE + 0.05 ? closest : null;
    }

    private static int baselineColumnHeight(PowerLineStylePreset base, PoleDesign current) {
        if (base != null && base.getPoleDesignId() != null) {
            PoleDesign presetDesign = PoleDesignCatalog.findBuiltin(base.getPoleDesignId());
            if (presetDesign != null) {
                int height = sumColumnHeight(presetDesign);
                if (height > 0) {
                    return height;
                }
            }
        }
        return sumColumnHeight(current);
    }

    private static int baselineCrossarmLength(PowerLineStylePreset base, PoleDesign current) {
        if (base != null && base.getPoleDesignId() != null) {
            PoleDesign presetDesign = PoleDesignCatalog.findBuiltin(base.getPoleDesignId());
            if (presetDesign != null) {
                int length = maxCrossarmLength(presetDesign);
                if (length > 0) {
                    return length;
                }
            }
        }
        return maxCrossarmLength(current);
    }

    private static void scaleColumnLayers(PoleDesign design, int targetTotalHeight) {
        int current = sumColumnHeight(design);
        if (current <= 0) {
            return;
        }
        double factor = (double) targetTotalHeight / current;
        int accumulated = 0;
        int columnIndex = 0;
        int columnCount = countColumnLayers(design);
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() != PoleLayer.Shape.COLUMN) {
                continue;
            }
            columnIndex++;
            int scaled = columnIndex == columnCount
                ? Math.max(1, targetTotalHeight - accumulated)
                : Math.max(1, (int) Math.round(layer.getHeight() * factor));
            accumulated += scaled;
            layer.setHeight(scaled);
        }
    }

    private static int sumColumnHeight(PoleDesign design) {
        int total = 0;
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.COLUMN) {
                total += layer.getHeight();
            }
        }
        return total;
    }

    private static int maxCrossarmLength(PoleDesign design) {
        int max = 0;
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                max = Math.max(max, layer.getCrossarmLength());
            }
        }
        return max;
    }

    private static int countColumnLayers(PoleDesign design) {
        int count = 0;
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.COLUMN) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasColumnLayers(PoleDesign design) {
        return sumColumnHeight(design) > 0;
    }

    private static boolean hasCrossarmLayers(PoleDesign design) {
        return maxCrossarmLength(design) > 0;
    }

    private static int normalizeCrossarmLength(int length) {
        int normalized = Math.max(1, length);
        if (normalized % 2 == 0) {
            normalized++;
        }
        return normalized;
    }
}
