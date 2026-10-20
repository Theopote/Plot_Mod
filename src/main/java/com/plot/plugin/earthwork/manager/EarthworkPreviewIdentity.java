package com.plot.plugin.earthwork.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.model.BakedElevationGrid;
import com.plot.plugin.earthwork.model.BoundaryEdgeOverride;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceFacet;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.ExcavationPitParameters;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.RetainingEdge;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 预览结果身份绑定与设计/地形修订指纹。
 * <p>
 * {@link #designRevision} 覆盖 {@code SiteEarthworkPipeline} 实际读取的设计输入
 * （全站分区几何、设计面、边坡、合成策略、断线/排除区/挡墙、材料与外部引用），
 * 而非仅目标区域的零散字段。
 */
public final class EarthworkPreviewIdentity {
    private EarthworkPreviewIdentity() {
    }

    public static void stamp(
            EarthworkGenerationResult result,
            EarthworkProject project,
            GradingRegion region,
            World world,
            TerrainSnapshot terrain) {
        if (result == null || region == null) {
            return;
        }
        EarthworkSite site = project != null ? project.getActiveSite() : null;
        result.sourceRegionId = region.getId() != null ? region.getId() : "";
        result.sourceSiteId = site != null ? site.getId() : "";
        result.worldKey = world != null ? TerrainSnapshotCache.worldKey(world) : "";
        result.designRevision = designRevision(project, region);
        result.terrainRevision = terrain != null ? terrain.contentFingerprint() : 0L;
    }

    /**
     * 设计修订指纹：含预览焦点区域 + 整站设计输入（管线为站点合成）。
     */
    public static long designRevision(EarthworkProject project, GradingRegion region) {
        if (region == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeStr(region.getId());
        hash = 31L * hash + region.getPreviewGridSize();
        EarthworkSite site = project != null ? project.getActiveSite() : null;
        if (site == null) {
            hash = 31L * hash + hashRegionFallback(region);
            return hash;
        }
        hash = 31L * hash + safeStr(site.getId());
        hash = 31L * hash + hashSiteDesign(site);
        return hash;
    }

    /**
     * 设计/区域身份是否与当前工程一致（不含世界校验）。
     */
    public static boolean matchesDesign(
            EarthworkGenerationResult result,
            EarthworkProject project,
            String expectedRegionId) {
        if (result == null || project == null || expectedRegionId == null || expectedRegionId.isBlank()) {
            return false;
        }
        if (!expectedRegionId.equals(result.sourceRegionId)) {
            return false;
        }
        EarthworkSite site = project.getActiveSite();
        if (site == null || !site.getId().equals(result.sourceSiteId)) {
            return false;
        }
        GradingRegion region = project.getRegion(expectedRegionId);
        if (region == null) {
            return false;
        }
        return designRevision(project, region) == result.designRevision;
    }

    /**
     * 正式校验：设计身份 + 世界身份。{@code world == null} 时失败即拒绝。
     */
    public static boolean matchesCurrent(
            EarthworkGenerationResult result,
            EarthworkProject project,
            String expectedRegionId,
            World world) {
        if (!matchesDesign(result, project, expectedRegionId)) {
            return false;
        }
        if (world == null) {
            return false;
        }
        if (result.worldKey == null || result.worldKey.isBlank()) {
            return false;
        }
        return TerrainSnapshotCache.worldKey(world).equals(result.worldKey);
    }

    /**
     * 规范化轮廓指纹：量化坐标后对旋转起点与顺/逆时针方向取不变哈希。
     */
    public static long normalizedOutlineFingerprint(List<Vec2d> points) {
        List<long[]> ring = quantizedRing(points);
        if (ring.size() < 3) {
            return 17L + ring.size();
        }
        long forward = rotatedRingHash(ring, false);
        long reverse = rotatedRingHash(ring, true);
        return Math.min(forward, reverse);
    }

    private static long hashSiteDesign(EarthworkSite site) {
        long hash = 17L;
        hash = 31L * hash + hashPoints(site.getSiteBoundary());
        hash = 31L * hash + hashCompositionPolicy(site.getCompositionPolicy());
        hash = 31L * hash + hashMaterial(site.getMaterialModel());
        hash = 31L * hash + safeEnum(site.getCutMaterialClass());
        hash = 31L * hash + safeEnum(site.getFillMaterialClass());

        List<GradingZone> zones = new ArrayList<>(site.getGradingZones().values());
        zones.sort(Comparator.comparing(z -> z.getId() != null ? z.getId() : "", String::compareTo));
        hash = 31L * hash + zones.size();
        for (GradingZone zone : zones) {
            hash = 31L * hash + hashZone(zone);
        }

        List<Breakline> breaklines = new ArrayList<>(site.getBreaklines());
        breaklines.sort(Comparator.comparing(b -> b.getId() != null ? b.getId() : "", String::compareTo));
        for (Breakline breakline : breaklines) {
            hash = 31L * hash + hashBreakline(breakline);
        }

        List<ExclusionZone> exclusions = new ArrayList<>(site.getExclusionZones());
        exclusions.sort(Comparator.comparing(e -> e.getId() != null ? e.getId() : "", String::compareTo));
        for (ExclusionZone exclusion : exclusions) {
            hash = 31L * hash + hashExclusion(exclusion);
        }

        List<RetainingEdge> edges = new ArrayList<>(site.getRetainingEdges());
        edges.sort(Comparator.comparing(e -> e.getId() != null ? e.getId() : "", String::compareTo));
        for (RetainingEdge edge : edges) {
            hash = 31L * hash + hashRetainingEdge(edge);
        }
        return hash;
    }

    private static long hashZone(GradingZone zone) {
        if (zone == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeStr(zone.getId());
        hash = 31L * hash + Boolean.hashCode(zone.isEnabled());
        hash = 31L * hash + safeEnum(zone.getType());
        hash = 31L * hash + zone.getPriority();
        hash = 31L * hash + hashPoints(zone.getOuterPoints());
        hash = 31L * hash + hashHoles(zone.getHoles());
        hash = 31L * hash + safeStr(zone.getBuildingFootprintRef());
        hash = 31L * hash + safeStr(zone.getRoadEdgeRef());
        hash = 31L * hash + safeEnum(zone.getCutMaterialClass());
        hash = 31L * hash + safeEnum(zone.getFillMaterialClass());
        hash = 31L * hash + hashMaterial(zone.getMaterialOverride());
        hash = 31L * hash + hashEdgeSettings(zone.getEdgeSettings());
        hash = 31L * hash + hashVerticalPolicy(zone.getVerticalAdjustmentPolicy());
        hash = 31L * hash + hashDesignSurface(zone.getDesignSurface());

        GradingRegion region = zone.getRegion();
        if (region != null) {
            hash = 31L * hash + hashRegionFallback(region);
        }
        return hash;
    }

    private static long hashRegionFallback(GradingRegion region) {
        long hash = 17L;
        hash = 31L * hash + safeEnum(region.getSurfaceMode());
        hash = 31L * hash + Boolean.hashCode(region.isAutoBalance());
        Integer manual = region.getManualTargetElevation();
        hash = 31L * hash + (manual != null ? manual : Integer.MIN_VALUE);
        hash = 31L * hash + region.getPreviewGridSize();
        hash = 31L * hash + Double.hashCode(region.getSlopeDirectionDegrees());
        hash = 31L * hash + region.getSlopePitchRatio();
        hash = 31L * hash + Boolean.hashCode(region.isFitSlopeBalanceCutFill());
        hash = 31L * hash + hashPoints(region.getOuterPoints());
        hash = 31L * hash + hashHoles(region.getHoles());
        hash = 31L * hash + safeStr(region.getFillMaterial());
        hash = 31L * hash + safeStr(region.getCutExposeMaterial());
        hash = 31L * hash + hashMaterial(region.getMaterialProperties());
        Vec2d anchor = region.getSlopeAnchorCanvas();
        if (anchor != null) {
            hash = 31L * hash + Double.hashCode(quantize(anchor.x));
            hash = 31L * hash + Double.hashCode(quantize(anchor.y));
        }
        Integer anchorElev = region.getSlopeAnchorElevation();
        hash = 31L * hash + (anchorElev != null ? anchorElev : Integer.MIN_VALUE);
        for (int i = 0; i < 3; i++) {
            hash = 31L * hash + Double.hashCode(quantize(region.getThreePointCanvasX(i)));
            hash = 31L * hash + Double.hashCode(quantize(region.getThreePointCanvasY(i)));
            hash = 31L * hash + region.getThreePointElevation(i);
        }
        return hash;
    }

    private static long hashDesignSurface(DesignSurface surface) {
        if (surface == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeEnum(surface.getKind());
        hash = 31L * hash + Boolean.hashCode(surface.isAutoBalance());
        Integer manual = surface.getManualTargetElevation();
        hash = 31L * hash + (manual != null ? manual : Integer.MIN_VALUE);
        hash = 31L * hash + Boolean.hashCode(surface.isFitSlopeBalanceCutFill());
        hash = 31L * hash + Double.hashCode(surface.getSlopeDirectionDegrees());
        hash = 31L * hash + surface.getSlopePitchRatio();
        Vec2d anchor = surface.getAnchorCanvas();
        if (anchor != null) {
            hash = 31L * hash + Double.hashCode(quantize(anchor.x));
            hash = 31L * hash + Double.hashCode(quantize(anchor.y));
        }
        Integer anchorElev = surface.getAnchorElevation();
        hash = 31L * hash + (anchorElev != null ? anchorElev : Integer.MIN_VALUE);
        for (int i = 0; i < 3; i++) {
            hash = 31L * hash + Double.hashCode(quantize(surface.getThreePointCanvasX(i)));
            hash = 31L * hash + Double.hashCode(quantize(surface.getThreePointCanvasY(i)));
            hash = 31L * hash + surface.getThreePointElevation(i);
        }
        Integer elev = surface.getElevation();
        hash = 31L * hash + (elev != null ? elev : Integer.MIN_VALUE);
        hash = 31L * hash + safeStr(surface.getBuildingFootprintRef());
        hash = 31L * hash + safeStr(surface.getRoadEdgeRef());
        hash = 31L * hash + safeEnum(surface.getElevationSource());
        Integer bottom = surface.getBottomElevation();
        hash = 31L * hash + (bottom != null ? bottom : Integer.MIN_VALUE);
        ExcavationPitParameters pit = surface.getExcavationPit();
        hash = 31L * hash + pit.getBasementFloorDepth();
        hash = 31L * hash + pit.getFoundationDepth();
        hash = 31L * hash + pit.getWorkingAllowance();
        hash = 31L * hash + surface.getWorkingMarginBlocks();
        hash = 31L * hash + surface.getVerticalOffset();
        hash = 31L * hash + hashBakedGrid(surface.getBakedElevationGrid());
        for (DesignSurfaceFacet facet : surface.getFacets()) {
            hash = 31L * hash + hashFacet(facet);
        }
        return hash;
    }

    private static long hashFacet(DesignSurfaceFacet facet) {
        if (facet == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeStr(facet.getId());
        hash = 31L * hash + hashPoints(facet.getOuterPoints());
        hash = 31L * hash + hashHoles(facet.getHoles());
        hash = 31L * hash + hashDesignSurface(facet.getPlane());
        return hash;
    }

    private static long hashBakedGrid(BakedElevationGrid grid) {
        if (grid == null || grid.isEmpty()) {
            return 0L;
        }
        long hash = 17L + grid.sampleCount();
        for (BakedElevationGrid.Sample sample : grid.toSamples()) {
            hash = 31L * hash + sample.worldX();
            hash = 31L * hash + sample.worldZ();
            hash = 31L * hash + sample.targetY();
        }
        return hash;
    }

    private static long hashEdgeSettings(ZoneEdgeSettings edge) {
        if (edge == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeEnum(edge.getDefaultTreatment());
        hash = 31L * hash + edge.getCutSlopePitchRatio();
        hash = 31L * hash + edge.getFillSlopePitchNumerator();
        hash = 31L * hash + edge.getFillSlopePitchDenominator();
        hash = 31L * hash + edge.getMaximumReachBlocks();
        hash = 31L * hash + edge.getBenchWidthBlocks();
        hash = 31L * hash + safeStr(edge.getWallMaterial());
        hash = 31L * hash + Boolean.hashCode(edge.isUseLinkedZoneFillMaterial());
        for (BoundaryEdgeOverride override : edge.getEdgeOverrides()) {
            if (override == null) {
                continue;
            }
            hash = 31L * hash + override.getEdgeIndex();
            hash = 31L * hash + safeEnum(override.getTreatment());
        }
        return hash;
    }

    private static long hashVerticalPolicy(VerticalAdjustmentPolicy policy) {
        if (policy == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeEnum(policy.getMode());
        hash = 31L * hash + policy.getMinOffset();
        hash = 31L * hash + policy.getMaxOffset();
        hash = 31L * hash + Float.hashCode(policy.getWeight());
        return hash;
    }

    private static long hashCompositionPolicy(CompositionPolicy policy) {
        if (policy == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + safeStr(policy.getOverlapResolution());
        hash = 31L * hash + safeEnum(policy.getBalanceScopeEnum());
        hash = 31L * hash + safeEnum(policy.getOptimizationModeEnum());
        hash = 31L * hash + Boolean.hashCode(policy.isBalanceResidualUniformPolish());
        hash = 31L * hash + safeStr(policy.getBreaklinePrecedence());
        hash = 31L * hash + policy.getBlendWidthBlocks();
        return hash;
    }

    private static long hashBreakline(Breakline breakline) {
        long hash = 17L;
        hash = 31L * hash + safeStr(breakline.getId());
        hash = 31L * hash + safeStr(breakline.getRole());
        hash = 31L * hash + safeStr(breakline.getLeftZoneId());
        hash = 31L * hash + safeStr(breakline.getRightZoneId());
        hash = 31L * hash + hashPoints(breakline.getPoints());
        return hash;
    }

    private static long hashExclusion(ExclusionZone exclusion) {
        long hash = 17L;
        hash = 31L * hash + safeStr(exclusion.getId());
        hash = 31L * hash + safeStr(exclusion.getMode());
        hash = 31L * hash + hashPoints(exclusion.getOuterPoints());
        hash = 31L * hash + hashHoles(exclusion.getHoles());
        return hash;
    }

    private static long hashRetainingEdge(RetainingEdge edge) {
        long hash = 17L;
        hash = 31L * hash + safeStr(edge.getId());
        hash = 31L * hash + hashPoints(edge.getPolyline());
        hash = 31L * hash + edge.getTopElevation();
        hash = 31L * hash + edge.getBottomElevation();
        hash = 31L * hash + safeStr(edge.getWallMaterial());
        hash = 31L * hash + safeStr(edge.getLinkedZoneId());
        hash = 31L * hash + Boolean.hashCode(edge.isUseLinkedZoneFillMaterial());
        return hash;
    }

    private static long hashMaterial(MaterialConversionModel model) {
        if (model == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + Float.hashCode(model.reusableRatio());
        hash = 31L * hash + Float.hashCode(model.cutToCompactedFillRatio());
        return hash;
    }

    private static long hashHoles(List<List<Vec2d>> holes) {
        if (holes == null || holes.isEmpty()) {
            return 0L;
        }
        long hash = 17L + holes.size();
        for (List<Vec2d> hole : holes) {
            hash = 31L * hash + normalizedOutlineFingerprint(hole);
        }
        return hash;
    }

    private static long hashPoints(List<Vec2d> points) {
        return normalizedOutlineFingerprint(points);
    }

    private static List<long[]> quantizedRing(List<Vec2d> points) {
        List<Vec2d> normalized = PolygonRegionUtils.normalizeRegionOutline(points);
        List<long[]> ring = new ArrayList<>(normalized.size());
        for (Vec2d point : normalized) {
            if (point == null) {
                continue;
            }
            ring.add(new long[] {
                Double.doubleToLongBits(quantize(point.x)),
                Double.doubleToLongBits(quantize(point.y))
            });
        }
        return ring;
    }

    private static long rotatedRingHash(List<long[]> ring, boolean reverse) {
        int n = ring.size();
        int start = 0;
        for (int i = 1; i < n; i++) {
            if (compareQuantized(ring.get(i), ring.get(start)) < 0) {
                start = i;
            }
        }
        long hash = 17L + n;
        for (int i = 0; i < n; i++) {
            int index = reverse
                ? Math.floorMod(start - i, n)
                : Math.floorMod(start + i, n);
            long[] q = ring.get(index);
            hash = 31L * hash + q[0];
            hash = 31L * hash + q[1];
        }
        return hash;
    }

    private static int compareQuantized(long[] a, long[] b) {
        int cmp = Long.compare(a[0], b[0]);
        return cmp != 0 ? cmp : Long.compare(a[1], b[1]);
    }

    private static double quantize(double value) {
        return Math.rint(value * 100.0) / 100.0;
    }

    private static long safeStr(String value) {
        return value != null ? value.hashCode() : 0L;
    }

    private static long safeEnum(Enum<?> value) {
        return value != null ? value.ordinal() : -1L;
    }
}
