package com.plot.plugin.earthwork.design;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 解析与建筑关联、由土方主导标高的 {@link GradingZoneType#BUILDING_PAD} 设计标高。
 * <p>
 * 当垫层标高来源为 {@link DesignSurfaceElevationSource#BUILDING_BASE_ELEVATION} 时不返回结果，
 * 避免与 {@link BuildingFootprintResolver} 形成循环依赖。
 */
public final class BuildingPadElevationService {
    private BuildingPadElevationService() {
    }

    public record PadMatch(EarthworkSite site, GradingZone zone) {
    }

    public static Optional<Integer> resolveEarthworkOwnedPadElevation(
            EarthworkProject project,
            String buildingId,
            List<Vec2d> footprintPoints,
            DesignTerrainGrid previewGrid,
            ICoordinateService coordinateService) {
        if (project == null || buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        Optional<PadMatch> padMatch = findBuildingPad(project, buildingId);
        if (padMatch.isEmpty()) {
            return Optional.empty();
        }
        PadMatch match = padMatch.get();
        GradingZone zone = match.zone();
        DesignSurface surface = zone.getDesignSurface();
        if (!isEarthworkOwnedElevation(surface)) {
            return Optional.empty();
        }

        Optional<Integer> explicit = resolveExplicitElevation(match.site(), zone, surface);
        if (explicit.isPresent()) {
            return explicit;
        }

        if (previewGrid != null && previewGrid.cellCount() > 0
            && footprintPoints != null && footprintPoints.size() >= 3
            && coordinateService != null) {
            List<Integer> gridSamples = samplePreviewGridTargets(
                previewGrid, footprintPoints, zone.getId(), coordinateService);
            if (!gridSamples.isEmpty()) {
                return Optional.of(BuildingFoundationUtils.computeBaseElevation(gridSamples, null));
            }
        }
        return Optional.empty();
    }

    public static Optional<PadMatch> findBuildingPad(EarthworkProject project, String buildingId) {
        if (project == null || buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        EarthworkSite activeSite = project.getActiveSite();
        Optional<PadMatch> activeMatch = findInSite(activeSite, buildingId);
        if (activeMatch.isPresent()) {
            return activeMatch;
        }
        for (EarthworkSite site : project.getSites().values()) {
            if (Objects.equals(site, activeSite)) {
                continue;
            }
            Optional<PadMatch> match = findInSite(site, buildingId);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<PadMatch> findInSite(EarthworkSite site, String buildingId) {
        if (site == null) {
            return Optional.empty();
        }
        for (GradingZone zone : site.getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            if (zone.getType() != GradingZoneType.BUILDING_PAD) {
                continue;
            }
            String ref = BuildingFootprintResolver.resolveFootprintRef(zone, zone.getDesignSurface());
            if (buildingId.equals(ref)) {
                return Optional.of(new PadMatch(site, zone));
            }
        }
        return Optional.empty();
    }

    static boolean isEarthworkOwnedElevation(DesignSurface surface) {
        return surface != null
            && surface.getElevationSource() != DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION;
    }

    private static Optional<Integer> resolveExplicitElevation(
            EarthworkSite site,
            GradingZone zone,
            DesignSurface surface) {
        Integer base = surface.getElevation();
        if (base == null) {
            base = surface.getManualTargetElevation();
        }
        if (base == null) {
            return Optional.empty();
        }
        return Optional.of(applyZoneOffsets(site, zone, surface, base));
    }

    private static int applyZoneOffsets(
            EarthworkSite site,
            GradingZone zone,
            DesignSurface surface,
            int base) {
        int elevation = base + surface.getVerticalOffset();
        Integer balanceOffset = site.getLastZoneVerticalOffsets().get(zone.getId());
        if (balanceOffset != null) {
            elevation += balanceOffset;
        }
        return elevation;
    }

    private static List<Integer> samplePreviewGridTargets(
            DesignTerrainGrid grid,
            List<Vec2d> footprintPoints,
            String padZoneId,
            ICoordinateService coordinateService) {
        List<Integer> samples = new ArrayList<>();
        for (Vec2d center : PolygonRegionUtils.collectFootprintCellCenters(footprintPoints)) {
            BlockPos block = PolygonRegionUtils.canvasToBlockXZ(center, coordinateService);
            DesignTerrainCell cell = grid.get(block.getX(), block.getZ());
            if (cell == null || cell.excluded() || cell.noTouch()) {
                continue;
            }
            if (padZoneId != null && !padZoneId.isBlank() && !padZoneId.equals(cell.zoneId())) {
                continue;
            }
            samples.add(cell.targetY());
        }
        return samples;
    }
}
