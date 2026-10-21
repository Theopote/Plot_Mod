package com.plot.plugin.earthwork.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.solver.EarthworkBalanceStatus;
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
    /** 与 {@link #placementRecords} 一一对应的 Zone 归属。 */
    public final Map<BlockPos, PlacementMeta> placementMeta = new LinkedHashMap<>();
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
    /** 自动平衡搜索后残余 |Cut−Fill|（仅当 status ≠ NOT_REQUESTED 时有意义）。 */
    public long balanceResidualImbalance;
    public EarthworkBalanceStatus balanceStatus = EarthworkBalanceStatus.NOT_REQUESTED;
    /** 统一 ΔY 搜索半幅；未运行时为 0。 */
    public int balanceSearchHalfRange;
    /** 无法归属到任何 Zone 的挖/填改方块数（仍计入全场）。 */
    public long unassignedCutChangedBlocks;
    public long unassignedFillChangedBlocks;
    /** 挡土墙等 STRUCTURE 方块数（不计入挖填改方块）。 */
    public long structureChangedBlocks;

    /** 生成时绑定的区域 ID（施工前必须与当前选中区域一致）。 */
    public String sourceRegionId = "";
    /** 生成时绑定的场地 ID。 */
    public String sourceSiteId = "";
    /** 生成时的世界标识（如 dimension id）。 */
    public String worldKey = "";
    /** 设计参数指纹（目标高度、边坡等）。 */
    public long designRevision;
    /** 地形快照指纹。 */
    public long terrainRevision;

    /**
     * 用最终 {@link #placementRecords} 回写改方块计数，使预览虚影、落地与报告使用同一份变更。
     * <p>
     * 几何挖填方量保持不变；实际修改方块数按全场、各 Zone、未归属三类聚合。
     * {@link ChangeType#STRUCTURE} 单独统计，不计入挖/填改方块数。
     * <p>
     * 不变量：{@code siteCut = Σ zoneCut + unassignedCut}（FILL 同理）。
     */
    public void syncChangedBlocksFromPlacements() {
        long cut = 0L;
        long fill = 0L;
        long unassignedCut = 0L;
        long unassignedFill = 0L;
        long structure = 0L;
        Map<String, long[]> zoneChanged = new LinkedHashMap<>();
        for (BlockPos pos : placementRecords.keySet()) {
            ChangeType type = changeTypes.get(pos);
            if (type == ChangeType.STRUCTURE) {
                structure++;
                continue;
            }
            if (type != ChangeType.CUT && type != ChangeType.FILL) {
                continue;
            }
            if (type == ChangeType.CUT) {
                cut++;
            } else {
                fill++;
            }
            String zoneId = resolveZoneId(pos);
            if (zoneId == null || zoneId.isBlank()) {
                if (type == ChangeType.CUT) {
                    unassignedCut++;
                } else {
                    unassignedFill++;
                }
                continue;
            }
            long[] counts = zoneChanged.computeIfAbsent(zoneId, ignored -> new long[2]);
            if (type == ChangeType.CUT) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        }
        unassignedCutChangedBlocks = unassignedCut;
        unassignedFillChangedBlocks = unassignedFill;
        structureChangedBlocks = structure;
        volumeReport = volumeReport.withChangedBlocks(cut, fill);
        siteVolumeReport = siteVolumeReport.withChangedBlocks(
            cut, fill, zoneChanged, unassignedCut, unassignedFill, structure);
    }

    private String resolveZoneId(BlockPos pos) {
        PlacementMeta meta = placementMeta.get(pos);
        if (meta != null && meta.isAssigned()) {
            return meta.zoneId();
        }
        // 兼容旧路径：无 meta 时回退网格（不应作为主路径）
        if (pos == null || designTerrainGrid == null) {
            return null;
        }
        var cell = designTerrainGrid.get(pos.getX(), pos.getZ());
        return cell != null ? cell.zoneId() : null;
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
