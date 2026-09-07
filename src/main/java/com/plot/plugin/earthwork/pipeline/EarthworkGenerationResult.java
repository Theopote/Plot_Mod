package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkElevationVolumeCurve;
import com.plot.plugin.earthwork.solver.EarthworkSectionProfile;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 土方生成管线输出：方量报告、体素 {@link BlockRecord} 与预览网格采样。
 */
public class EarthworkGenerationResult {
    public TerrainSnapshot existingTerrainSnapshot = TerrainSnapshot.empty();
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
    public final Map<BlockPos, ChangeType> changeTypes = new LinkedHashMap<>();
    public final List<GridSample> gridSamples = new ArrayList<>();
    public EarthworkVolumeReport volumeReport = EarthworkVolumeReport.empty();
    public SiteEarthworkReport siteVolumeReport = SiteEarthworkReport.empty();
    public EarthworkProjectReport projectReport = EarthworkProjectReport.empty();
    public DesignTerrainGrid designTerrainGrid;
    public EarthworkElevationVolumeCurve elevationVolumeCurve = EarthworkElevationVolumeCurve.EMPTY;
    public EarthworkSectionProfile sectionProfile = EarthworkSectionProfile.EMPTY;
    /** 自然边坡场景下禁用整网平移近似曲线，仅展示耦合求解的最终方量。 */
    public boolean elevationVolumeCurveRequiresSlopeCoupledSolver = false;
    public int resolvedElevation;
    public int resolvedElevationMin;
    public int resolvedElevationMax;
    public boolean slopedSurface;
    public boolean siteGeneration;
    public final List<String> warnings = new ArrayList<>();
    public int calculationCellCount;

    /**
     * 用最终 {@link #placementRecords} 回写改方块计数，使预览虚影、落地与报告使用同一份变更。
     */
    public void syncChangedBlocksFromPlacements() {
        long cut = 0L;
        long fill = 0L;
        for (BlockPos pos : placementRecords.keySet()) {
            ChangeType type = changeTypes.get(pos);
            if (type == ChangeType.CUT) {
                cut++;
            } else if (type == ChangeType.FILL) {
                fill++;
            }
        }
        volumeReport = volumeReport.withChangedBlocks(cut, fill);
        siteVolumeReport = siteVolumeReport.withTotalsChangedBlocks(cut, fill);
    }

    public void attachPlayerInsights() {
        attachPlayerInsights(true);
    }

    /**
     * @param allowLegacyShiftCurve 为 {@code false} 时跳过整网 ΔY 平移近似曲线（自然边坡需逐候选重建坡面）。
     */
    public void attachPlayerInsights(boolean allowLegacyShiftCurve) {
        elevationVolumeCurveRequiresSlopeCoupledSolver = !allowLegacyShiftCurve;
        if (designTerrainGrid != null && designTerrainGrid.cellCount() > 0) {
            sectionProfile = EarthworkSectionProfile.fromGrid(designTerrainGrid);
        } else {
            sectionProfile = EarthworkSectionProfile.EMPTY;
        }
        if (!allowLegacyShiftCurve) {
            elevationVolumeCurve = EarthworkElevationVolumeCurve.EMPTY;
            return;
        }
        if (designTerrainGrid != null && designTerrainGrid.cellCount() > 0) {
            elevationVolumeCurve = EarthworkElevationVolumeCurve.fromGrid(designTerrainGrid, resolvedElevation);
            return;
        }
        elevationVolumeCurve = EarthworkElevationVolumeCurve.fromTerrain(
            existingTerrainSnapshot, resolvedElevation);
    }

    public enum ChangeType {
        CUT,
        FILL,
        /** 挡土墙等结构体，不计入填方施工量。 */
        STRUCTURE
    }

    public static class GridSample {
        public final Vec2d center;
        public final int groundY;
        public final ChangeType changeType;

        public GridSample(Vec2d center, int groundY, ChangeType changeType) {
            this.center = center;
            this.groundY = groundY;
            this.changeType = changeType;
        }
    }
}
