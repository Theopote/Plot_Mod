package com.plot.plugin.road.model.section;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.RoadParameterLimits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 横断面编辑草稿：认领默认、单条道路、批量编辑共用同一份 schema。
 */
public final class CrossSectionDraft {
    private int width;
    private int laneCount;
    private int defaultLaneWidth = RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS;
    private final List<Integer> laneWidths = new ArrayList<>();
    private MaterialMix material;
    private boolean includeShoulder;
    private int shoulderWidth;
    private boolean includeSidewalk;
    private int sidewalkWidth;
    private String sidewalkMaterial;
    private boolean includeBikeLane;
    private int bikeLaneWidth;
    private boolean includeMedian;
    private int medianWidth;
    private int medianHeight = RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS;
    private int streetlightSpacing;
    private boolean laneDividers;
    private CenterLineStyle centerLineStyle;
    private String markingMaterial;
    private RoadsideEdgeMode roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER;
    private float fillSlopeRatio;
    private float cutSlopeRatio;
    private String fillSlopeMaterial;
    private String cutSlopeMaterial;
    private String retainingWallMaterial = "minecraft:stone_bricks";
    private float maxSlope;

    public static CrossSectionDraft fromConfig(RoadSystemConfig config) {
        CrossSectionDraft draft = new CrossSectionDraft();
        if (config == null) {
            return draft;
        }
        draft.width = config.getRoadWidth();
        draft.laneCount = config.getLaneCount();
        draft.defaultLaneWidth = config.getDefaultLaneWidth();
        draft.laneWidths.clear();
        draft.laneWidths.addAll(config.getLaneWidths());
        draft.material = config.getSelectedMaterial();
        draft.includeShoulder = config.isIncludeShoulder();
        draft.shoulderWidth = config.getShoulderWidth();
        draft.includeSidewalk = config.isIncludeSidewalk();
        draft.sidewalkWidth = config.getSidewalkWidth();
        draft.sidewalkMaterial = config.getSelectedSidewalkMaterial();
        draft.includeBikeLane = config.isIncludeBikeLane();
        draft.bikeLaneWidth = config.getBikeLaneWidth();
        draft.includeMedian = config.isIncludeMedian();
        draft.medianWidth = config.getMedianWidth();
        draft.medianHeight = config.getMedianHeight();
        draft.streetlightSpacing = config.getStreetlightSpacing();
        draft.laneDividers = config.isLaneDividers();
        draft.centerLineStyle = config.getCenterLineStyle();
        draft.markingMaterial = config.getMarkingMaterial();
        draft.roadsideEdgeMode = config.getRoadsideEdgeMode();
        draft.fillSlopeRatio = config.getFillSlopeRatio();
        draft.cutSlopeRatio = config.getCutSlopeRatio();
        draft.fillSlopeMaterial = config.getFillSlopeMaterial();
        draft.cutSlopeMaterial = config.getCutSlopeMaterial();
        draft.retainingWallMaterial = config.getRetainingWallMaterial();
        draft.maxSlope = config.getMaxSlope();
        draft.syncLaneWidthList();
        draft.recomputeDerivedWidth();
        return draft;
    }

    public void applyToConfig(RoadSystemConfig config) {
        if (config == null) {
            return;
        }
        recomputeDerivedWidth();
        config.setDefaultLaneWidth(defaultLaneWidth);
        config.setLaneCount(laneCount);
        config.setLaneWidths(new ArrayList<>(laneWidths));
        config.setRoadWidth(width);
        config.setSelectedMaterial(material);
        config.setIncludeShoulder(includeShoulder);
        config.setShoulderWidth(shoulderWidth);
        config.setIncludeSidewalk(includeSidewalk);
        config.setSidewalkWidth(sidewalkWidth);
        config.setSelectedSidewalkMaterial(sidewalkMaterial);
        config.setIncludeBikeLane(includeBikeLane);
        config.setBikeLaneWidth(bikeLaneWidth);
        config.setIncludeMedian(includeMedian);
        config.setMedianWidth(medianWidth);
        config.setMedianHeight(medianHeight);
        config.setStreetlightSpacing(streetlightSpacing);
        config.setLaneDividers(laneDividers);
        config.setCenterLineStyle(centerLineStyle);
        config.setMarkingMaterial(markingMaterial);
        config.setRoadsideEdgeMode(roadsideEdgeMode);
        config.setFillSlopeRatio(fillSlopeRatio);
        config.setCutSlopeRatio(cutSlopeRatio);
        config.setFillSlopeMaterial(fillSlopeMaterial);
        config.setCutSlopeMaterial(cutSlopeMaterial);
        config.setRetainingWallMaterial(retainingWallMaterial);
        config.setMaxSlope(maxSlope);
    }

    public static CrossSectionDraft fromBatchDefaults(RoadNetworkManager.BatchEditDefaults defaults) {
        CrossSectionDraft draft = new CrossSectionDraft();
        if (defaults == null) {
            return draft;
        }
        draft.width = defaults.width();
        draft.laneCount = defaults.laneCount();
        draft.defaultLaneWidth = RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS;
        draft.material = defaults.material();
        draft.includeShoulder = defaults.includeShoulder();
        draft.shoulderWidth = defaults.shoulderWidth();
        draft.includeSidewalk = defaults.includeSidewalk();
        draft.sidewalkWidth = defaults.sidewalkWidth();
        draft.sidewalkMaterial = defaults.sidewalkMaterial();
        draft.includeBikeLane = defaults.includeBikeLane();
        draft.bikeLaneWidth = defaults.bikeLaneWidth();
        draft.includeMedian = defaults.includeMedian();
        draft.medianWidth = defaults.medianWidth();
        draft.medianHeight = RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS;
        draft.streetlightSpacing = defaults.streetlightSpacing();
        draft.laneDividers = defaults.laneDividers();
        draft.centerLineStyle = defaults.centerLineStyle();
        draft.markingMaterial = defaults.markingMaterial();
        draft.roadsideEdgeMode = defaults.includeRetainingWall()
            ? RoadsideEdgeMode.RETAINING_WALL
            : (defaults.includeSlopeBatter() ? RoadsideEdgeMode.SLOPE_BATTER : RoadsideEdgeMode.NONE);
        draft.fillSlopeRatio = defaults.fillSlopeRatio();
        draft.cutSlopeRatio = defaults.cutSlopeRatio();
        draft.fillSlopeMaterial = defaults.fillSlopeMaterial();
        draft.cutSlopeMaterial = defaults.cutSlopeMaterial();
        draft.retainingWallMaterial = defaults.retainingWallMaterial();
        draft.maxSlope = defaults.maxSlope();
        draft.syncLaneWidthList();
        draft.migrateFromStoredWidthIfNeeded();
        draft.recomputeDerivedWidth();
        return draft;
    }

    public RoadNetworkManager.BatchEditDefaults toBatchDefaults() {
        return toBatchDefaultsPreservingBridge(null);
    }

    /**
     * 横断面字段来自本草稿；桥梁结构字段从 {@code bridgeSource} 保留（缺省用记录默认值）。
     */
    public RoadNetworkManager.BatchEditDefaults toBatchDefaultsPreservingBridge(
            RoadNetworkManager.BatchEditDefaults bridgeSource) {
        RoadNetworkManager.BatchEditDefaults crossSection = new RoadNetworkManager.BatchEditDefaults(
            width,
            laneCount,
            material,
            includeShoulder,
            shoulderWidth,
            includeSidewalk,
            sidewalkWidth,
            sidewalkMaterial,
            includeBikeLane,
            bikeLaneWidth,
            includeMedian,
            medianWidth,
            streetlightSpacing,
            laneDividers,
            centerLineStyle,
            markingMaterial,
            includeSlopeBatter(),
            includeRetainingWall(),
            fillSlopeRatio,
            cutSlopeRatio,
            fillSlopeMaterial,
            cutSlopeMaterial,
            retainingWallMaterial,
            maxSlope
        );
        if (bridgeSource == null) {
            return crossSection;
        }
        return bridgeSource.mergeCrossSectionFrom(crossSection);
    }

    public RoadCrossSection toCrossSection() {
        RoadCrossSection section = new RoadCrossSection();
        applyExposedFieldsOnto(section);
        return section;
    }

    /**
     * Writes every draft-exposed field onto {@code section}. Fields the draft schema
     * does not cover (shoulder / median / bike materials, null inheritance) are left alone.
     */
    public void applyExposedFieldsOnto(RoadCrossSection section) {
        if (section == null) {
            return;
        }
        section.getCarriageway().setLaneCount(laneCount);
        section.getCarriageway().setDefaultLaneWidth(defaultLaneWidth);
        section.getCarriageway().setMaterial(material);
        section.getCarriageway().syncLaneCount(laneCount);
        boolean anyExplicit = false;
        for (int i = 0; i < laneCount; i++) {
            if (i < laneWidths.size() && laneWidths.get(i) != null && laneWidths.get(i) > 0) {
                section.getCarriageway().setLaneWidthAt(i, laneWidths.get(i));
                anyExplicit = true;
            }
        }
        if (!anyExplicit) {
            section.getCarriageway().setDefaultLaneWidth(defaultLaneWidth);
        }
        section.getCarriageway().syncDerivedWidth(centerLineStyle, laneDividers, 0);
        width = section.getCarriageway().getWidth() != null ? section.getCarriageway().getWidth() : width;
        section.getShoulder().setEnabled(includeShoulder);
        section.getShoulder().setWidth(shoulderWidth);
        section.getSidewalk().setEnabled(includeSidewalk);
        section.getSidewalk().setWidth(sidewalkWidth);
        section.getSidewalk().setMaterial(sidewalkMaterial);
        section.getBikeLane().setEnabled(includeBikeLane);
        section.getBikeLane().setWidth(bikeLaneWidth);
        section.getMedian().setEnabled(includeMedian);
        section.getMedian().setWidth(
            includeMedian ? RoadParameterLimits.clampMedianWidthBlocks(width, medianWidth) : medianWidth);
        section.getMedian().setHeight(medianHeight);
        section.getStreetFurniture().setStreetlightSpacing(streetlightSpacing);
        section.getMarkings().setLaneDividers(laneDividers);
        section.getMarkings().setCenterLineStyle(centerLineStyle);
        section.getMarkings().setMaterial(markingMaterial);
        section.getSlopeBatter().setMode(roadsideEdgeMode);
        section.getSlopeBatter().setFillRatio(fillSlopeRatio);
        section.getSlopeBatter().setCutRatio(cutSlopeRatio);
        section.getSlopeBatter().setFillMaterial(fillSlopeMaterial);
        section.getSlopeBatter().setCutMaterial(cutSlopeMaterial);
        section.getSlopeBatter().setWallMaterial(retainingWallMaterial);
    }

    /**
     * Overlays only fields that differ from {@code baseline} onto {@code section}.
     * Used when rebuilding from an opened original so unedited exposed fields keep
     * null inheritance, and unexposed materials survive a single-field edit.
     */
    public void applyChangedFieldsOnto(RoadCrossSection section, CrossSectionDraft baseline) {
        if (section == null) {
            return;
        }
        if (baseline == null) {
            applyExposedFieldsOnto(section);
            return;
        }
        boolean lanesChanged = laneCount != baseline.laneCount
            || !laneWidths.equals(baseline.laneWidths)
            || defaultLaneWidth != baseline.defaultLaneWidth;
        boolean markingsChanged = laneDividers != baseline.laneDividers
            || centerLineStyle != baseline.centerLineStyle;
        boolean medianChanged = includeMedian != baseline.includeMedian
            || medianWidth != baseline.medianWidth
            || medianHeight != baseline.medianHeight;
        if (lanesChanged) {
            section.getCarriageway().setLaneCount(laneCount);
            section.getCarriageway().setDefaultLaneWidth(defaultLaneWidth);
            section.getCarriageway().syncLaneCount(laneCount);
            for (int i = 0; i < laneCount; i++) {
                if (i < laneWidths.size() && laneWidths.get(i) != null && laneWidths.get(i) > 0) {
                    section.getCarriageway().setLaneWidthAt(i, laneWidths.get(i));
                }
            }
        }
        // 仅在车道/标线实际改动时写回派生总宽；隔离带不占行车道宽。
        if (lanesChanged || markingsChanged) {
            CenterLineStyle style = centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE;
            section.getCarriageway().syncDerivedWidth(style, laneDividers, 0);
            width = section.getCarriageway().getWidth() != null ? section.getCarriageway().getWidth() : width;
        } else if (width != baseline.width && baseline.width > 0) {
            section.getCarriageway().setWidth(width);
        }
        if (medianChanged) {
            section.getMedian().setEnabled(includeMedian);
            section.getMedian().setWidth(
                includeMedian ? RoadParameterLimits.clampMedianWidthBlocks(width, medianWidth) : medianWidth);
            section.getMedian().setHeight(medianHeight);
        }
        if (!materialsEqual(material, baseline.material)) {
            section.getCarriageway().setMaterial(material);
        }
        if (includeShoulder != baseline.includeShoulder) {
            section.getShoulder().setEnabled(includeShoulder);
        }
        if (shoulderWidth != baseline.shoulderWidth) {
            section.getShoulder().setWidth(shoulderWidth);
        }
        if (includeSidewalk != baseline.includeSidewalk) {
            section.getSidewalk().setEnabled(includeSidewalk);
        }
        if (sidewalkWidth != baseline.sidewalkWidth) {
            section.getSidewalk().setWidth(sidewalkWidth);
        }
        if (!java.util.Objects.equals(sidewalkMaterial, baseline.sidewalkMaterial)) {
            section.getSidewalk().setMaterial(sidewalkMaterial);
        }
        if (includeBikeLane != baseline.includeBikeLane) {
            section.getBikeLane().setEnabled(includeBikeLane);
        }
        if (bikeLaneWidth != baseline.bikeLaneWidth) {
            section.getBikeLane().setWidth(bikeLaneWidth);
        }
        if (streetlightSpacing != baseline.streetlightSpacing) {
            section.getStreetFurniture().setStreetlightSpacing(streetlightSpacing);
        }
        if (laneDividers != baseline.laneDividers) {
            section.getMarkings().setLaneDividers(laneDividers);
        }
        if (centerLineStyle != baseline.centerLineStyle) {
            section.getMarkings().setCenterLineStyle(centerLineStyle);
        }
        if (!java.util.Objects.equals(markingMaterial, baseline.markingMaterial)) {
            section.getMarkings().setMaterial(markingMaterial);
        }
        if (roadsideEdgeMode != baseline.roadsideEdgeMode) {
            section.getSlopeBatter().setMode(roadsideEdgeMode);
        }
        if (Float.compare(fillSlopeRatio, baseline.fillSlopeRatio) != 0) {
            section.getSlopeBatter().setFillRatio(fillSlopeRatio);
        }
        if (Float.compare(cutSlopeRatio, baseline.cutSlopeRatio) != 0) {
            section.getSlopeBatter().setCutRatio(cutSlopeRatio);
        }
        if (!java.util.Objects.equals(fillSlopeMaterial, baseline.fillSlopeMaterial)) {
            section.getSlopeBatter().setFillMaterial(fillSlopeMaterial);
        }
        if (!java.util.Objects.equals(cutSlopeMaterial, baseline.cutSlopeMaterial)) {
            section.getSlopeBatter().setCutMaterial(cutSlopeMaterial);
        }
        if (!java.util.Objects.equals(retainingWallMaterial, baseline.retainingWallMaterial)) {
            section.getSlopeBatter().setWallMaterial(retainingWallMaterial);
        }
    }

    public static CrossSectionDraft fromRoad(Road road, RoadSystemConfig defaults) {
        CrossSectionDraft draft = fromConfig(defaults);
        if (road == null) {
            return draft;
        }
        draft.laneCount = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
        draft.defaultLaneWidth = road.getCrossSection().getCarriageway().getEffectiveDefaultLaneWidth();
        draft.laneWidths.clear();
        List<Integer> resolved = road.getCrossSection().getCarriageway().resolveLaneWidths();
        for (int i = 0; i < draft.laneCount; i++) {
            List<Lane> lanes = road.getCrossSection().getCarriageway().getLanes();
            Lane lane = i < lanes.size() ? lanes.get(i) : null;
            if (lane != null && lane.getWidth() != null && lane.getWidth() > 0) {
                draft.laneWidths.add(lane.getWidth());
            } else {
                draft.laneWidths.add(0);
            }
        }
        if (road.getWidth() != null) {
            draft.width = road.getWidth();
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
        if (road.getMedianHeight() != null) {
            draft.medianHeight = road.getMedianHeight();
        } else {
            draft.medianHeight = road.getCrossSection().getMedian().getEffectiveHeight();
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
        if (road.getRoadsideEdgeMode() != null) {
            draft.roadsideEdgeMode = road.getRoadsideEdgeMode();
        } else {
            draft.roadsideEdgeMode = road.getEffectiveRoadsideEdgeMode(defaults);
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
        if (road.getRetainingWallMaterial() != null && !road.getRetainingWallMaterial().isBlank()) {
            draft.retainingWallMaterial = road.getRetainingWallMaterial();
        } else {
            draft.retainingWallMaterial = road.getEffectiveRetainingWallMaterial(defaults);
        }
        if (road.getMaxSlope() != null) {
            draft.maxSlope = road.getMaxSlope();
        } else {
            draft.maxSlope = defaults.getMaxSlope();
        }
        draft.syncLaneWidthList();
        draft.migrateFromStoredWidthIfNeeded();
        draft.recomputeDerivedWidth();
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
        draft.defaultLaneWidth = section.getCarriageway().getEffectiveDefaultLaneWidth();
        draft.laneWidths.clear();
        List<Lane> lanes = section.getCarriageway().getLanes();
        for (int i = 0; i < draft.laneCount; i++) {
            Lane lane = i < lanes.size() ? lanes.get(i) : null;
            draft.laneWidths.add(lane != null && lane.getWidth() != null && lane.getWidth() > 0
                ? lane.getWidth()
                : 0);
        }
        draft.material = section.getCarriageway().getMaterial() != null
            ? section.getCarriageway().getMaterial()
            : resolved.carriagewayMaterial;
        draft.includeShoulder = resolved.includeShoulder;
        draft.shoulderWidth = resolved.shoulderWidth;
        draft.includeSidewalk = resolved.includeSidewalk;
        draft.sidewalkWidth = resolved.sidewalkWidth;
        draft.sidewalkMaterial = resolved.sidewalkMaterial;
        draft.includeBikeLane = resolved.includeBikeLane;
        draft.bikeLaneWidth = resolved.bikeLaneWidth;
        draft.includeMedian = resolved.includeMedian;
        draft.medianWidth = resolved.medianWidth;
        draft.medianHeight = resolved.medianHeight > 0
            ? resolved.medianHeight
            : RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS;
        draft.streetlightSpacing = resolved.streetlightSpacing != null ? resolved.streetlightSpacing : 0;
        draft.laneDividers = resolved.laneDividers;
        draft.centerLineStyle = resolved.centerLineStyle != null
            ? resolved.centerLineStyle
            : CenterLineStyle.NONE;
        draft.markingMaterial = resolved.markingMaterial;
        draft.roadsideEdgeMode = resolved.roadsideEdgeMode();
        draft.fillSlopeRatio = resolved.fillSlopeRatio;
        draft.cutSlopeRatio = resolved.cutSlopeRatio;
        draft.fillSlopeMaterial = resolved.fillSlopeMaterial;
        draft.cutSlopeMaterial = resolved.cutSlopeMaterial;
        draft.retainingWallMaterial = resolved.retainingWallMaterial;
        draft.syncLaneWidthList();
        draft.recomputeDerivedWidth();
        return draft;
    }

    public int width() {
        return width;
    }

    public void setWidth(int width) {
        // 兼容旧入口：把总宽反推为均匀单车道宽。
        this.width = width;
        migrateFromStoredWidthIfNeeded();
        recomputeDerivedWidth();
    }

    public int laneCount() {
        return laneCount;
    }

    public void setLaneCount(int laneCount) {
        this.laneCount = Math.max(1, laneCount);
        syncLaneWidthList();
        recomputeDerivedWidth();
    }

    public int defaultLaneWidth() {
        return defaultLaneWidth;
    }

    public void setDefaultLaneWidth(int defaultLaneWidth) {
        this.defaultLaneWidth = RoadParameterLimits.clampLaneTravelWidth(defaultLaneWidth);
        // 均匀模式：清空显式分车道覆盖
        Collections.fill(laneWidths, 0);
        recomputeDerivedWidth();
    }

    public List<Integer> laneWidths() {
        return List.copyOf(laneWidths);
    }

    public void setLaneWidthAt(int index, int laneWidth) {
        syncLaneWidthList();
        if (index >= 0 && index < laneWidths.size()) {
            laneWidths.set(index, RoadParameterLimits.clampLaneTravelWidth(laneWidth));
            recomputeDerivedWidth();
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
        this.medianWidth = Math.max(0, medianWidth);
    }

    public int medianHeight() {
        return medianHeight;
    }

    public void setMedianHeight(int medianHeight) {
        this.medianHeight = RoadParameterLimits.clampMedianHeightBlocks(medianHeight);
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
        recomputeDerivedWidth();
    }

    public CenterLineStyle centerLineStyle() {
        return centerLineStyle;
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        this.centerLineStyle = centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE;
        recomputeDerivedWidth();
    }

    public String markingMaterial() {
        return markingMaterial;
    }

    public void setMarkingMaterial(String markingMaterial) {
        this.markingMaterial = markingMaterial;
    }

    public RoadsideEdgeMode roadsideEdgeMode() {
        return roadsideEdgeMode != null ? roadsideEdgeMode : RoadsideEdgeMode.NONE;
    }

    public void setRoadsideEdgeMode(RoadsideEdgeMode roadsideEdgeMode) {
        this.roadsideEdgeMode = roadsideEdgeMode != null ? roadsideEdgeMode : RoadsideEdgeMode.NONE;
    }

    public boolean includeSlopeBatter() {
        return roadsideEdgeMode().isSlopeBatter();
    }

    public void setIncludeSlopeBatter(boolean includeSlopeBatter) {
        this.roadsideEdgeMode = includeSlopeBatter
            ? RoadsideEdgeMode.SLOPE_BATTER
            : RoadsideEdgeMode.NONE;
    }

    public boolean includeRetainingWall() {
        return roadsideEdgeMode().isRetainingWall();
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

    public String retainingWallMaterial() {
        return retainingWallMaterial;
    }

    public void setRetainingWallMaterial(String retainingWallMaterial) {
        this.retainingWallMaterial = retainingWallMaterial != null && !retainingWallMaterial.isBlank()
            ? retainingWallMaterial
            : "minecraft:stone_bricks";
    }

    public float maxSlope() {
        return maxSlope;
    }

    public void setMaxSlope(float maxSlope) {
        this.maxSlope = maxSlope;
    }

    private static boolean materialsEqual(MaterialMix left, MaterialMix right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return java.util.Objects.equals(left.getPrimaryMaterial(), right.getPrimaryMaterial())
            && java.util.Objects.equals(left.getAccentMaterial(), right.getAccentMaterial())
            && Float.compare(left.getAccentRatio(), right.getAccentRatio()) == 0;
    }

    private void syncLaneWidthList() {
        while (laneWidths.size() < laneCount) {
            laneWidths.add(0);
        }
        while (laneWidths.size() > laneCount) {
            laneWidths.removeLast();
        }
    }

    private boolean hasExplicitLaneWidths() {
        for (Integer w : laneWidths) {
            if (w != null && w > 0) {
                return true;
            }
        }
        return false;
    }

    private void migrateFromStoredWidthIfNeeded() {
        if (hasExplicitLaneWidths()) {
            return;
        }
        defaultLaneWidth = CarriagewayWidthRules.inferUniformLaneWidth(
            width,
            Math.max(1, laneCount),
            centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE,
            laneDividers,
            0);
    }

    public void recomputeDerivedWidth() {
        List<Integer> resolved = resolveLaneWidths();
        width = CarriagewayWidthRules.derivedCarriagewayWidth(
            resolved,
            centerLineStyle != null ? centerLineStyle : CenterLineStyle.NONE,
            laneDividers);
        if (includeMedian) {
            medianWidth = RoadParameterLimits.clampMedianWidthBlocks(width, Math.max(1, medianWidth));
        }
    }

    public List<Integer> resolveLaneWidths() {
        syncLaneWidthList();
        List<Integer> resolved = new ArrayList<>(laneCount);
        int uniform = RoadParameterLimits.clampLaneTravelWidth(defaultLaneWidth);
        for (int i = 0; i < laneCount; i++) {
            int w = i < laneWidths.size() && laneWidths.get(i) != null ? laneWidths.get(i) : 0;
            resolved.add(w > 0 ? RoadParameterLimits.clampLaneTravelWidth(w) : uniform);
        }
        return resolved;
    }
}
