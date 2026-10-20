package com.plot.plugin.earthwork.manager;

import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import net.minecraft.world.World;

/**
 * 预览结果身份绑定与设计/地形修订指纹。
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

    public static long designRevision(EarthworkProject project, GradingRegion region) {
        if (region == null) {
            return 0L;
        }
        long hash = 17L;
        hash = 31L * hash + region.getId().hashCode();
        hash = 31L * hash + region.getSurfaceMode().ordinal();
        hash = 31L * hash + Boolean.hashCode(region.isAutoBalance());
        Integer manual = region.getManualTargetElevation();
        hash = 31L * hash + (manual != null ? manual : Integer.MIN_VALUE);
        hash = 31L * hash + region.getPreviewGridSize();
        hash = 31L * hash + Double.hashCode(region.getSlopeDirectionDegrees());
        hash = 31L * hash + region.getSlopePitchRatio();
        hash = 31L * hash + Boolean.hashCode(region.isFitSlopeBalanceCutFill());
        if (project != null) {
            GradingZone zone = project.getZone(region.getId());
            if (zone != null) {
                ZoneEdgeSettings edge = zone.getEdgeSettings();
                hash = 31L * hash + edge.getDefaultTreatment().ordinal();
                hash = 31L * hash + edge.getCutSlopePitchRatio();
                hash = 31L * hash + edge.getFillSlopePitchNumerator();
                hash = 31L * hash + edge.getFillSlopePitchDenominator();
                hash = 31L * hash + edge.getMaximumReachBlocks();
                hash = 31L * hash + edge.getBenchWidthBlocks();
            }
        }
        return hash;
    }

    public static boolean matchesCurrent(
            EarthworkGenerationResult result,
            EarthworkProject project,
            String expectedRegionId,
            World world) {
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
        if (world != null) {
            String currentWorld = TerrainSnapshotCache.worldKey(world);
            if (!currentWorld.equals(result.worldKey)) {
                return false;
            }
        }
        GradingRegion region = project.getRegion(expectedRegionId);
        if (region == null) {
            return false;
        }
        return designRevision(project, region) == result.designRevision;
    }
}
