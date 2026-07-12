package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadMaterialUtils;

/**
 * 横断面解析结果：道路级覆盖与全局默认合并后的有效值，供生成器与预览使用。
 */
public final class ResolvedCrossSection {
    public final int laneCount;
    public final int carriagewayWidth;
    public final String carriagewayMaterial;
    public final boolean includeShoulder;
    public final int shoulderWidth;
    public final String shoulderMaterial;
    public final boolean includeSidewalk;
    public final int sidewalkWidth;
    public final String sidewalkMaterial;
    public final boolean includeDrain;
    public final Integer streetlightSpacing;
    public final float fillSlopeRatio;
    public final float cutSlopeRatio;

    private ResolvedCrossSection(
            int laneCount,
            int carriagewayWidth,
            String carriagewayMaterial,
            boolean includeShoulder,
            int shoulderWidth,
            String shoulderMaterial,
            boolean includeSidewalk,
            int sidewalkWidth,
            String sidewalkMaterial,
            boolean includeDrain,
            Integer streetlightSpacing,
            float fillSlopeRatio,
            float cutSlopeRatio) {
        this.laneCount = laneCount;
        this.carriagewayWidth = carriagewayWidth;
        this.carriagewayMaterial = carriagewayMaterial;
        this.includeShoulder = includeShoulder;
        this.shoulderWidth = shoulderWidth;
        this.shoulderMaterial = shoulderMaterial;
        this.includeSidewalk = includeSidewalk;
        this.sidewalkWidth = sidewalkWidth;
        this.sidewalkMaterial = sidewalkMaterial;
        this.includeDrain = includeDrain;
        this.streetlightSpacing = streetlightSpacing;
        this.fillSlopeRatio = fillSlopeRatio;
        this.cutSlopeRatio = cutSlopeRatio;
    }

    public static ResolvedCrossSection fromConfig(RoadSystemConfig defaults) {
        return resolve(RoadCrossSection.fromConfig(defaults), defaults);
    }

    public static ResolvedCrossSection resolve(RoadCrossSection section, RoadSystemConfig defaults) {
        RoadCrossSection source = section != null ? section : new RoadCrossSection();
        RoadSystemConfig config = defaults != null ? defaults : new RoadSystemConfig("road_system");

        LaneGroup carriageway = source.getCarriageway();
        Shoulder shoulder = source.getShoulder();
        Sidewalk sidewalk = source.getSidewalk();
        Drain drain = source.getDrain();
        StreetFurniture furniture = source.getStreetFurniture();

        int laneCount = carriageway.getEffectiveLaneCount();
        int width = carriageway.getWidth() != null ? carriageway.getWidth() : config.getRoadWidth();
        String roadMaterial = carriageway.getMaterial() != null
            ? carriageway.getMaterial()
            : config.getSelectedMaterial();

        boolean includeShoulder = shoulder.getEnabled() != null
            ? shoulder.getEnabled()
            : config.isIncludeShoulder();
        int shoulderWidth = shoulder.getWidth() != null ? shoulder.getWidth() : config.getShoulderWidth();
        String shoulderMaterial = shoulder.getMaterial() != null
            ? shoulder.getMaterial()
            : (config.getFillSlopeMaterial() != null && !config.getFillSlopeMaterial().isBlank()
                ? config.getFillSlopeMaterial()
                : "material.plot.gravel");

        boolean includeSidewalk = sidewalk.getEnabled() != null
            ? sidewalk.getEnabled()
            : config.isIncludeSidewalk();
        int sidewalkWidth = sidewalk.getWidth() != null ? sidewalk.getWidth() : config.getSidewalkWidth();
        String sidewalkMaterial = sidewalk.getMaterial() != null
            ? sidewalk.getMaterial()
            : config.getSelectedSidewalkMaterial();

        boolean includeDrain = drain.getEnabled() != null ? drain.getEnabled() : config.isIncludeDrainage();

        return new ResolvedCrossSection(
            laneCount,
            Math.max(1, width),
            RoadMaterialUtils.normalizeStoredMaterial(roadMaterial),
            includeShoulder,
            Math.max(0, shoulderWidth),
            RoadMaterialUtils.normalizeStoredMaterial(shoulderMaterial),
            includeSidewalk,
            Math.max(0, sidewalkWidth),
            RoadMaterialUtils.normalizeStoredMaterial(sidewalkMaterial),
            includeDrain,
            furniture.getStreetlightSpacing(),
            includeShoulder ? config.getFillSlopeRatio() : 0f,
            includeShoulder ? config.getCutSlopeRatio() : 0f
        );
    }

    public double carriagewayHalfWidth() {
        return carriagewayWidth / 2.0;
    }

    public double outerSidewalkOffset() {
        return carriagewayHalfWidth()
            + (includeShoulder ? shoulderWidth : 0)
            + (includeSidewalk ? sidewalkWidth / 2.0 : 0);
    }

    public double outerDrainageOffset() {
        double sidewalkBand = includeSidewalk ? sidewalkWidth : 0;
        return carriagewayHalfWidth()
            + (includeShoulder ? shoulderWidth : 0)
            + sidewalkBand
            + 0.5;
    }
}
