package com.plot.plugin.powerline.design.structure;

import java.util.HashSet;
import java.util.Set;

/**
 * 在编译/预设定型阶段将塔体尺寸规范为可居中放置点状装饰的奇数占用。
 */
public final class TowerStructureParityEnforcer {

    private TowerStructureParityEnforcer() {
    }

    public static void enforce(TowerStructureDesign structure) {
        if (structure == null || !TowerFootprintParity.structureNeedsCenterColumn(structure)) {
            return;
        }
        Set<String> adjustedStationIds = new HashSet<>();
        for (TowerDecoration decoration : structure.getDecorations()) {
            if (!TowerFootprintParity.requiresUniqueCenterColumn(decoration)) {
                continue;
            }
            TowerStation station = TowerFootprintParity.stationForDecorationHeight(
                structure,
                decoration.getBaseHeight());
            if (station == null || !adjustedStationIds.add(station.getId())) {
                continue;
            }
            double snappedWidth = TowerFootprintParity.snapHalfDimensionToOddBlockExtent(station.getHalfWidth());
            double snappedDepth = TowerFootprintParity.snapHalfDimensionToOddBlockExtent(station.getHalfDepth());
            station.setHalfWidth(snappedWidth);
            station.setHalfDepth(snappedDepth);
        }
    }
}
