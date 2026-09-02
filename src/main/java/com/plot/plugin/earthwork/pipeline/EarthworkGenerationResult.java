package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
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
    public int resolvedElevation;
    public int resolvedElevationMin;
    public int resolvedElevationMax;
    public boolean slopedSurface;
    public boolean siteGeneration;
    public final List<String> warnings = new ArrayList<>();
    public int calculationCellCount;

    public enum ChangeType {
        CUT, FILL
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
