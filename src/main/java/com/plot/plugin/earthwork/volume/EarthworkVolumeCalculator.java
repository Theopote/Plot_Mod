package com.plot.plugin.earthwork.volume;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.grading.GradingPlane;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.geometry.ZoneBoundarySlopeApplicator;
import com.plot.plugin.earthwork.model.EarthMaterialProperties;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 遍历设计地形或平面目标，委托 {@link EarthworkVoxelizer} 累计几何方量与落地方块。
 */
public final class EarthworkVolumeCalculator {
    private final EarthworkVoxelizer voxelizer;

    public EarthworkVolumeCalculator(EarthworkVoxelizer voxelizer) {
        this.voxelizer = voxelizer;
    }

    public void computeFromPlane(
            GradingRegion region,
            World world,
            TerrainSnapshot terrain,
            GradingPlane plane,
            EarthworkGenerationResult result,
            int previewGridSize,
            ZoneEdgeSettings edgeSettings) {
        SiteEarthworkReport.VolumeMetrics totals = new SiteEarthworkReport.VolumeMetrics();
        List<Vec2d> regionOutline = region.getOuterPoints();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            int designTarget = plane.evaluateAt(column.worldX(), column.worldZ());
            int targetElevation = ZoneBoundarySlopeApplicator.resolveLegacyTargetY(
                column.center(),
                column.groundY(),
                designTarget,
                regionOutline,
                edgeSettings);
            voxelizer.applyColumn(
                region,
                world,
                column,
                targetElevation,
                previewGridSize,
                result,
                totals,
                null);
        }
        result.volumeReport = totals.toReport(region.getMaterialProperties());
        result.siteVolumeReport = new SiteEarthworkReport(result.volumeReport, Map.of());
    }

    public void computeFromDesignGrid(
            EarthworkSite site,
            World world,
            DesignTerrainGrid grid,
            EarthworkGenerationResult result,
            int previewGridSize) {
        Map<String, GradingZone> zonesById = site.getGradingZones();
        Map<String, SiteEarthworkReport.VolumeMetrics> zoneMetrics = new HashMap<>();
        SiteEarthworkReport.VolumeMetrics totals = new SiteEarthworkReport.VolumeMetrics();
        EarthMaterialProperties siteMaterial = site.getMaterialModel();

        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork()) {
                continue;
            }
            GradingZone zone = zonesById.get(cell.zoneId());
            if (zone == null) {
                continue;
            }
            GradingRegion region = zone.getRegion();
            SiteEarthworkReport.VolumeMetrics zoneVolume = zoneMetrics.computeIfAbsent(
                zone.getId(),
                ignored -> new SiteEarthworkReport.VolumeMetrics());

            voxelizer.applyColumn(
                region,
                world,
                toColumn(cell),
                cell.targetY(),
                previewGridSize,
                result,
                totals,
                zoneVolume);
        }

        result.volumeReport = totals.toReport(siteMaterial);
        result.siteVolumeReport = SiteEarthworkReport.fromMetrics(totals, zoneMetrics, siteMaterial);
        result.projectReport = EarthworkProjectReport.Builder.build(site, result.siteVolumeReport);
    }

    private static TerrainSnapshot.Column toColumn(DesignTerrainCell cell) {
        return new TerrainSnapshot.Column(
            cell.center(),
            cell.worldX(),
            cell.worldZ(),
            cell.existingGroundY());
    }
}
