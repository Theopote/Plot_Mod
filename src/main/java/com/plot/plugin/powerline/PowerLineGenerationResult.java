package com.plot.plugin.powerline;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.geometry.ConductorSpanGeometry;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力线路生成结果。
 */
public class PowerLineGenerationResult {
    public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
    public final List<String> warnings = new ArrayList<>();
    public int poleCount;
    public double wireLength;
    public int structureBlockCount;
    public int braceBlockCount;
    public int armBlockCount;
    public final PowerLineFootprint footprint;
    public final Map<TowerRole, Integer> towersByRole = new EnumMap<>(TowerRole.class);
    public final List<PolePlacement> polePlacements = new ArrayList<>();
    public final List<ConductorSpanGeometry> conductorSpans = new ArrayList<>();
    public final List<PowerPoleSite> poleSites = new ArrayList<>();

    public PowerLineGenerationResult(PowerLineFootprint footprint) {
        this.footprint = footprint;
    }

    public void recordRole(TowerRole role) {
        TowerRole key = role != null ? role : TowerRole.SUSPENSION;
        towersByRole.merge(key, 1, Integer::sum);
    }

    public int roleCount(TowerRole role) {
        return towersByRole.getOrDefault(role, 0);
    }

    public int blockCount() {
        return placementRecords.size();
    }

    public PowerLineGeometryModel toGeometryModel() {
        return PowerLineGeometryModel.from(poleSites, polePlacements, conductorSpans);
    }
}
