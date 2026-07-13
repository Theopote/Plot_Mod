package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadMaterialUtils;

import java.util.List;

/**
 * 横断面解析结果：道路级覆盖与全局默认合并后的有效值，供生成器与预览使用。
 */
public final class ResolvedCrossSection {
    public static final String DEFAULT_MARKING_MATERIAL = "material.plot.white_concrete";
    public static final String DEFAULT_BIKE_LANE_MATERIAL = "minecraft:light_blue_concrete";

    public final int laneCount;
    public final List<Integer> laneWidths;
    public final List<Double> laneDividerOffsets;
    public final int carriagewayWidth;
    public final String carriagewayMaterial;
    public final boolean includeMedian;
    public final int medianWidth;
    public final String medianMaterial;
    public final boolean laneDividers;
    public final CenterLineStyle centerLineStyle;
    public final String markingMaterial;
    public final boolean includeShoulder;
    public final int shoulderWidth;
    public final String shoulderMaterial;
    public final boolean includeBikeLane;
    public final int bikeLaneWidth;
    public final String bikeLaneMaterial;
    public final boolean includeSidewalk;
    public final int sidewalkWidth;
    public final String sidewalkMaterial;
    public final boolean includeDrain;
    public final Integer streetlightSpacing;
    public final boolean includeSlopeBatter;
    public final float fillSlopeRatio;
    public final float cutSlopeRatio;
    public final String fillSlopeMaterial;
    public final String cutSlopeMaterial;

    private ResolvedCrossSection(
            int laneCount,
            List<Integer> laneWidths,
            List<Double> laneDividerOffsets,
            int carriagewayWidth,
            String carriagewayMaterial,
            boolean includeMedian,
            int medianWidth,
            String medianMaterial,
            boolean laneDividers,
            CenterLineStyle centerLineStyle,
            String markingMaterial,
            boolean includeShoulder,
            int shoulderWidth,
            String shoulderMaterial,
            boolean includeBikeLane,
            int bikeLaneWidth,
            String bikeLaneMaterial,
            boolean includeSidewalk,
            int sidewalkWidth,
            String sidewalkMaterial,
            boolean includeDrain,
            Integer streetlightSpacing,
            boolean includeSlopeBatter,
            float fillSlopeRatio,
            float cutSlopeRatio,
            String fillSlopeMaterial,
            String cutSlopeMaterial) {
        this.laneCount = laneCount;
        this.laneWidths = List.copyOf(laneWidths);
        this.laneDividerOffsets = List.copyOf(laneDividerOffsets);
        this.carriagewayWidth = carriagewayWidth;
        this.carriagewayMaterial = carriagewayMaterial;
        this.includeMedian = includeMedian;
        this.medianWidth = medianWidth;
        this.medianMaterial = medianMaterial;
        this.laneDividers = laneDividers;
        this.centerLineStyle = centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE;
        this.markingMaterial = markingMaterial;
        this.includeShoulder = includeShoulder;
        this.shoulderWidth = shoulderWidth;
        this.shoulderMaterial = shoulderMaterial;
        this.includeBikeLane = includeBikeLane;
        this.bikeLaneWidth = bikeLaneWidth;
        this.bikeLaneMaterial = bikeLaneMaterial;
        this.includeSidewalk = includeSidewalk;
        this.sidewalkWidth = sidewalkWidth;
        this.sidewalkMaterial = sidewalkMaterial;
        this.includeDrain = includeDrain;
        this.streetlightSpacing = streetlightSpacing;
        this.includeSlopeBatter = includeSlopeBatter;
        this.fillSlopeRatio = fillSlopeRatio;
        this.cutSlopeRatio = cutSlopeRatio;
        this.fillSlopeMaterial = fillSlopeMaterial;
        this.cutSlopeMaterial = cutSlopeMaterial;
    }

    public static ResolvedCrossSection fromConfig(RoadSystemConfig defaults) {
        return resolve(RoadCrossSection.fromConfig(defaults), defaults);
    }

    public static ResolvedCrossSection resolve(RoadCrossSection section, RoadSystemConfig defaults) {
        RoadCrossSection source = section != null ? section : new RoadCrossSection();
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("road_system");

        LaneGroup carriageway = source.getCarriageway();
        Median median = source.getMedian();
        Markings markings = source.getMarkings();
        Shoulder shoulder = source.getShoulder();
        BikeLane bikeLane = source.getBikeLane();
        Sidewalk sidewalk = source.getSidewalk();
        Drain drain = source.getDrain();
        SlopeBatter slopeBatter = source.getSlopeBatter();
        StreetFurniture furniture = source.getStreetFurniture();

        int width = carriageway.getWidth() != null ? carriageway.getWidth() : config.getRoadWidth();
        int safeWidth = Math.max(1, width);
        int laneCount = carriageway.getEffectiveLaneCount();
        List<Integer> laneWidths = carriageway.resolveLaneWidths(safeWidth);
        List<Double> laneDividerOffsets = carriageway.resolveLaneDividerOffsets(safeWidth);
        String roadMaterial = carriageway.getMaterial() != null
            ? carriageway.getMaterial()
            : config.getSelectedMaterial();

        boolean includeMedian = median.getEnabled() != null && median.getEnabled();
        int medianWidth = includeMedian
            ? Math.max(1, median.getWidth() != null ? median.getWidth() : 1)
            : 0;
        String medianMaterial = median.getMaterial() != null
            ? median.getMaterial()
            : "material.plot.grass_block";

        boolean laneDividers = MarkingRules.resolveLaneDividers(markings, laneCount);
        CenterLineStyle centerLineStyle = MarkingRules.resolveCenterLineStyle(markings, config);
        String markingMaterial = markings.getMaterial() != null
            ? markings.getMaterial()
            : DEFAULT_MARKING_MATERIAL;

        boolean includeShoulder = shoulder.getEnabled() != null
            ? shoulder.getEnabled()
            : config.isIncludeShoulder();
        int shoulderWidth = shoulder.getWidth() != null ? shoulder.getWidth() : config.getShoulderWidth();
        String shoulderMaterial = shoulder.getMaterial() != null
            ? shoulder.getMaterial()
            : (config.getFillSlopeMaterial() != null && !config.getFillSlopeMaterial().isBlank()
                ? config.getFillSlopeMaterial()
                : "material.plot.gravel");

        boolean includeBikeLane = bikeLane.getEnabled() != null && bikeLane.getEnabled();
        int bikeLaneWidth = includeBikeLane
            ? Math.max(1, bikeLane.getWidth() != null ? bikeLane.getWidth() : 1)
            : 0;
        String bikeLaneMaterial = bikeLane.getMaterial() != null
            ? bikeLane.getMaterial()
            : DEFAULT_BIKE_LANE_MATERIAL;

        boolean includeSidewalk = sidewalk.getEnabled() != null
            ? sidewalk.getEnabled()
            : config.isIncludeSidewalk();
        int sidewalkWidth = sidewalk.getWidth() != null ? sidewalk.getWidth() : config.getSidewalkWidth();
        String sidewalkMaterial = sidewalk.getMaterial() != null
            ? sidewalk.getMaterial()
            : config.getSelectedSidewalkMaterial();

        boolean includeDrain = drain.getEnabled() != null ? drain.getEnabled() : config.isIncludeDrainage();

        boolean slopeEnabled = slopeBatter.getEnabled() != null
            ? slopeBatter.getEnabled()
            : includeShoulder;
        float fillRatio = slopeBatter.getFillRatio() != null
            ? slopeBatter.getFillRatio()
            : config.getFillSlopeRatio();
        float cutRatio = slopeBatter.getCutRatio() != null
            ? slopeBatter.getCutRatio()
            : config.getCutSlopeRatio();
        String fillSlopeMaterial = slopeBatter.getFillMaterial() != null && !slopeBatter.getFillMaterial().isBlank()
            ? slopeBatter.getFillMaterial()
            : config.getFillSlopeMaterial();
        String cutSlopeMaterial = slopeBatter.getCutMaterial() != null
            ? slopeBatter.getCutMaterial()
            : config.getCutSlopeMaterial();
        boolean includeSlopeBatter = slopeEnabled && includeShoulder && (fillRatio > 0f || cutRatio > 0f);

        return new ResolvedCrossSection(
            laneCount,
            laneWidths,
            laneDividerOffsets,
            safeWidth,
            RoadMaterialUtils.normalizeStoredMaterial(roadMaterial),
            includeMedian,
            medianWidth,
            RoadMaterialUtils.normalizeStoredMaterial(medianMaterial),
            laneDividers,
            centerLineStyle,
            RoadMaterialUtils.normalizeStoredMaterial(markingMaterial),
            includeShoulder,
            Math.max(0, shoulderWidth),
            RoadMaterialUtils.normalizeStoredMaterial(shoulderMaterial),
            includeBikeLane,
            bikeLaneWidth,
            RoadMaterialUtils.normalizeStoredMaterial(bikeLaneMaterial),
            includeSidewalk,
            Math.max(0, sidewalkWidth),
            RoadMaterialUtils.normalizeStoredMaterial(sidewalkMaterial),
            includeDrain,
            furniture.getStreetlightSpacing(),
            includeSlopeBatter,
            includeSlopeBatter ? fillRatio : 0f,
            includeSlopeBatter ? cutRatio : 0f,
            RoadMaterialUtils.normalizeStoredMaterial(fillSlopeMaterial),
            cutSlopeMaterial != null && !cutSlopeMaterial.isBlank()
                ? RoadMaterialUtils.normalizeStoredMaterial(cutSlopeMaterial)
                : null
        );
    }

    public double carriagewayHalfWidth() {
        return RoadDimensionUtils.halfExtentFromCenter(carriagewayWidth);
    }

    /** 路肩 + 自行车道 + 人行道总方块宽度。 */
    public int outerBandBlockCount() {
        return (includeShoulder ? shoulderWidth : 0)
            + (includeBikeLane ? bikeLaneWidth : 0)
            + (includeSidewalk ? sidewalkWidth : 0);
    }

    private static double stripCenterOffset(int startLateral, int widthBlocks) {
        if (widthBlocks <= 0) {
            return 0.0;
        }
        return startLateral + (widthBlocks - 1) / 2.0;
    }

    public double shoulderCenterOffset() {
        if (!includeShoulder || shoulderWidth <= 0) {
            return 0.0;
        }
        int start = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + 1;
        return stripCenterOffset(start, shoulderWidth);
    }

    public double bikeLaneCenterOffset() {
        if (!includeBikeLane || bikeLaneWidth <= 0) {
            return 0.0;
        }
        int start = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + 1;
        if (includeShoulder) {
            start += shoulderWidth;
        }
        return stripCenterOffset(start, bikeLaneWidth);
    }

    public double sidewalkCenterOffset() {
        if (!includeSidewalk || sidewalkWidth <= 0) {
            return 0.0;
        }
        int start = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + 1;
        if (includeShoulder) {
            start += shoulderWidth;
        }
        if (includeBikeLane) {
            start += bikeLaneWidth;
        }
        return stripCenterOffset(start, sidewalkWidth);
    }

    /** 路肩外侧过渡带总宽度（路肩 + 自行车道 + 人行道）。 */
    public double outerBandWidth() {
        return (includeShoulder ? shoulderWidth : 0)
            + (includeBikeLane ? bikeLaneWidth : 0)
            + (includeSidewalk ? sidewalkWidth : 0);
    }

    public double bikeLaneOffset() {
        return bikeLaneCenterOffset();
    }

    public double outerSidewalkOffset() {
        return sidewalkCenterOffset();
    }

    public double outerDrainageOffset() {
        int outer = RoadDimensionUtils.maxLateralOffset(carriagewayWidth) + outerBandBlockCount() + 1;
        return outer;
    }
}
