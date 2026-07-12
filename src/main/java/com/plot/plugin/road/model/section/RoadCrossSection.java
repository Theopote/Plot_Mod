package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadMaterialUtils;

/**
 * 道路横断面模板，挂在 {@link com.plot.plugin.road.model.Road} 上。
 * 生成器读取 {@link ResolvedCrossSection} 做偏移与铺面。
 */
public class RoadCrossSection {
    private LaneGroup carriageway = new LaneGroup();
    private Median median = new Median();
    private Markings markings = new Markings();
    private Shoulder shoulder = new Shoulder();
    private BikeLane bikeLane = new BikeLane();
    private Sidewalk sidewalk = new Sidewalk();
    private Drain drain = new Drain();
    private SlopeBatter slopeBatter = new SlopeBatter();
    private StreetFurniture streetFurniture = new StreetFurniture();

    public RoadCrossSection() {
    }

    public static RoadCrossSection fromConfig(RoadSystemConfig defaults) {
        if (defaults == null) {
            return new RoadCrossSection();
        }
        RoadCrossSection section = new RoadCrossSection();
        section.carriageway.setLaneCount(1);
        section.carriageway.setWidth(defaults.getRoadWidth());
        section.carriageway.setMaterial(defaults.getSelectedMaterial());
        section.shoulder.setEnabled(defaults.isIncludeShoulder());
        section.shoulder.setWidth(defaults.getShoulderWidth());
        section.shoulder.setMaterial(defaults.getFillSlopeMaterial());
        section.slopeBatter.setFillRatio(defaults.getFillSlopeRatio());
        section.slopeBatter.setCutRatio(defaults.getCutSlopeRatio());
        section.slopeBatter.setFillMaterial(defaults.getFillSlopeMaterial());
        section.slopeBatter.setCutMaterial(defaults.getCutSlopeMaterial());
        section.sidewalk.setEnabled(defaults.isIncludeSidewalk());
        section.sidewalk.setWidth(defaults.getSidewalkWidth());
        section.sidewalk.setMaterial(defaults.getSelectedSidewalkMaterial());
        section.drain.setEnabled(defaults.isIncludeDrainage());
        section.markings.setLaneDividers(true);
        section.markings.setCenterLine(false);
        section.markings.setMaterial("material.plot.white_concrete");
        return section;
    }

    public static RoadCrossSection fromPreset(RoadSystemConfig.RoadPreset preset) {
        if (preset == null) {
            return new RoadCrossSection();
        }
        String roadMaterial = preset.roadMaterial != null && !preset.roadMaterial.isBlank()
            ? preset.roadMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String sidewalkMaterial = preset.sidewalkMaterial != null && !preset.sidewalkMaterial.isBlank()
            ? preset.sidewalkMaterial
            : roadMaterial;

        RoadCrossSection section = new RoadCrossSection();
        int laneCount = preset.width >= 7 ? 2 : 1;
        section.carriageway.setLaneCount(laneCount);
        section.carriageway.setWidth(preset.width);
        section.carriageway.setMaterial(roadMaterial);
        section.carriageway.syncLaneCount(laneCount);
        section.shoulder.setEnabled(preset.includeShoulder);
        section.shoulder.setWidth(preset.shoulderWidth);
        section.shoulder.setMaterial("material.plot.gravel");
        section.sidewalk.setEnabled(preset.hasSidewalk);
        section.sidewalk.setWidth(preset.hasSidewalk ? Math.max(1, preset.sidewalkWidth) : 0);
        section.sidewalk.setMaterial(sidewalkMaterial);
        section.drain.setEnabled(preset.includeDrainage);
        section.markings.setLaneDividers(laneCount > 1);
        section.markings.setCenterLine(false);
        section.markings.setMaterial("material.plot.white_concrete");
        return section;
    }

    public static RoadCrossSection fromLegacy(
            Integer width,
            String material,
            Boolean includeSidewalk,
            Integer sidewalkWidth,
            String sidewalkMaterial,
            Integer streetlightSpacing) {
        RoadCrossSection section = new RoadCrossSection();
        section.carriageway.setLaneCount(1);
        section.carriageway.setWidth(width);
        section.carriageway.setMaterial(material);
        section.sidewalk.setEnabled(includeSidewalk);
        section.sidewalk.setWidth(sidewalkWidth);
        section.sidewalk.setMaterial(sidewalkMaterial);
        if (streetlightSpacing != null) {
            section.streetFurniture.setStreetlightSpacing(streetlightSpacing);
        }
        return section;
    }

    public LaneGroup getCarriageway() {
        return carriageway;
    }

    public void setCarriageway(LaneGroup carriageway) {
        this.carriageway = carriageway != null ? carriageway : new LaneGroup();
    }

    public Median getMedian() {
        return median;
    }

    public void setMedian(Median median) {
        this.median = median != null ? median : new Median();
    }

    public Markings getMarkings() {
        return markings;
    }

    public void setMarkings(Markings markings) {
        this.markings = markings != null ? markings : new Markings();
    }

    public Shoulder getShoulder() {
        return shoulder;
    }

    public void setShoulder(Shoulder shoulder) {
        this.shoulder = shoulder != null ? shoulder : new Shoulder();
    }

    public BikeLane getBikeLane() {
        return bikeLane;
    }

    public void setBikeLane(BikeLane bikeLane) {
        this.bikeLane = bikeLane != null ? bikeLane : new BikeLane();
    }

    public Sidewalk getSidewalk() {
        return sidewalk;
    }

    public void setSidewalk(Sidewalk sidewalk) {
        this.sidewalk = sidewalk != null ? sidewalk : new Sidewalk();
    }

    public Drain getDrain() {
        return drain;
    }

    public void setDrain(Drain drain) {
        this.drain = drain != null ? drain : new Drain();
    }

    public SlopeBatter getSlopeBatter() {
        return slopeBatter;
    }

    public void setSlopeBatter(SlopeBatter slopeBatter) {
        this.slopeBatter = slopeBatter != null ? slopeBatter : new SlopeBatter();
    }

    public StreetFurniture getStreetFurniture() {
        return streetFurniture;
    }

    public void setStreetFurniture(StreetFurniture streetFurniture) {
        this.streetFurniture = streetFurniture != null ? streetFurniture : new StreetFurniture();
    }

    public void applyDefaults(RoadSystemConfig defaults) {
        if (defaults == null) {
            return;
        }
        RoadCrossSection template = fromConfig(defaults);
        carriageway = template.carriageway;
        shoulder = template.shoulder;
        sidewalk = template.sidewalk;
        drain = template.drain;
    }

    public ResolvedCrossSection resolve(RoadSystemConfig defaults) {
        return ResolvedCrossSection.resolve(this, defaults);
    }

    public RoadCrossSection copy() {
        RoadCrossSection copy = new RoadCrossSection();
        copy.carriageway = carriageway.copy();
        copy.median = median.copy();
        copy.markings = markings.copy();
        copy.shoulder = shoulder.copy();
        copy.bikeLane = bikeLane.copy();
        copy.sidewalk = sidewalk.copy();
        copy.drain = drain.copy();
        copy.slopeBatter = slopeBatter.copy();
        copy.streetFurniture = streetFurniture.copy();
        return copy;
    }

    public static void mergeLegacyFlatFields(
            RoadCrossSection section,
            Integer width,
            String material,
            Boolean includeSidewalk,
            Integer sidewalkWidth,
            String sidewalkMaterial,
            Boolean includeShoulder,
            Integer shoulderWidth,
            String shoulderMaterial,
            Boolean includeDrainage,
            Integer streetlightSpacing) {
        if (section == null) {
            return;
        }
        if (width != null) {
            section.carriageway.setWidth(width);
        }
        if (material != null) {
            section.carriageway.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(material));
        }
        if (includeSidewalk != null) {
            section.sidewalk.setEnabled(includeSidewalk);
        }
        if (sidewalkWidth != null) {
            section.sidewalk.setWidth(sidewalkWidth);
        }
        if (sidewalkMaterial != null) {
            section.sidewalk.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(sidewalkMaterial));
        }
        if (includeShoulder != null) {
            section.shoulder.setEnabled(includeShoulder);
        }
        if (shoulderWidth != null) {
            section.shoulder.setWidth(shoulderWidth);
        }
        if (shoulderMaterial != null) {
            section.shoulder.setMaterial(RoadMaterialUtils.normalizeStoredMaterial(shoulderMaterial));
        }
        if (includeDrainage != null) {
            section.drain.setEnabled(includeDrainage);
        }
        if (streetlightSpacing != null) {
            section.streetFurniture.setStreetlightSpacing(streetlightSpacing);
        }
    }
}
