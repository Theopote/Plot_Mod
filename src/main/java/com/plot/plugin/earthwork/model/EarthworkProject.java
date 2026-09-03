package com.plot.plugin.earthwork.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.material.EarthMaterialClass;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.persistence.EarthworkProjectMigrator;
import com.plot.plugin.earthwork.persistence.EarthworkProjectSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 土方平衡项目（管理场地与设计分区）。
 * <p>
 * Phase A：内部以 {@link EarthworkSite} 为聚合根，保留 {@link GradingRegion} 兼容 API。
 */
public class EarthworkProject {
    public static final int SCHEMA_VERSION_V1 = EarthworkProjectSchema.V1;
    public static final int SCHEMA_VERSION_V2 = EarthworkProjectSchema.V2;
    public static final int SCHEMA_VERSION_V3 = EarthworkProjectSchema.V3;
    public static final int SCHEMA_VERSION_CURRENT = EarthworkProjectSchema.CURRENT;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int schemaVersion = SCHEMA_VERSION_CURRENT;
    private final Map<String, EarthworkSite> sites = new LinkedHashMap<>();
    private String activeSiteId = "";

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Map<String, EarthworkSite> getSites() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sites));
    }

    public EarthworkSite getSite(String siteId) {
        return sites.get(siteId);
    }

    public EarthworkSite getActiveSite() {
        ensureActiveSite();
        return sites.get(activeSiteId);
    }

    public String getActiveSiteId() {
        ensureActiveSite();
        return activeSiteId;
    }

    public void setActiveSiteId(String siteId) {
        if (siteId != null && sites.containsKey(siteId)) {
            activeSiteId = siteId;
        }
    }

    public EarthworkSite addSite(EarthworkSite site) {
        if (site == null) {
            throw new IllegalArgumentException("Earthwork site cannot be null");
        }
        sites.put(site.getId(), site);
        if (activeSiteId == null || activeSiteId.isBlank() || !sites.containsKey(activeSiteId)) {
            activeSiteId = site.getId();
        }
        return site;
    }

    public void removeSite(String siteId) {
        sites.remove(siteId);
        if (siteId != null && siteId.equals(activeSiteId)) {
            activeSiteId = sites.isEmpty() ? "" : sites.keySet().iterator().next();
        }
    }

    public int getSiteCount() {
        return sites.size();
    }

    // --- GradingRegion 兼容 API（委托给当前 Site 的分区） ---

    public Map<String, GradingRegion> getRegions() {
        EarthworkSite site = getActiveSite();
        Map<String, GradingRegion> regions = new LinkedHashMap<>();
        for (GradingZone zone : site.getGradingZones().values()) {
            regions.put(zone.getId(), zone.getRegion());
        }
        return Collections.unmodifiableMap(regions);
    }

    public GradingRegion getRegion(String id) {
        GradingZone zone = getActiveSite().getZone(id);
        return zone != null ? zone.getRegion() : null;
    }

    public GradingZone getZone(String id) {
        return getActiveSite().getZone(id);
    }

    public GradingRegion addRegion(GradingRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Grading region cannot be null");
        }
        EarthworkSite site = getActiveSite();
        GradingZone zone = GradingZone.fromGradingRegion(region);
        site.addZone(zone);
        site.recomputeSiteBoundaryFromZones();
        return zone.getRegion();
    }

    public void removeRegion(String id) {
        EarthworkSite site = getActiveSite();
        site.removeZone(id);
        if (site.getZoneCount() > 0) {
            site.recomputeSiteBoundaryFromZones();
        }
    }

    public int getRegionCount() {
        return getActiveSite().getZoneCount();
    }

    public double getTotalArea() {
        return getActiveSite().getTotalArea();
    }

    public void ensureActiveSite() {
        if (!activeSiteId.isBlank() && sites.containsKey(activeSiteId)) {
            return;
        }
        if (!sites.isEmpty()) {
            activeSiteId = sites.keySet().iterator().next();
            return;
        }
        EarthworkSite site = new EarthworkSite();
        addSite(site);
    }

    public String toJson() {
        return GSON.toJson(ProjectData.from(this));
    }

    /**
     * 解析 JSON。损坏内容抛 {@link IllegalArgumentException}，不得静默变成空项目。
     * 自动执行 schema v1 → v2 → v3 迁移链。
     */
    public static EarthworkProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new EarthworkProject();
        }
        try {
            return EarthworkProjectMigrator.load(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid earthwork project JSON", e);
        }
    }

    /**
     * 由 {@link EarthworkProjectMigrator} 在迁移完成后调用；不再执行版本升级。
     */
    public static EarthworkProject fromNormalizedJson(String json) {
        ProjectData data = GSON.fromJson(json, ProjectData.class);
        EarthworkProjectSchema.assertSupported(data.schemaVersion);
        return data.toProject();
    }

    /**
     * v1 → v2 迁移（供 {@link EarthworkProjectMigrator} 使用）。
     */
    public static String migrateV1JsonToV2Json(String json) {
        ProjectData data = GSON.fromJson(json, ProjectData.class);
        EarthworkProject project = data.migrateV1();
        ProjectData v2 = ProjectData.from(project);
        v2.schemaVersion = SCHEMA_VERSION_V2;
        v2.regions = new ArrayList<>();
        return GSON.toJson(v2);
    }

    /**
     * 原子保存：先写临时文件再 rename。
     */
    public void saveTo(Path file) throws IOException {
        com.plot.core.persistence.AtomicFileWriter.write(file, toJson());
    }

    public static EarthworkProject loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new EarthworkProject();
        }
        try {
            return fromJson(Files.readString(file));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse earthwork project: " + file.getFileName(), e);
        }
    }

    EarthworkProject deepCopy() {
        return fromJson(toJson());
    }

    static class Vec2dData {
        double x;
        double y;

        Vec2dData() {
        }

        Vec2dData(Vec2d vec) {
            this.x = vec.x;
            this.y = vec.y;
        }

        Vec2d toVec2d() {
            return new Vec2d(x, y);
        }
    }

    static class ProjectData {
        int schemaVersion;
        List<SiteData> sites = new ArrayList<>();
        String activeSiteId = "";
        List<RegionData> regions = new ArrayList<>();

        static ProjectData from(EarthworkProject project) {
            ProjectData data = new ProjectData();
            data.schemaVersion = SCHEMA_VERSION_CURRENT;
            data.activeSiteId = project.activeSiteId;
            for (EarthworkSite site : project.sites.values()) {
                data.sites.add(SiteData.from(site));
            }
            return data;
        }

        EarthworkProject toProject() {
            return loadCurrent();
        }

        private EarthworkProject loadCurrent() {
            EarthworkProject project = new EarthworkProject();
            project.schemaVersion = SCHEMA_VERSION_CURRENT;
            if (sites != null) {
                for (SiteData siteData : sites) {
                    EarthworkSite site = siteData != null ? siteData.toSite() : null;
                    if (site != null) {
                        project.addSite(site);
                    }
                }
            }
            if (activeSiteId != null && !activeSiteId.isBlank() && project.getSite(activeSiteId) != null) {
                project.activeSiteId = activeSiteId;
            }
            project.ensureActiveSite();
            return project;
        }

        private EarthworkProject migrateV1() {
            EarthworkProject project = new EarthworkProject();
            project.schemaVersion = SCHEMA_VERSION_V2;
            if (regions == null || regions.isEmpty()) {
                project.ensureActiveSite();
                return project;
            }

            EarthworkSite site = new EarthworkSite();
            site.setName("Imported Site");
            MaterialConversionModel siteMaterial = MaterialConversionModel.DEFAULT;

            for (RegionData regionData : regions) {
                GradingRegion region = regionData.toRegion();
                if (region == null) {
                    continue;
                }
                if (siteMaterial == MaterialConversionModel.DEFAULT) {
                    siteMaterial = region.getMaterialProperties();
                }
                GradingZone zone = GradingZone.fromGradingRegion(region);
                zone.setPriority(GradingZone.DEFAULT_PRIORITY);
                site.addZone(zone);
            }

            if (site.getZoneCount() == 0) {
                project.ensureActiveSite();
                return project;
            }

            site.setMaterialModel(siteMaterial);
            site.recomputeSiteBoundaryFromZones();
            project.addSite(site);
            return project;
        }
    }

    static class SiteData {
        String id;
        String name;
        List<Vec2dData> siteBoundary = new ArrayList<>();
        MaterialData materialModel = new MaterialData();
        String cutMaterialClass = EarthMaterialClass.UNKNOWN.name();
        String fillMaterialClass = EarthMaterialClass.COMMON_FILL.name();
        ExistingTerrainRefData existingTerrainRef;
        CompositionPolicyData compositionPolicy = new CompositionPolicyData();
        List<ZoneData> gradingZones = new ArrayList<>();
        List<BreaklineData> breaklines = new ArrayList<>();
        List<RetainingEdgeData> retainingEdges = new ArrayList<>();
        List<ExclusionZoneData> exclusionZones = new ArrayList<>();

        static SiteData from(EarthworkSite site) {
            SiteData data = new SiteData();
            data.id = site.getId();
            data.name = site.getName();
            for (Vec2d point : site.getSiteBoundary()) {
                data.siteBoundary.add(new Vec2dData(point));
            }
            data.materialModel = MaterialData.from(site.getMaterialModel());
            data.cutMaterialClass = site.getCutMaterialClass().name();
            data.fillMaterialClass = site.getFillMaterialClass().name();
            data.existingTerrainRef = ExistingTerrainRefData.from(site.getExistingTerrainRef());
            data.compositionPolicy = CompositionPolicyData.from(site.getCompositionPolicy());
            for (GradingZone zone : site.getGradingZones().values()) {
                data.gradingZones.add(ZoneData.from(zone));
            }
            for (Breakline breakline : site.getBreaklines()) {
                data.breaklines.add(BreaklineData.from(breakline));
            }
            for (RetainingEdge edge : site.getRetainingEdges()) {
                data.retainingEdges.add(RetainingEdgeData.from(edge));
            }
            for (ExclusionZone exclusion : site.getExclusionZones()) {
                data.exclusionZones.add(ExclusionZoneData.from(exclusion));
            }
            return data;
        }

        EarthworkSite toSite() {
            String siteId = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
            EarthworkSite site = new EarthworkSite(siteId);
            site.setName(name);
            site.setSiteBoundary(readPoints(siteBoundary));
            site.setMaterialModel(materialModel != null
                ? materialModel.toProperties()
                : MaterialConversionModel.DEFAULT);
            site.setCutMaterialClass(EarthMaterialClass.fromId(cutMaterialClass));
            site.setFillMaterialClass(EarthMaterialClass.fromId(fillMaterialClass));
            if (existingTerrainRef != null) {
                site.setExistingTerrainRef(existingTerrainRef.toRef());
            }
            site.setCompositionPolicy(compositionPolicy != null
                ? compositionPolicy.toPolicy()
                : CompositionPolicy.DEFAULT);
            if (gradingZones != null) {
                for (ZoneData zoneData : gradingZones) {
                    GradingZone zone = zoneData != null ? zoneData.toZone() : null;
                    if (zone != null) {
                        site.addZone(zone);
                    }
                }
            }
            site.setBreaklines(readBreaklines(breaklines));
            site.setRetainingEdges(readRetainingEdges(retainingEdges));
            site.setExclusionZones(readExclusionZones(exclusionZones));
            site.refreshSiteBoundaryIfNeeded();
            return site;
        }

        private static List<Breakline> readBreaklines(List<BreaklineData> items) {
            List<Breakline> result = new ArrayList<>();
            if (items == null) {
                return result;
            }
            for (BreaklineData item : items) {
                if (item != null) {
                    result.add(item.toBreakline());
                }
            }
            return result;
        }

        private static List<RetainingEdge> readRetainingEdges(List<RetainingEdgeData> items) {
            List<RetainingEdge> result = new ArrayList<>();
            if (items == null) {
                return result;
            }
            for (RetainingEdgeData item : items) {
                if (item != null) {
                    result.add(item.toRetainingEdge());
                }
            }
            return result;
        }

        private static List<ExclusionZone> readExclusionZones(List<ExclusionZoneData> items) {
            List<ExclusionZone> result = new ArrayList<>();
            if (items == null) {
                return result;
            }
            for (ExclusionZoneData item : items) {
                if (item != null) {
                    result.add(item.toExclusionZone());
                }
            }
            return result;
        }
    }

    private static List<Vec2d> readPoints(List<Vec2dData> pointData) {
        List<Vec2d> points = new ArrayList<>();
        if (pointData == null) {
            return points;
        }
        for (Vec2dData data : pointData) {
            if (data != null) {
                points.add(data.toVec2d());
            }
        }
        return points;
    }

    private static List<List<Vec2d>> readHoleRings(List<List<Vec2dData>> holeData) {
        List<List<Vec2d>> rings = new ArrayList<>();
        if (holeData == null) {
            return rings;
        }
        for (List<Vec2dData> ringData : holeData) {
            List<Vec2d> ring = readPoints(ringData);
            if (ring.size() >= 3) {
                rings.add(ring);
            }
        }
        return rings;
    }

    private static RegionGeometry readRegionGeometry(
            List<Vec2dData> outerRing,
            List<Vec2dData> outerPoints,
            List<List<Vec2dData>> holes) {
        List<Vec2d> outer = readPoints(outerRing != null && !outerRing.isEmpty() ? outerRing : outerPoints);
        return RegionGeometry.of(outer, readHoleRings(holes));
    }

    private static void writeOuterRing(
            RegionGeometry geometry,
            List<Vec2dData> outerRing,
            List<Vec2dData> outerPoints) {
        if (outerRing != null) {
            outerRing.clear();
        }
        if (outerPoints != null) {
            outerPoints.clear();
        }
        if (geometry == null) {
            return;
        }
        for (Vec2d point : geometry.outerRing()) {
            if (outerRing != null) {
                outerRing.add(new Vec2dData(point));
            }
            if (outerPoints != null) {
                outerPoints.add(new Vec2dData(point));
            }
        }
    }

    private static void writeHoles(RegionGeometry geometry, List<List<Vec2dData>> holes) {
        if (holes == null) {
            return;
        }
        holes.clear();
        if (geometry == null) {
            return;
        }
        for (List<Vec2d> ring : geometry.holes()) {
            List<Vec2dData> ringData = new ArrayList<>();
            for (Vec2d point : ring) {
                ringData.add(new Vec2dData(point));
            }
            holes.add(ringData);
        }
    }

    static class MaterialData {
        float reusableRatio = MaterialConversionModel.DEFAULT_REUSABLE_RATIO;
        float cutToCompactedFillRatio = MaterialConversionModel.DEFAULT_CUT_TO_COMPACTED_FILL_RATIO;

        static MaterialData from(MaterialConversionModel properties) {
            MaterialData data = new MaterialData();
            data.reusableRatio = properties.reusableRatio();
            data.cutToCompactedFillRatio = properties.cutToCompactedFillRatio();
            return data;
        }

        MaterialConversionModel toProperties() {
            return new MaterialConversionModel(reusableRatio, cutToCompactedFillRatio);
        }
    }

    static class ExistingTerrainRefData {
        long capturedAtEpochMs;
        String worldKey = "";
        long outlineFingerprint;
        long contentFingerprint;
        int columnCount;
        String snapshotFile = "";

        static ExistingTerrainRefData from(ExistingTerrainRef ref) {
            ExistingTerrainRefData data = new ExistingTerrainRefData();
            data.capturedAtEpochMs = ref.getCapturedAtEpochMs();
            data.worldKey = ref.getWorldKey();
            data.outlineFingerprint = ref.getOutlineFingerprint();
            data.contentFingerprint = ref.getContentFingerprint();
            data.columnCount = ref.getColumnCount();
            data.snapshotFile = ref.getSnapshotFile();
            return data;
        }

        ExistingTerrainRef toRef() {
            ExistingTerrainRef ref = new ExistingTerrainRef();
            ref.setCapturedAtEpochMs(capturedAtEpochMs);
            ref.setWorldKey(worldKey);
            ref.setOutlineFingerprint(outlineFingerprint);
            ref.setContentFingerprint(contentFingerprint);
            ref.setColumnCount(columnCount);
            ref.setSnapshotFile(snapshotFile);
            return ref;
        }
    }

    static class CompositionPolicyData {
        String overlapResolution = CompositionPolicy.OVERLAP_HIGHEST_PRIORITY_WINS;
        String balanceScope = CompositionPolicy.BALANCE_SCOPE_SITE;
        /** 规范字段；缺省时回退 {@link #balanceMethod}。 */
        String optimizationMode;
        /**
         * @deprecated 兼容旧 JSON；仅当 {@link #optimizationMode} 缺省时使用。
         */
        @Deprecated
        String balanceMethod = CompositionPolicy.OPTIMIZATION_MODE_NONE;
        boolean balanceResidualUniformPolish = true;
        String outsideSiteBoundary = CompositionPolicy.OUTSIDE_IGNORE;
        String exclusionPrecedence = CompositionPolicy.PRECEDENCE_ABSOLUTE;
        String breaklinePrecedence = CompositionPolicy.PRECEDENCE_ABSOLUTE;
        int blendWidthBlocks;

        static CompositionPolicyData from(CompositionPolicy policy) {
            CompositionPolicyData data = new CompositionPolicyData();
            data.overlapResolution = policy.getOverlapResolution();
            data.balanceScope = policy.getBalanceScope();
            data.optimizationMode = policy.getOptimizationMode();
            data.balanceMethod = policy.getOptimizationMode();
            data.balanceResidualUniformPolish = policy.isBalanceResidualUniformPolish();
            data.outsideSiteBoundary = policy.getOutsideSiteBoundary();
            data.exclusionPrecedence = policy.getExclusionPrecedence();
            data.breaklinePrecedence = policy.getBreaklinePrecedence();
            data.blendWidthBlocks = policy.getBlendWidthBlocks();
            return data;
        }

        CompositionPolicy toPolicy() {
            CompositionPolicy policy = new CompositionPolicy();
            policy.setOverlapResolution(overlapResolution);
            policy.setBalanceScope(balanceScope);
            String mode = optimizationMode != null && !optimizationMode.isBlank()
                ? optimizationMode
                : balanceMethod;
            policy.setOptimizationMode(mode);
            policy.setBalanceResidualUniformPolish(balanceResidualUniformPolish);
            policy.setOutsideSiteBoundary(outsideSiteBoundary);
            policy.setExclusionPrecedence(exclusionPrecedence);
            policy.setBreaklinePrecedence(breaklinePrecedence);
            policy.setBlendWidthBlocks(blendWidthBlocks);
            return policy;
        }
    }

    static class DesignSurfaceData {
        String kind = DesignSurfaceKind.LEVEL_PAD.name();
        boolean autoBalance = true;
        Integer manualTargetElevation;
        boolean fitSlopeBalanceCutFill = true;
        double slopeDirectionDegrees;
        int slopePitchRatio = GradingRegion.DEFAULT_SLOPE_PITCH_RATIO;
        Double anchorCanvasX;
        Double anchorCanvasY;
        Integer anchorElevation;
        double[] threePointCanvasX = new double[3];
        double[] threePointCanvasY = new double[3];
        int[] threePointElevation = new int[] {64, 64, 64};
        Integer elevation;
        String buildingFootprintRef = "";
        String roadEdgeRef = "";
        String elevationSource = DesignSurfaceElevationSource.MANUAL.name();
        Integer bottomElevation;
        /** 地下室楼面深度；缺省时回退 {@link #basementDepthBlocks}。 */
        Integer basementFloorDepth;
        int foundationDepth;
        int workingAllowance;
        /** @deprecated 兼容旧 JSON；加载时映射为 {@link #basementFloorDepth}。 */
        @Deprecated
        int basementDepthBlocks = ExcavationPitParameters.DEFAULT_BASEMENT_FLOOR_DEPTH;
        int workingMarginBlocks = 1;
        int verticalOffset;
        List<BakedSampleData> bakedSamples = new ArrayList<>();
        List<DesignSurfaceFacetData> facets = new ArrayList<>();

        static DesignSurfaceData from(DesignSurface surface) {
            DesignSurfaceData data = new DesignSurfaceData();
            data.kind = surface.getKind().name();
            data.autoBalance = surface.isAutoBalance();
            data.manualTargetElevation = surface.getManualTargetElevation();
            data.fitSlopeBalanceCutFill = surface.isFitSlopeBalanceCutFill();
            data.slopeDirectionDegrees = surface.getSlopeDirectionDegrees();
            data.slopePitchRatio = surface.getSlopePitchRatio();
            Vec2d anchor = surface.getAnchorCanvas();
            if (anchor != null) {
                data.anchorCanvasX = anchor.x;
                data.anchorCanvasY = anchor.y;
            }
            data.anchorElevation = surface.getAnchorElevation();
            for (int i = 0; i < 3; i++) {
                data.threePointCanvasX[i] = surface.getThreePointCanvasX(i);
                data.threePointCanvasY[i] = surface.getThreePointCanvasY(i);
                data.threePointElevation[i] = surface.getThreePointElevation(i);
            }
            data.elevation = surface.getElevation();
            data.buildingFootprintRef = surface.getBuildingFootprintRef();
            data.roadEdgeRef = surface.getRoadEdgeRef();
            data.elevationSource = surface.getElevationSource().name();
            data.bottomElevation = surface.getBottomElevation();
            ExcavationPitParameters pit = surface.getExcavationPit();
            data.basementFloorDepth = pit.getBasementFloorDepth();
            data.foundationDepth = pit.getFoundationDepth();
            data.workingAllowance = pit.getWorkingAllowance();
            data.basementDepthBlocks = pit.getBasementFloorDepth();
            data.workingMarginBlocks = surface.getWorkingMarginBlocks();
            data.verticalOffset = surface.getVerticalOffset();
            for (BakedElevationGrid.Sample sample : surface.getBakedElevationGrid().toSamples()) {
                data.bakedSamples.add(BakedSampleData.from(sample));
            }
            for (DesignSurfaceFacet facet : surface.getFacets()) {
                if (facet != null) {
                    data.facets.add(DesignSurfaceFacetData.from(facet));
                }
            }
            return data;
        }

        DesignSurface toSurface() {
            DesignSurface surface = new DesignSurface();
            surface.setKind(DesignSurfaceKind.fromId(kind));
            surface.setAutoBalance(autoBalance);
            surface.setManualTargetElevation(manualTargetElevation);
            surface.setFitSlopeBalanceCutFill(fitSlopeBalanceCutFill);
            surface.setSlopeDirectionDegrees(slopeDirectionDegrees);
            surface.setSlopePitchRatio(slopePitchRatio);
            if (anchorCanvasX != null && anchorCanvasY != null) {
                surface.setAnchorCanvas(new Vec2d(anchorCanvasX, anchorCanvasY));
            }
            surface.setAnchorElevation(anchorElevation);
            if (threePointCanvasX != null && threePointCanvasY != null && threePointElevation != null) {
                for (int i = 0; i < 3; i++) {
                    surface.setThreePointControl(
                        i,
                        new Vec2d(threePointCanvasX[i], threePointCanvasY[i]),
                        threePointElevation[i]);
                }
            }
            surface.setElevation(elevation);
            surface.setBuildingFootprintRef(buildingFootprintRef);
            surface.setRoadEdgeRef(roadEdgeRef);
            surface.setElevationSource(DesignSurfaceElevationSource.fromId(elevationSource));
            surface.setBottomElevation(bottomElevation);
            int floorDepth = basementFloorDepth != null ? basementFloorDepth : basementDepthBlocks;
            surface.setExcavationPit(new ExcavationPitParameters(floorDepth, foundationDepth, workingAllowance));
            surface.setWorkingMarginBlocks(workingMarginBlocks);
            surface.setVerticalOffset(verticalOffset);
            surface.setBakedElevationGrid(BakedElevationGrid.fromSamples(readBakedSamples(bakedSamples)));
            surface.setFacets(readFacets(facets));
            return surface;
        }

        private static List<DesignSurfaceFacet> readFacets(List<DesignSurfaceFacetData> items) {
            List<DesignSurfaceFacet> result = new ArrayList<>();
            if (items == null) {
                return result;
            }
            for (DesignSurfaceFacetData item : items) {
                if (item != null) {
                    DesignSurfaceFacet facet = item.toFacet();
                    if (facet != null) {
                        result.add(facet);
                    }
                }
            }
            return result;
        }

        private static List<BakedElevationGrid.Sample> readBakedSamples(List<BakedSampleData> items) {
            List<BakedElevationGrid.Sample> samples = new ArrayList<>();
            if (items == null) {
                return samples;
            }
            for (BakedSampleData item : items) {
                if (item != null) {
                    samples.add(item.toSample());
                }
            }
            return samples;
        }
    }

    static class DesignSurfaceFacetData {
        String id;
        String name = "";
        List<Vec2dData> outerRing = new ArrayList<>();
        List<Vec2dData> outerPoints = new ArrayList<>();
        List<List<Vec2dData>> holes = new ArrayList<>();
        DesignSurfaceData plane = new DesignSurfaceData();

        static DesignSurfaceFacetData from(DesignSurfaceFacet facet) {
            DesignSurfaceFacetData data = new DesignSurfaceFacetData();
            data.id = facet.getId();
            data.name = facet.getName();
            writeOuterRing(facet.getGeometry(), data.outerRing, data.outerPoints);
            writeHoles(facet.getGeometry(), data.holes);
            data.plane = DesignSurfaceData.from(facet.getPlane());
            return data;
        }

        DesignSurfaceFacet toFacet() {
            RegionGeometry geometry = readRegionGeometry(outerRing, outerPoints, holes);
            if (geometry.isEmpty()) {
                return null;
            }
            DesignSurfaceFacet facet = new DesignSurfaceFacet(
                id != null && !id.isBlank() ? id : UUID.randomUUID().toString(),
                geometry);
            facet.setName(name);
            if (plane != null) {
                facet.setPlane(plane.toSurface());
            }
            return facet;
        }
    }

    static class BakedSampleData {
        int worldX;
        int worldZ;
        int targetY;

        static BakedSampleData from(BakedElevationGrid.Sample sample) {
            BakedSampleData data = new BakedSampleData();
            data.worldX = sample.worldX();
            data.worldZ = sample.worldZ();
            data.targetY = sample.targetY();
            return data;
        }

        BakedElevationGrid.Sample toSample() {
            return new BakedElevationGrid.Sample(worldX, worldZ, targetY);
        }
    }

    static class ZoneData {
        String id;
        String name;
        String type = GradingZoneType.FLAT.name();
        int priority = GradingZone.DEFAULT_PRIORITY;
        boolean enabled = true;
        String buildingFootprintRef = "";
        String roadEdgeRef = "";
        List<Vec2dData> outerRing = new ArrayList<>();
        List<Vec2dData> outerPoints = new ArrayList<>();
        List<List<Vec2dData>> holes = new ArrayList<>();
        MaterialData materialOverride;
        MaterialData materialModel = new MaterialData();
        String cutMaterialClass = EarthMaterialClass.UNKNOWN.name();
        String fillMaterialClass = EarthMaterialClass.COMMON_FILL.name();
        String cutExposeMaterial = "";
        String fillMaterial = GradingRegion.DEFAULT_FILL_MATERIAL;
        int previewGridSize;
        DesignSurfaceData designSurface = new DesignSurfaceData();
        ZoneEdgeSettingsData edgeSettings = new ZoneEdgeSettingsData();
        VerticalAdjustmentPolicyData verticalAdjustmentPolicy;

        static ZoneData from(GradingZone zone) {
            zone.syncDesignSurfaceToRegion();
            ZoneData data = new ZoneData();
            data.id = zone.getId();
            data.name = zone.getName();
            data.type = zone.getType().name();
            data.priority = zone.getPriority();
            data.enabled = zone.isEnabled();
            data.buildingFootprintRef = zone.getBuildingFootprintRef();
            data.roadEdgeRef = zone.getRoadEdgeRef();
            writeOuterRing(zone.getGeometry(), data.outerRing, data.outerPoints);
            writeHoles(zone.getGeometry(), data.holes);
            if (zone.getMaterialOverride() != null) {
                data.materialOverride = MaterialData.from(zone.getMaterialOverride());
            }
            data.materialModel = MaterialData.from(zone.getRegion().getMaterialProperties());
            data.cutMaterialClass = zone.getCutMaterialClass().name();
            data.fillMaterialClass = zone.getFillMaterialClass().name();
            data.cutExposeMaterial = zone.getCutExposeMaterial();
            data.fillMaterial = zone.getFillMaterial();
            data.previewGridSize = zone.getPreviewGridSize();
            data.designSurface = DesignSurfaceData.from(zone.getDesignSurface());
            data.edgeSettings = ZoneEdgeSettingsData.from(zone.getEdgeSettings());
            data.verticalAdjustmentPolicy = VerticalAdjustmentPolicyData.from(zone.getVerticalAdjustmentPolicy());
            return data;
        }

        GradingZone toZone() {
            RegionGeometry geometry = readRegionGeometry(outerRing, outerPoints, holes);
            if (geometry.isEmpty()) {
                return null;
            }
            String zoneId = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
            GradingZone zone = new GradingZone(zoneId, geometry);
            zone.setName(name);
            zone.setType(GradingZoneType.fromId(type));
            zone.setPriority(priority);
            zone.setEnabled(enabled);
            if (buildingFootprintRef != null && !buildingFootprintRef.isBlank()) {
                zone.setBuildingFootprintRef(buildingFootprintRef);
            }
            if (roadEdgeRef != null && !roadEdgeRef.isBlank()) {
                zone.setRoadEdgeRef(roadEdgeRef);
            }
            if (materialOverride != null) {
                zone.setMaterialOverride(materialOverride.toProperties());
            }
            if (materialModel != null) {
                zone.getRegion().setMaterialProperties(materialModel.toProperties());
            }
            zone.setCutMaterialClass(EarthMaterialClass.fromId(cutMaterialClass));
            zone.setFillMaterialClass(EarthMaterialClass.fromId(fillMaterialClass));
            if (cutExposeMaterial != null) {
                zone.setCutExposeMaterial(cutExposeMaterial);
            }
            if (fillMaterial != null) {
                zone.setFillMaterial(fillMaterial);
            }
            zone.setPreviewGridSize(resolvePreviewGridSize());
            zone.setDesignSurface(designSurface != null ? designSurface.toSurface() : new DesignSurface());
            zone.setEdgeSettings(edgeSettings != null ? edgeSettings.toSettings() : new ZoneEdgeSettings());
            if (verticalAdjustmentPolicy != null) {
                zone.setVerticalAdjustmentPolicy(verticalAdjustmentPolicy.toPolicy());
            }
            return zone;
        }

        private int resolvePreviewGridSize() {
            if (previewGridSize > 0) {
                return previewGridSize;
            }
            return GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
        }
    }

    static class VerticalAdjustmentPolicyData {
        String mode = VerticalAdjustmentPolicy.Mode.LOCKED.name();
        int minOffset;
        int maxOffset;
        float weight = VerticalAdjustmentPolicy.DEFAULT_WEIGHT;

        static VerticalAdjustmentPolicyData from(VerticalAdjustmentPolicy policy) {
            VerticalAdjustmentPolicyData data = new VerticalAdjustmentPolicyData();
            if (policy == null) {
                return data;
            }
            data.mode = policy.getMode().name();
            data.minOffset = policy.getMinOffset();
            data.maxOffset = policy.getMaxOffset();
            data.weight = policy.getWeight();
            return data;
        }

        VerticalAdjustmentPolicy toPolicy() {
            return new VerticalAdjustmentPolicy(
                VerticalAdjustmentPolicy.Mode.fromId(mode),
                minOffset,
                maxOffset,
                weight);
        }
    }

    static class ZoneEdgeSettingsData {
        String defaultTreatment = EdgeTreatment.VERTICAL.name();
        int cutSlopePitchRatio = ZoneEdgeSettings.DEFAULT_CUT_SLOPE_PITCH;
        int fillSlopePitchNumerator = ZoneEdgeSettings.DEFAULT_FILL_SLOPE_NUMERATOR;
        int fillSlopePitchDenominator = ZoneEdgeSettings.DEFAULT_FILL_SLOPE_DENOMINATOR;
        int maximumReachBlocks = ZoneEdgeSettings.DEFAULT_MAX_REACH_BLOCKS;
        int benchWidthBlocks;
        String wallMaterial = "minecraft:stone_bricks";
        boolean useLinkedZoneFillMaterial = true;
        List<BoundaryEdgeOverrideData> edgeOverrides = new ArrayList<>();

        static ZoneEdgeSettingsData from(ZoneEdgeSettings settings) {
            ZoneEdgeSettingsData data = new ZoneEdgeSettingsData();
            if (settings == null) {
                return data;
            }
            data.defaultTreatment = settings.getDefaultTreatment().name();
            data.cutSlopePitchRatio = settings.getCutSlopePitchRatio();
            data.fillSlopePitchNumerator = settings.getFillSlopePitchNumerator();
            data.fillSlopePitchDenominator = settings.getFillSlopePitchDenominator();
            data.maximumReachBlocks = settings.getMaximumReachBlocks();
            data.benchWidthBlocks = settings.getBenchWidthBlocks();
            data.wallMaterial = settings.getWallMaterial();
            data.useLinkedZoneFillMaterial = settings.isUseLinkedZoneFillMaterial();
            for (BoundaryEdgeOverride override : settings.getEdgeOverrides()) {
                if (override != null) {
                    data.edgeOverrides.add(BoundaryEdgeOverrideData.from(override));
                }
            }
            return data;
        }

        ZoneEdgeSettings toSettings() {
            ZoneEdgeSettings settings = new ZoneEdgeSettings();
            settings.setDefaultTreatment(EdgeTreatment.fromId(defaultTreatment));
            settings.setCutSlopePitchRatio(cutSlopePitchRatio);
            settings.setFillSlopePitchNumerator(fillSlopePitchNumerator);
            settings.setFillSlopePitchDenominator(fillSlopePitchDenominator);
            settings.setMaximumReachBlocks(maximumReachBlocks);
            settings.setBenchWidthBlocks(benchWidthBlocks);
            settings.setWallMaterial(wallMaterial);
            settings.setUseLinkedZoneFillMaterial(useLinkedZoneFillMaterial);
            if (edgeOverrides != null) {
                List<BoundaryEdgeOverride> overrides = new ArrayList<>();
                for (BoundaryEdgeOverrideData item : edgeOverrides) {
                    if (item != null) {
                        overrides.add(item.toOverride());
                    }
                }
                settings.setEdgeOverrides(overrides);
            }
            return settings;
        }
    }

    static class BoundaryEdgeOverrideData {
        int edgeIndex;
        String treatment = EdgeTreatment.VERTICAL.name();

        static BoundaryEdgeOverrideData from(BoundaryEdgeOverride override) {
            BoundaryEdgeOverrideData data = new BoundaryEdgeOverrideData();
            data.edgeIndex = override.getEdgeIndex();
            data.treatment = override.getTreatment().name();
            return data;
        }

        BoundaryEdgeOverride toOverride() {
            BoundaryEdgeOverride override = new BoundaryEdgeOverride();
            override.setEdgeIndex(edgeIndex);
            override.setTreatment(EdgeTreatment.fromId(treatment));
            return override;
        }
    }

    static class BreaklineData {
        String id;
        String name;
        List<Vec2dData> points = new ArrayList<>();
        String role = Breakline.ROLE_HARD_BOUNDARY;
        String leftZoneId = "";
        String rightZoneId = "";

        static BreaklineData from(Breakline breakline) {
            BreaklineData data = new BreaklineData();
            data.id = breakline.getId();
            data.name = breakline.getName();
            for (Vec2d point : breakline.getPoints()) {
                data.points.add(new Vec2dData(point));
            }
            data.role = breakline.getRole();
            data.leftZoneId = breakline.getLeftZoneId();
            data.rightZoneId = breakline.getRightZoneId();
            return data;
        }

        Breakline toBreakline() {
            Breakline breakline = new Breakline(id);
            breakline.setName(name);
            breakline.setPoints(readPoints(points));
            breakline.setRole(role);
            breakline.setLeftZoneId(leftZoneId);
            breakline.setRightZoneId(rightZoneId);
            return breakline;
        }
    }

    static class ExclusionZoneData {
        String id;
        String name;
        List<Vec2dData> outerRing = new ArrayList<>();
        List<Vec2dData> outerPoints = new ArrayList<>();
        List<List<Vec2dData>> holes = new ArrayList<>();
        String mode = ExclusionZone.MODE_PRESERVE_EXISTING;

        static ExclusionZoneData from(ExclusionZone exclusion) {
            ExclusionZoneData data = new ExclusionZoneData();
            data.id = exclusion.getId();
            data.name = exclusion.getName();
            writeOuterRing(exclusion.getGeometry(), data.outerRing, data.outerPoints);
            writeHoles(exclusion.getGeometry(), data.holes);
            data.mode = exclusion.getMode();
            return data;
        }

        ExclusionZone toExclusionZone() {
            ExclusionZone exclusion = new ExclusionZone(id);
            exclusion.setName(name);
            exclusion.setGeometry(readRegionGeometry(outerRing, outerPoints, holes));
            exclusion.setMode(mode);
            return exclusion;
        }
    }

    static class RetainingEdgeData {
        String id;
        String name;
        List<Vec2dData> polyline = new ArrayList<>();
        int topElevation;
        int bottomElevation;
        String side = RetainingEdge.SIDE_CUT;
        String wallMaterial = "minecraft:stone_bricks";
        String linkedZoneId = "";
        boolean useLinkedZoneFillMaterial;

        static RetainingEdgeData from(RetainingEdge edge) {
            RetainingEdgeData data = new RetainingEdgeData();
            data.id = edge.getId();
            data.name = edge.getName();
            for (Vec2d point : edge.getPolyline()) {
                data.polyline.add(new Vec2dData(point));
            }
            data.topElevation = edge.getTopElevation();
            data.bottomElevation = edge.getBottomElevation();
            data.side = edge.getSide();
            data.wallMaterial = edge.getWallMaterial();
            data.linkedZoneId = edge.getLinkedZoneId();
            data.useLinkedZoneFillMaterial = edge.isUseLinkedZoneFillMaterial();
            return data;
        }

        RetainingEdge toRetainingEdge() {
            RetainingEdge edge = new RetainingEdge(id);
            edge.setName(name);
            edge.setPolyline(readPoints(polyline));
            edge.setTopElevation(topElevation);
            edge.setBottomElevation(bottomElevation);
            edge.setSide(side);
            edge.setWallMaterial(wallMaterial);
            edge.setLinkedZoneId(linkedZoneId);
            edge.setUseLinkedZoneFillMaterial(useLinkedZoneFillMaterial);
            return edge;
        }
    }

  /** v1 兼容字段 */
    static class RegionData {
        String id;
        String name;
        List<Vec2dData> outerPoints = new ArrayList<>();
        String surfaceMode = GradingSurfaceMode.LEVEL_PAD.name();
        boolean autoBalance = true;
        Integer manualTargetElevation;
        float reusableRatio = MaterialConversionModel.DEFAULT_REUSABLE_RATIO;
        float cutToCompactedFillRatio = MaterialConversionModel.DEFAULT_CUT_TO_COMPACTED_FILL_RATIO;
        /** @deprecated 仅用于读取旧工程 */
        @Deprecated
        Float fillFactor;
        String cutExposeMaterial = "";
        String fillMaterial = GradingRegion.DEFAULT_FILL_MATERIAL;
        int previewGridSize;
        /** @deprecated 旧字段 */
        @Deprecated
        Integer gridSize;
        double slopeDirectionDegrees;
        int slopePitchRatio = GradingRegion.DEFAULT_SLOPE_PITCH_RATIO;
        Double slopeAnchorCanvasX;
        Double slopeAnchorCanvasY;
        Integer slopeAnchorElevation;
        double[] threePointCanvasX = new double[3];
        double[] threePointCanvasY = new double[3];
        int[] threePointElevation = new int[] {64, 64, 64};
        boolean fitSlopeBalanceCutFill = true;

        GradingRegion toRegion() {
            if (outerPoints == null) {
                return null;
            }
            List<Vec2d> points = readPoints(outerPoints);
            if (points.size() < 3) {
                return null;
            }
            String regionId = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
            GradingRegion region = new GradingRegion(regionId, points);
            region.setName(name);
            region.setAutoBalance(autoBalance);
            region.setManualTargetElevation(manualTargetElevation);
            region.setSurfaceMode(GradingSurfaceMode.fromId(surfaceMode));
            region.setMaterialProperties(resolveMaterialProperties());
            if (cutExposeMaterial != null) {
                region.setCutExposeMaterial(cutExposeMaterial);
            }
            if (fillMaterial != null) {
                region.setFillMaterial(fillMaterial);
            }
            region.setPreviewGridSize(resolvePreviewGridSize());
            region.setSlopeDirectionDegrees(slopeDirectionDegrees);
            region.setSlopePitchRatio(slopePitchRatio);
            if (slopeAnchorCanvasX != null && slopeAnchorCanvasY != null) {
                region.setSlopeAnchorCanvas(new Vec2d(slopeAnchorCanvasX, slopeAnchorCanvasY));
            }
            region.setSlopeAnchorElevation(slopeAnchorElevation);
            if (threePointCanvasX != null && threePointCanvasY != null && threePointElevation != null) {
                for (int i = 0; i < 3; i++) {
                    region.setThreePointControl(
                        i,
                        new Vec2d(threePointCanvasX[i], threePointCanvasY[i]),
                        threePointElevation[i]);
                }
            }
            region.setFitSlopeBalanceCutFill(fitSlopeBalanceCutFill);
            return region;
        }

        private int resolvePreviewGridSize() {
            if (previewGridSize > 0) {
                return previewGridSize;
            }
            if (gridSize != null && gridSize > 0) {
                return gridSize;
            }
            return GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
        }

        private MaterialConversionModel resolveMaterialProperties() {
            if (fillFactor != null && fillFactor > 0.0f) {
                return MaterialConversionModel.fromLegacyFillFactor(fillFactor);
            }
            return new MaterialConversionModel(reusableRatio, cutToCompactedFillRatio);
        }
    }
}
