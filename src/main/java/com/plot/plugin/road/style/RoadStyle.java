package com.plot.plugin.road.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;

/**
 * 道路风格模板：一次性决定横断面、纵坡与附属设施参数。
 * 生成器只消费 {@link com.plot.plugin.road.model.section.ResolvedCrossSection}，不直接读 Style。
 */
public class RoadStyle {
    public String id;
    public String name;
    public int width = 5;
    /** 0 表示按宽度自动推断（width &gt;= 7 → 2 车道） */
    public int laneCount;
    public boolean hasSidewalk;
    public int sidewalkWidth;
    public boolean includeShoulder;
    public int shoulderWidth;
    public boolean includeBikeLane;
    public int bikeLaneWidth = 1;
    public boolean includeDrainage;
    public boolean includeMedian;
    public int medianWidth;
    public float maxSlope = 10.0f;
    public Boolean includeSlopeBatter;
    public float fillSlopeRatio = 1.5f;
    public float cutSlopeRatio = 1.0f;
    public String roadMaterial;
    public String sidewalkMaterial;
    public String shoulderMaterial;
    public String bikeLaneMaterial;
    public String fillSlopeMaterial;
    public String cutSlopeMaterial;
    public String markingMaterial = "material.plot.white_concrete";
    public String centerLineStyle;
    public Boolean laneDividers;
    public Integer streetlightSpacing;
    /** 可选默认主题；全局主题见 {@link com.plot.plugin.config.RoadSystemConfig#getRoadThemeId()}。 */
    public String themeId;

    public RoadStyle() {
    }

    public RoadStyle(String id) {
        this.id = id;
        this.name = id;
    }

    public int resolveLaneCount() {
        if (laneCount > 0) {
            return laneCount;
        }
        return width >= 7 ? 2 : 1;
    }

    public CenterLineStyle resolveCenterLineStyle() {
        if (centerLineStyle == null || centerLineStyle.isBlank()) {
            return CenterLineStyle.NONE;
        }
        try {
            return CenterLineStyle.valueOf(centerLineStyle);
        } catch (IllegalArgumentException ignored) {
            return CenterLineStyle.NONE;
        }
    }

    public boolean resolveLaneDividers() {
        if (laneDividers != null) {
            return laneDividers;
        }
        return resolveLaneCount() > 1;
    }

    public boolean resolveIncludeSlopeBatter() {
        if (includeSlopeBatter != null) {
            return includeSlopeBatter;
        }
        return fillSlopeRatio > 0f || cutSlopeRatio > 0f;
    }

    public RoadCrossSection toCrossSection() {
        return toCrossSection(null);
    }

    public RoadCrossSection toCrossSection(String themeIdOverride) {
        String resolvedThemeId = themeIdOverride != null && !themeIdOverride.isBlank()
            ? themeIdOverride
            : (this.themeId != null && !this.themeId.isBlank() ? this.themeId : RoadThemeCatalog.MODERN_ID);
        return buildCrossSectionFrom(RoadThemeCatalog.applyTheme(resolvedThemeId, this));
    }

    public RoadStyle copy() {
        RoadStyle copy = new RoadStyle();
        copy.id = id;
        copy.name = name;
        copy.width = width;
        copy.laneCount = laneCount;
        copy.hasSidewalk = hasSidewalk;
        copy.sidewalkWidth = sidewalkWidth;
        copy.includeShoulder = includeShoulder;
        copy.shoulderWidth = shoulderWidth;
        copy.includeBikeLane = includeBikeLane;
        copy.bikeLaneWidth = bikeLaneWidth;
        copy.includeDrainage = includeDrainage;
        copy.includeMedian = includeMedian;
        copy.medianWidth = medianWidth;
        copy.maxSlope = maxSlope;
        copy.includeSlopeBatter = includeSlopeBatter;
        copy.fillSlopeRatio = fillSlopeRatio;
        copy.cutSlopeRatio = cutSlopeRatio;
        copy.roadMaterial = roadMaterial;
        copy.sidewalkMaterial = sidewalkMaterial;
        copy.shoulderMaterial = shoulderMaterial;
        copy.bikeLaneMaterial = bikeLaneMaterial;
        copy.fillSlopeMaterial = fillSlopeMaterial;
        copy.cutSlopeMaterial = cutSlopeMaterial;
        copy.markingMaterial = markingMaterial;
        copy.centerLineStyle = centerLineStyle;
        copy.laneDividers = laneDividers;
        copy.streetlightSpacing = streetlightSpacing;
        copy.themeId = themeId;
        return copy;
    }

    private RoadCrossSection buildCrossSectionFrom(RoadStyle style) {
        String roadMat = style.roadMaterial != null && !style.roadMaterial.isBlank()
            ? style.roadMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String sidewalkMat = style.sidewalkMaterial != null && !style.sidewalkMaterial.isBlank()
            ? style.sidewalkMaterial
            : roadMat;
        String shoulderMat = style.shoulderMaterial != null && !style.shoulderMaterial.isBlank()
            ? style.shoulderMaterial
            : "material.plot.gravel";
        String bikeMat = style.bikeLaneMaterial != null && !style.bikeLaneMaterial.isBlank()
            ? style.bikeLaneMaterial
            : ResolvedCrossSection.DEFAULT_BIKE_LANE_MATERIAL;

        int lanes = style.resolveLaneCount();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(lanes);
        section.getCarriageway().setWidth(style.width);
        section.getCarriageway().setMaterial(MaterialMix.single(roadMat));
        section.getCarriageway().syncLaneCount(lanes);

        section.getShoulder().setEnabled(style.includeShoulder);
        section.getShoulder().setWidth(style.shoulderWidth);
        section.getShoulder().setMaterial(shoulderMat);

        section.getBikeLane().setEnabled(style.includeBikeLane);
        section.getBikeLane().setWidth(style.includeBikeLane ? Math.max(1, style.bikeLaneWidth) : 0);
        section.getBikeLane().setMaterial(bikeMat);

        section.getSidewalk().setEnabled(style.hasSidewalk);
        section.getSidewalk().setWidth(style.hasSidewalk ? Math.max(1, style.sidewalkWidth) : 0);
        section.getSidewalk().setMaterial(sidewalkMat);

        section.getDrain().setEnabled(style.includeDrainage);

        section.getMedian().setEnabled(style.includeMedian);
        section.getMedian().setWidth(style.includeMedian ? Math.max(1, style.medianWidth) : 0);

        section.getMarkings().setLaneDividers(style.resolveLaneDividers());
        section.getMarkings().setCenterLineStyle(style.resolveCenterLineStyle());
        section.getMarkings().setMaterial(
            style.markingMaterial != null && !style.markingMaterial.isBlank()
                ? style.markingMaterial
                : "material.plot.white_concrete"
        );

        boolean slopeBatter = style.resolveIncludeSlopeBatter();
        section.getSlopeBatter().setEnabled(slopeBatter);
        if (slopeBatter) {
            section.getSlopeBatter().setFillRatio(style.fillSlopeRatio);
            section.getSlopeBatter().setCutRatio(style.cutSlopeRatio);
            section.getSlopeBatter().setFillMaterial(
                style.fillSlopeMaterial != null && !style.fillSlopeMaterial.isBlank()
                    ? style.fillSlopeMaterial
                    : shoulderMat
            );
            if (style.cutSlopeMaterial != null && !style.cutSlopeMaterial.isBlank()) {
                section.getSlopeBatter().setCutMaterial(style.cutSlopeMaterial);
            }
        }

        if (style.streetlightSpacing != null && style.streetlightSpacing > 0) {
            section.getStreetFurniture().setStreetlightSpacing(style.streetlightSpacing);
        } else {
            section.getStreetFurniture().setStreetlightSpacing(null);
        }
        return section;
    }

    public void applyTo(Road road) {
        applyTo(road, null);
    }

    public void applyTo(Road road, String themeIdOverride) {
        if (road == null) {
            return;
        }
        road.setCrossSection(toCrossSection(themeIdOverride));
        road.setStyleId(id);
        if (maxSlope > 0f) {
            road.setMaxSlope(maxSlope);
        }
    }
}
