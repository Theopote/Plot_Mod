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
        return includeShoulder && (fillSlopeRatio > 0f || cutSlopeRatio > 0f);
    }

    public RoadCrossSection toCrossSection() {
        String roadMat = roadMaterial != null && !roadMaterial.isBlank()
            ? roadMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String sidewalkMat = sidewalkMaterial != null && !sidewalkMaterial.isBlank()
            ? sidewalkMaterial
            : roadMat;
        String shoulderMat = shoulderMaterial != null && !shoulderMaterial.isBlank()
            ? shoulderMaterial
            : "material.plot.gravel";
        String bikeMat = bikeLaneMaterial != null && !bikeLaneMaterial.isBlank()
            ? bikeLaneMaterial
            : ResolvedCrossSection.DEFAULT_BIKE_LANE_MATERIAL;

        int lanes = resolveLaneCount();
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(lanes);
        section.getCarriageway().setWidth(width);
        section.getCarriageway().setMaterial(MaterialMix.single(roadMat));
        section.getCarriageway().syncLaneCount(lanes);

        section.getShoulder().setEnabled(includeShoulder);
        section.getShoulder().setWidth(shoulderWidth);
        section.getShoulder().setMaterial(shoulderMat);

        section.getBikeLane().setEnabled(includeBikeLane);
        section.getBikeLane().setWidth(includeBikeLane ? Math.max(1, bikeLaneWidth) : 0);
        section.getBikeLane().setMaterial(bikeMat);

        section.getSidewalk().setEnabled(hasSidewalk);
        section.getSidewalk().setWidth(hasSidewalk ? Math.max(1, sidewalkWidth) : 0);
        section.getSidewalk().setMaterial(sidewalkMat);

        section.getDrain().setEnabled(includeDrainage);

        section.getMedian().setEnabled(includeMedian);
        section.getMedian().setWidth(includeMedian ? Math.max(1, medianWidth) : 0);

        section.getMarkings().setLaneDividers(resolveLaneDividers());
        section.getMarkings().setCenterLineStyle(resolveCenterLineStyle());
        section.getMarkings().setMaterial(
            markingMaterial != null && !markingMaterial.isBlank()
                ? markingMaterial
                : "material.plot.white_concrete"
        );

        boolean slopeBatter = resolveIncludeSlopeBatter();
        section.getSlopeBatter().setEnabled(slopeBatter);
        if (slopeBatter) {
            section.getSlopeBatter().setFillRatio(fillSlopeRatio);
            section.getSlopeBatter().setCutRatio(cutSlopeRatio);
            section.getSlopeBatter().setFillMaterial(
                fillSlopeMaterial != null && !fillSlopeMaterial.isBlank()
                    ? fillSlopeMaterial
                    : shoulderMat
            );
            if (cutSlopeMaterial != null && !cutSlopeMaterial.isBlank()) {
                section.getSlopeBatter().setCutMaterial(cutSlopeMaterial);
            }
        }

        if (streetlightSpacing != null && streetlightSpacing > 0) {
            section.getStreetFurniture().setStreetlightSpacing(streetlightSpacing);
        } else {
            section.getStreetFurniture().setStreetlightSpacing(null);
        }
        return section;
    }

    public void applyTo(Road road) {
        if (road == null) {
            return;
        }
        road.setCrossSection(toCrossSection());
        road.setStyleId(id);
        if (maxSlope > 0f) {
            road.setMaxSlope(maxSlope);
        }
    }
}
