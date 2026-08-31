package com.plot.plugin.road.model.section;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;

import java.util.ArrayList;
import java.util.List;

/**
 * 横断面编辑草稿：认领默认、单条道路、批量编辑共用同一份 schema。
 */
public final class CrossSectionDraft {
    private int width;
    private int laneCount;
    private final List<Integer> laneWidths = new ArrayList<>();
    private MaterialMix material;
    private boolean includeShoulder;
    private int shoulderWidth;
    private boolean includeSidewalk;
    private int sidewalkWidth;
    private String sidewalkMaterial;
    private boolean includeDrainage;
    private boolean includeBikeLane;
    private int bikeLaneWidth;
    private boolean includeMedian;
    private int medianWidth;
    private int streetlightSpacing;
    private boolean laneDividers;
    private CenterLineStyle centerLineStyle;
    private String markingMaterial;
    private boolean includeSlopeBatter;
    private float fillSlopeRatio;
    private float cutSlopeRatio;
    private String fillSlopeMaterial;
    private String cutSlopeMaterial;
    private float maxSlope;

    public static CrossSectionDraft fromConfig(RoadSystemConfig config) {
        CrossSectionDraft draft = new CrossSectionDraft();
        if (config == null) {
            return draft;
        }
        draft.width = config.getRoadWidth();
        draft.laneCount = config.getLaneCount();
        draft.laneWidths.clear();
        draft.laneWidths.addAll(config.getLaneWidths());
        draft.material = config.getSelectedMaterial();
        draft.includeShoulder = config.isIncludeShoulder();
        draft.shoulderWidth = config.getShoulderWidth();
        draft.includeSidewalk = config.isIncludeSidewalk();
        draft.sidewalkWidth = config.getSidewalkWidth();
        draft.sidewalkMaterial = config.getSelectedSidewalkMaterial();
        draft.includeDrainage = config.isIncludeDrainage();
        draft.includeBikeLane = config.isIncludeBikeLane();
        draft.bikeLaneWidth = config.getBikeLaneWidth();
        draft.includeMedian = config.isIncludeMedian();
        draft.medianWidth = config.getMedianWidth();
        draft.streetlightSpacing = config.getStreetlightSpacing();
        draft.laneDividers = config.isLaneDividers();
        draft.centerLineStyle = config.getCenterLineStyle();
        draft.markingMaterial = config.getMarkingMaterial();
        draft.includeSlopeBatter = config.isIncludeSlopeBatter();
        draft.fillSlopeRatio = config.getFillSlopeRatio();
        draft.cutSlopeRatio = config.getCutSlopeRatio();
        draft.fillSlopeMaterial = config.getFillSlopeMaterial();
        draft.cutSlopeMaterial = config.getCutSlopeMaterial();
        draft.maxSlope = config.getMaxSlope();
        draft.syncLaneWidthList();
        return draft;
    }

    public void applyToConfig(RoadSystemConfig config) {
        if (config == null) {
            return;
        }
        config.setRoadWidth(width);
        config.setLaneCount(laneCount);
        config.setLaneWidths(new ArrayList<>(laneWidths));
        config.setSelectedMaterial(material);
        config.setIncludeShoulder(includeShoulder);
        config.setShoulderWidth(shoulderWidth);
        config.setIncludeSidewalk(includeSidewalk);
        config.setSidewalkWidth(sidewalkWidth);
        config.setSelectedSidewalkMaterial(sidewalkMaterial);
        config.setIncludeDrainage(includeDrainage);
        config.setIncludeBikeLane(includeBikeLane);
        config.setBikeLaneWidth(bikeLaneWidth);
        config.setIncludeMedian(includeMedian);
        config.setMedianWidth(medianWidth);
        config.setStreetlightSpacing(streetlightSpacing);
        config.setLaneDividers(laneDividers);
        config.setCenterLineStyle(centerLineStyle);
        config.setMarkingMaterial(markingMaterial);
        config.setIncludeSlopeBatter(includeSlopeBatter);
        config.setFillSlopeRatio(fillSlopeRatio);
        config.setCutSlopeRatio(cutSlopeRatio);
        config.setFillSlopeMaterial(fillSlopeMaterial);
        config.setCutSlopeMaterial(cutSlopeMaterial);
        config.setMaxSlope(maxSlope);
    }

    public static CrossSectionDraft fromBatchDefaults(RoadNetworkManager.BatchEditDefaults defaults) {
        CrossSectionDraft draft = new CrossSectionDraft();
        if (defaults == null) {
            return draft;
        }
        draft.width = defaults.width();
        draft.laneCount = defaults.laneCount();
        draft.material = defaults.material();
        draft.includeShoulder = defaults.includeShoulder();
        draft.shoulderWidth = defaults.shoulderWidth();
        draft.includeSidewalk = defaults.includeSidewalk();
        draft.sidewalkWidth = defaults.sidewalkWidth();
        draft.sidewalkMaterial = defaults.sidewalkMaterial();
        draft.includeDrainage = defaults.includeDrainage();
        draft.includeBikeLane = defaults.includeBikeLane();
        draft.bikeLaneWidth = defaults.bikeLaneWidth();
        draft.includeMedian = defaults.includeMedian();
        draft.medianWidth = defaults.medianWidth();
        draft.streetlightSpacing = defaults.streetlightSpacing();
        draft.laneDividers = defaults.laneDividers();
        draft.centerLineStyle = defaults.centerLineStyle();
        draft.markingMaterial = defaults.markingMaterial();
        draft.includeSlopeBatter = defaults.includeSlopeBatter();
        draft.fillSlopeRatio = defaults.fillSlopeRatio();
        draft.cutSlopeRatio = defaults.cutSlopeRatio();
        draft.fillSlopeMaterial = defaults.fillSlopeMaterial();
        draft.cutSlopeMaterial = defaults.cutSlopeMaterial();
        draft.maxSlope = defaults.maxSlope();
        draft.syncLaneWidthList();
        return draft;
    }

    public RoadNetworkManager.BatchEditDefaults toBatchDefaults() {
        return new RoadNetworkManager.BatchEditDefaults(
            width,
            laneCount,
            material,
            includeShoulder,
            shoulderWidth,
            includeSidewalk,
            sidewalkWidth,
            sidewalkMaterial,
            includeDrainage,
            includeBikeLane,
            bikeLaneWidth,
            includeMedian,
            medianWidth,
            streetlightSpacing,
            laneDividers,
            centerLineStyle,
            markingMaterial,
            includeSlopeBatter,
            fillSlopeRatio,
            cutSlopeRatio,
            fillSlopeMaterial,
            cutSlopeMaterial,
            maxSlope
        );
    }

    public RoadCrossSection toCrossSection() {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        section.getCarriageway().setLaneCount(laneCount);
        section.getCarriageway().setMaterial(material);
        section.getCarriageway().syncLaneCount(laneCount);
        List<Integer> resolved = section.getCarriageway().resolveLaneWidths(width);
        for (int i = 0; i < laneCount; i++) {
            int laneWidth = i < laneWidths.size() && laneWidths.get(i) > 0
                ? laneWidths.get(i)
                : resolved.get(i);
            section.getCarriageway().setLaneWidthAt(i, laneWidth);
        }
        section.getShoulder().setEnabled(includeShoulder);
        section.getShoulder().setWidth(shoulderWidth);
        section.getSidewalk().setEnabled(includeSidewalk);
        section.getSidewalk().setWidth(sidewalkWidth);
        section.getSidewalk().setMaterial(sidewalkMaterial);
        section.getDrain().setEnabled(includeDrainage);
        section.getBikeLane().setEnabled(includeBikeLane);
        section.getBikeLane().setWidth(bikeLaneWidth);
        section.getMedian().setEnabled(includeMedian);
        section.getMedian().setWidth(medianWidth);
        section.getStreetFurniture().setStreetlightSpacing(streetlightSpacing);
        section.getMarkings().setLaneDividers(laneDividers);
        section.getMarkings().setCenterLineStyle(centerLineStyle);
        section.getMarkings().setMaterial(markingMaterial);
        section.getSlopeBatter().setEnabled(includeSlopeBatter);
        section.getSlopeBatter().setFillRatio(fillSlopeRatio);
        section.getSlopeBatter().setCutRatio(cutSlopeRatio);
        section.getSlopeBatter().setFillMaterial(fillSlopeMaterial);
        section.getSlopeBatter().setCutMaterial(cutSlopeMaterial);
        return section;
    }

    public static CrossSectionDraft fromRoad(Road road, RoadSystemConfig defaults) {
        CrossSectionDraft draft = fromConfig(defaults);
        if (road == null) {
            return draft;
        }
        if (road.getWidth() != null) {
            draft.width = road.getWidth();
        }
        draft.laneCount = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
        draft.laneWidths.clear();
        List<Integer> resolved = road.getCrossSection().getCarriageway().resolveLaneWidths(draft.width);
        for (int i = 0; i < draft.laneCount; i++) {
            List<Lane> lanes = road.getCrossSection().getCarriageway().getLanes();
            Lane lane = i < lanes.size() ? lanes.get(i) : null;
            draft.laneWidths.add(lane != null && lane.getWidth() != null ? lane.getWidth() : resolved.get(i));
        }
        if (road.getMaterial() != null) {
            draft.material = road.getMaterial();
        }
        if (road.getIncludeShoulder() != null) {
            draft.includeShoulder = road.getIncludeShoulder();
        } else {
            draft.includeShoulder = road.getEffectiveIncludeShoulder(defaults);
        }
        if (road.getShoulderWidth() != null) {
            draft.shoulderWidth = road.getShoulderWidth();
        }
        if (road.getIncludeSidewalk() != null) {
            draft.includeSidewalk = road.getIncludeSidewalk();
        } else {
            draft.includeSidewalk = road.getEffectiveIncludeSidewalk(defaults);
        }
        if (road.getSidewalkWidth() != null) {
            draft.sidewalkWidth = road.getSidewalkWidth();
        }
        if (road.getSidewalkMaterial() != null) {
            draft.sidewalkMaterial = road.getSidewalkMaterial();
        }
        if (road.getIncludeDrainage() != null) {
            draft.includeDrainage = road.getIncludeDrainage();
        } else {
            draft.includeDrainage = road.getEffectiveIncludeDrainage(defaults);
        }
        if (road.getIncludeBikeLane() != null) {
            draft.includeBikeLane = road.getIncludeBikeLane();
        } else {
            draft.includeBikeLane = road.getEffectiveIncludeBikeLane(defaults);
        }
        if (road.getBikeLaneWidth() != null) {
            draft.bikeLaneWidth = road.getBikeLaneWidth();
        }
        if (road.getIncludeMedian() != null) {
            draft.includeMedian = road.getIncludeMedian();
        } else {
            draft.includeMedian = false;
        }
        if (road.getMedianWidth() != null) {
            draft.medianWidth = road.getMedianWidth();
        }
        draft.streetlightSpacing = road.getStreetlightSpacing() != null
            ? road.getStreetlightSpacing()
            : 0;
        if (road.getLaneDividers() != null) {
            draft.laneDividers = road.getLaneDividers();
        } else {
            draft.laneDividers = draft.laneCount > 1;
        }
        draft.centerLineStyle = road.getCenterLineStyle() != null
            ? road.getCenterLineStyle()
            : CenterLineStyle.NONE;
        draft.markingMaterial = road.getMarkingMaterial() != null
            ? road.getMarkingMaterial()
            : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL;
        if (road.getIncludeSlopeBatter() != null) {
            draft.includeSlopeBatter = road.getIncludeSlopeBatter();
        } else {
            draft.includeSlopeBatter = road.getEffectiveIncludeSlopeBatter(defaults);
        }
        if (road.getFillSlopeRatio() != null) {
            draft.fillSlopeRatio = road.getFillSlopeRatio();
        } else {
            draft.fillSlopeRatio = road.getEffectiveFillSlopeRatio(defaults);
        }
        if (road.getCutSlopeRatio() != null) {
            draft.cutSlopeRatio = road.getCutSlopeRatio();
        } else {
            draft.cutSlopeRatio = road.getEffectiveCutSlopeRatio(defaults);
        }
        if (road.getFillSlopeMaterial() != null) {
            draft.fillSlopeMaterial = road.getFillSlopeMaterial();
        } else {
            draft.fillSlopeMaterial = road.getEffectiveFillSlopeMaterial(defaults);
        }
        if (road.getCutSlopeMaterial() != null) {
            draft.cutSlopeMaterial = road.getCutSlopeMaterial();
        } else {
            draft.cutSlopeMaterial = road.getEffectiveCutSlopeMaterial(defaults);
        }
        if (road.getMaxSlope() != null) {
            draft.maxSlope = road.getMaxSlope();
        } else {
            draft.maxSlope = defaults.getMaxSlope();
        }
        return draft;
    }

    public static CrossSectionDraft fromCrossSection(RoadCrossSection section, RoadSystemConfig defaults) {
        CrossSectionDraft draft = fromConfig(defaults);
        if (section == null) {
            return draft;
        }
        ResolvedCrossSection resolved = section.resolve(defaults);
        draft.width = resolved.carriagewayWidth;
        draft.laneCount = resolved.laneCount;
        draft.laneWidths.clear();
        draft.laneWidths.addAll(section.getCarriageway().resolveLaneWidths(draft.width));
        draft.material = section.getCarriageway().getMaterial() != null
            ? section.getCarriageway().getMaterial()
            : resolved.carriagewayMaterial;
        draft.includeShoulder = resolved.includeShoulder;
        draft.shoulderWidth = resolved.shoulderWidth;
        draft.includeSidewalk = resolved.includeSidewalk;
        draft.sidewalkWidth = resolved.sidewalkWidth;
        draft.sidewalkMaterial = resolved.sidewalkMaterial;
        draft.includeDrainage = resolved.includeDrain;
        draft.includeBikeLane = resolved.includeBikeLane;
        draft.bikeLaneWidth = resolved.bikeLaneWidth;
        draft.includeMedian = resolved.includeMedian;
        draft.medianWidth = resolved.medianWidth;
        draft.streetlightSpacing = resolved.streetlightSpacing != null ? resolved.streetlightSpacing : 0;
        draft.laneDividers = resolved.laneDividers;
        draft.centerLineStyle = resolved.centerLineStyle != null
            ? resolved.centerLineStyle
            : CenterLineStyle.NONE;
        draft.markingMaterial = resolved.markingMaterial;
        draft.includeSlopeBatter = resolved.includeSlopeBatter;
        draft.fillSlopeRatio = resolved.fillSlopeRatio;
        draft.cutSlopeRatio = resolved.cutSlopeRatio;
        draft.fillSlopeMaterial = resolved.fillSlopeMaterial;
        draft.cutSlopeMaterial = resolved.cutSlopeMaterial;
        draft.syncLaneWidthList();
        return draft;
    }

    public void applyToRoad(Road road) {
        if (road == null) {
            return;
        }
        road.setWidth(width);
        road.setLaneCount(laneCount);
        road.getCrossSection().getCarriageway().syncLaneCount(laneCount);
        for (int i = 0; i < laneCount; i++) {
            if (i < laneWidths.size() && laneWidths.get(i) > 0) {
                road.getCrossSection().getCarriageway().setLaneWidthAt(i, laneWidths.get(i));
            }
        }
        road.setMaterial(material);
        road.setIncludeShoulder(includeShoulder);
        if (includeShoulder) {
            road.setShoulderWidth(shoulderWidth);
        }
        road.setIncludeSidewalk(includeSidewalk);
        if (includeSidewalk) {
            road.setSidewalkWidth(sidewalkWidth);
            road.setSidewalkMaterial(sidewalkMaterial);
        }
        road.setIncludeDrainage(includeDrainage);
        road.setIncludeBikeLane(includeBikeLane);
        if (includeBikeLane) {
            road.setBikeLaneWidth(bikeLaneWidth);
        }
        road.setIncludeMedian(includeMedian);
        if (includeMedian) {
            road.setMedianWidth(medianWidth);
        }
        road.setStreetlightSpacing(streetlightSpacing);
        road.setLaneDividers(laneDividers);
        road.setCenterLineStyle(centerLineStyle);
        road.setMarkingMaterial(markingMaterial);
        road.setIncludeSlopeBatter(includeSlopeBatter);
        if (includeSlopeBatter) {
            road.setFillSlopeRatio(fillSlopeRatio);
            road.setCutSlopeRatio(cutSlopeRatio);
            road.setFillSlopeMaterial(fillSlopeMaterial);
            road.setCutSlopeMaterial(cutSlopeMaterial);
        }
        road.setMaxSlope(maxSlope);
    }

    public int width() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int laneCount() {
        return laneCount;
    }

    public void setLaneCount(int laneCount) {
        this.laneCount = Math.max(1, laneCount);
        syncLaneWidthList();
    }

    public List<Integer> laneWidths() {
        return List.copyOf(laneWidths);
    }

    public void setLaneWidthAt(int index, int laneWidth) {
        syncLaneWidthList();
        if (index >= 0 && index < laneWidths.size()) {
            laneWidths.set(index, laneWidth);
        }
    }

    public MaterialMix material() {
        return material;
    }

    public void setMaterial(MaterialMix material) {
        this.material = material;
    }

    public boolean includeShoulder() {
        return includeShoulder;
    }

    public void setIncludeShoulder(boolean includeShoulder) {
        this.includeShoulder = includeShoulder;
    }

    public int shoulderWidth() {
        return shoulderWidth;
    }

    public void setShoulderWidth(int shoulderWidth) {
        this.shoulderWidth = shoulderWidth;
    }

    public boolean includeSidewalk() {
        return includeSidewalk;
    }

    public void setIncludeSidewalk(boolean includeSidewalk) {
        this.includeSidewalk = includeSidewalk;
    }

    public int sidewalkWidth() {
        return sidewalkWidth;
    }

    public void setSidewalkWidth(int sidewalkWidth) {
        this.sidewalkWidth = sidewalkWidth;
    }

    public String sidewalkMaterial() {
        return sidewalkMaterial;
    }

    public void setSidewalkMaterial(String sidewalkMaterial) {
        this.sidewalkMaterial = sidewalkMaterial;
    }

    public boolean includeDrainage() {
        return includeDrainage;
    }

    public void setIncludeDrainage(boolean includeDrainage) {
        this.includeDrainage = includeDrainage;
    }

    public boolean includeBikeLane() {
        return includeBikeLane;
    }

    public void setIncludeBikeLane(boolean includeBikeLane) {
        this.includeBikeLane = includeBikeLane;
    }

    public int bikeLaneWidth() {
        return bikeLaneWidth;
    }

    public void setBikeLaneWidth(int bikeLaneWidth) {
        this.bikeLaneWidth = bikeLaneWidth;
    }

    public boolean includeMedian() {
        return includeMedian;
    }

    public void setIncludeMedian(boolean includeMedian) {
        this.includeMedian = includeMedian;
    }

    public int medianWidth() {
        return medianWidth;
    }

    public void setMedianWidth(int medianWidth) {
        this.medianWidth = medianWidth;
    }

    public int streetlightSpacing() {
        return streetlightSpacing;
    }

    public void setStreetlightSpacing(int streetlightSpacing) {
        this.streetlightSpacing = streetlightSpacing;
    }

    public boolean laneDividers() {
        return laneDividers;
    }

    public void setLaneDividers(boolean laneDividers) {
        this.laneDividers = laneDividers;
    }

    public CenterLineStyle centerLineStyle() {
        return centerLineStyle;
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        this.centerLineStyle = centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE;
    }

    public String markingMaterial() {
        return markingMaterial;
    }

    public void setMarkingMaterial(String markingMaterial) {
        this.markingMaterial = markingMaterial;
    }

    public boolean includeSlopeBatter() {
        return includeSlopeBatter;
    }

    public void setIncludeSlopeBatter(boolean includeSlopeBatter) {
        this.includeSlopeBatter = includeSlopeBatter;
    }

    public float fillSlopeRatio() {
        return fillSlopeRatio;
    }

    public void setFillSlopeRatio(float fillSlopeRatio) {
        this.fillSlopeRatio = fillSlopeRatio;
    }

    public float cutSlopeRatio() {
        return cutSlopeRatio;
    }

    public void setCutSlopeRatio(float cutSlopeRatio) {
        this.cutSlopeRatio = cutSlopeRatio;
    }

    public String fillSlopeMaterial() {
        return fillSlopeMaterial;
    }

    public void setFillSlopeMaterial(String fillSlopeMaterial) {
        this.fillSlopeMaterial = fillSlopeMaterial;
    }

    public String cutSlopeMaterial() {
        return cutSlopeMaterial;
    }

    public void setCutSlopeMaterial(String cutSlopeMaterial) {
        this.cutSlopeMaterial = cutSlopeMaterial;
    }

    public float maxSlope() {
        return maxSlope;
    }

    public void setMaxSlope(float maxSlope) {
        this.maxSlope = maxSlope;
    }

    private void syncLaneWidthList() {
        while (laneWidths.size() < laneCount) {
            laneWidths.add(0);
        }
        while (laneWidths.size() > laneCount) {
            laneWidths.removeLast();
        }
    }

    public List<Integer> resolveLaneWidths() {
        RoadCrossSection section = toCrossSection();
        return section.getCarriageway().resolveLaneWidths(width);
    }
}
