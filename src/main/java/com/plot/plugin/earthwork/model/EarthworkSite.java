package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 土方场地：施工红线、材料模型、设计分区与合成策略的聚合根。
 */
public class EarthworkSite {
    private final String id;
    private String name;
    private List<Vec2d> siteBoundary = new ArrayList<>();
    private MaterialConversionModel materialModel = MaterialConversionModel.DEFAULT;
    private ExistingTerrainRef existingTerrainRef = new ExistingTerrainRef();
    private CompositionPolicy compositionPolicy = CompositionPolicy.DEFAULT;
    private final Map<String, GradingZone> gradingZones = new LinkedHashMap<>();
    private final List<Breakline> breaklines = new ArrayList<>();
    private final List<RetainingEdge> retainingEdges = new ArrayList<>();
    private final List<ExclusionZone> exclusionZones = new ArrayList<>();

    private transient EarthworkVolumeReport lastReport = EarthworkVolumeReport.empty();
    private transient int lastSiteWideVerticalOffset;
    private transient Map<String, Integer> lastZoneVerticalOffsets = Map.of();

    public Map<String, Integer> getLastZoneVerticalOffsets() {
        return lastZoneVerticalOffsets != null ? lastZoneVerticalOffsets : Map.of();
    }

    public void setLastZoneVerticalOffsets(Map<String, Integer> lastZoneVerticalOffsets) {
        this.lastZoneVerticalOffsets = lastZoneVerticalOffsets != null
            ? Map.copyOf(lastZoneVerticalOffsets)
            : Map.of();
    }

    public void clearLastZoneVerticalOffsets() {
        this.lastZoneVerticalOffsets = Map.of();
    }

    public int getLastSiteWideVerticalOffset() {
        return lastSiteWideVerticalOffset;
    }

    public void setLastSiteWideVerticalOffset(int lastSiteWideVerticalOffset) {
        this.lastSiteWideVerticalOffset = lastSiteWideVerticalOffset;
    }

    public void clearLastSiteWideVerticalOffset() {
        this.lastSiteWideVerticalOffset = 0;
    }

    public EarthworkSite() {
        this(UUID.randomUUID().toString());
    }

    public EarthworkSite(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Site id cannot be blank");
        }
        this.id = id;
        this.name = id.substring(0, Math.min(8, id.length()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isBlank() ? name.trim() : this.name;
    }

    public List<Vec2d> getSiteBoundary() {
        return copyPoints(siteBoundary);
    }

    public void setSiteBoundary(List<Vec2d> siteBoundary) {
        this.siteBoundary = copyPoints(siteBoundary);
    }

    public MaterialConversionModel getMaterialModel() {
        return materialModel != null ? materialModel : MaterialConversionModel.DEFAULT;
    }

    public void setMaterialModel(MaterialConversionModel materialModel) {
        this.materialModel = materialModel != null ? materialModel : MaterialConversionModel.DEFAULT;
    }

    public ExistingTerrainRef getExistingTerrainRef() {
        return existingTerrainRef != null ? existingTerrainRef : new ExistingTerrainRef();
    }

    public void setExistingTerrainRef(ExistingTerrainRef existingTerrainRef) {
        this.existingTerrainRef = existingTerrainRef != null ? existingTerrainRef : new ExistingTerrainRef();
    }

    public CompositionPolicy getCompositionPolicy() {
        return compositionPolicy != null ? compositionPolicy : CompositionPolicy.DEFAULT;
    }

    public void setCompositionPolicy(CompositionPolicy compositionPolicy) {
        this.compositionPolicy = compositionPolicy != null ? compositionPolicy : CompositionPolicy.DEFAULT;
    }

    public Map<String, GradingZone> getGradingZones() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(gradingZones));
    }

    public GradingZone getZone(String zoneId) {
        return gradingZones.get(zoneId);
    }

    public boolean isElevationLocked(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }
        GradingZone zone = getZone(zoneId);
        return zone != null && zone.isElevationLocked();
    }

    public int applyProposedVerticalOffset(String zoneId, int zoneAllocationOffset, int uniformOffset) {
        if (zoneId == null || zoneId.isBlank()) {
            return zoneAllocationOffset + uniformOffset;
        }
        GradingZone zone = getZone(zoneId);
        if (zone == null) {
            return zoneAllocationOffset + uniformOffset;
        }
        return zone.applyProposedVerticalOffset(zoneAllocationOffset, uniformOffset);
    }

    public GradingZone addZone(GradingZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("Grading zone cannot be null");
        }
        gradingZones.put(zone.getId(), zone);
        refreshSiteBoundaryIfNeeded();
        return zone;
    }

    public void removeZone(String zoneId) {
        gradingZones.remove(zoneId);
        refreshSiteBoundaryIfNeeded();
    }

    public int getZoneCount() {
        return gradingZones.size();
    }

    public List<Breakline> getBreaklines() {
        return List.copyOf(breaklines);
    }

    public void setBreaklines(List<Breakline> breaklines) {
        this.breaklines.clear();
        if (breaklines != null) {
            this.breaklines.addAll(breaklines);
        }
    }

    public Breakline addBreakline(Breakline breakline) {
        if (breakline == null) {
            throw new IllegalArgumentException("Breakline cannot be null");
        }
        breaklines.add(breakline);
        return breakline;
    }

    public void removeBreakline(String breaklineId) {
        if (breaklineId == null || breaklineId.isBlank()) {
            return;
        }
        breaklines.removeIf(line -> breaklineId.equals(line.getId()));
    }

    public List<RetainingEdge> getRetainingEdges() {
        return List.copyOf(retainingEdges);
    }

    public void setRetainingEdges(List<RetainingEdge> retainingEdges) {
        this.retainingEdges.clear();
        if (retainingEdges != null) {
            this.retainingEdges.addAll(retainingEdges);
        }
    }

    public RetainingEdge addRetainingEdge(RetainingEdge retainingEdge) {
        if (retainingEdge == null) {
            throw new IllegalArgumentException("Retaining edge cannot be null");
        }
        retainingEdges.add(retainingEdge);
        return retainingEdge;
    }

    public void removeRetainingEdge(String retainingEdgeId) {
        if (retainingEdgeId == null || retainingEdgeId.isBlank()) {
            return;
        }
        retainingEdges.removeIf(edge -> retainingEdgeId.equals(edge.getId()));
    }

    public List<ExclusionZone> getExclusionZones() {
        return List.copyOf(exclusionZones);
    }

    public void setExclusionZones(List<ExclusionZone> exclusionZones) {
        this.exclusionZones.clear();
        if (exclusionZones != null) {
            this.exclusionZones.addAll(exclusionZones);
        }
    }

    public ExclusionZone addExclusionZone(ExclusionZone exclusionZone) {
        if (exclusionZone == null) {
            throw new IllegalArgumentException("Exclusion zone cannot be null");
        }
        exclusionZones.add(exclusionZone);
        return exclusionZone;
    }

    public void removeExclusionZone(String exclusionZoneId) {
        if (exclusionZoneId == null || exclusionZoneId.isBlank()) {
            return;
        }
        exclusionZones.removeIf(zone -> exclusionZoneId.equals(zone.getId()));
    }

    public EarthworkVolumeReport getLastReport() {
        return lastReport != null ? lastReport : EarthworkVolumeReport.empty();
    }

    public void setLastReport(EarthworkVolumeReport lastReport) {
        this.lastReport = lastReport != null ? lastReport : EarthworkVolumeReport.empty();
    }

    public double getTotalArea() {
        return gradingZones.values().stream().mapToDouble(GradingZone::computeArea).sum();
    }

    public double getSiteBoundaryArea() {
        return EarthworkSiteBoundaryUtils.computeBoundaryArea(siteBoundary);
    }

    /**
     * MVP：单分区且类型受支持时，委托 {@code LegacyRegionPipeline}。
     */
    public boolean delegatesToLegacyGenerator() {
        if (gradingZones.size() != 1) {
            return false;
        }
        GradingZone onlyZone = gradingZones.values().iterator().next();
        return onlyZone.isDelegatableToLegacyGenerator()
            && !onlyZone.getEdgeSettings().hasActiveTreatment()
            && breaklines.isEmpty()
            && exclusionZones.isEmpty();
    }

    public GradingZone getLegacyDelegateZone() {
        if (!delegatesToLegacyGenerator()) {
            return null;
        }
        return gradingZones.values().iterator().next();
    }

    public void refreshSiteBoundaryIfNeeded() {
        if (siteBoundary == null || siteBoundary.size() < 3) {
            siteBoundary = new ArrayList<>(EarthworkSiteBoundaryUtils.resolveSiteBoundary(gradingZones.values()));
        }
    }

    public void recomputeSiteBoundaryFromZones() {
        siteBoundary = new ArrayList<>(EarthworkSiteBoundaryUtils.resolveSiteBoundary(gradingZones.values()));
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                if (point != null) {
                    copy.add(new Vec2d(point.x, point.y));
                }
            }
        }
        return copy;
    }
}
