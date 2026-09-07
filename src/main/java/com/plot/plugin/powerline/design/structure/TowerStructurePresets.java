package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;

import java.util.ArrayList;
import java.util.List;

/** 内置塔体结构预设。 */
public final class TowerStructurePresets {
    public static final String STATION_S0 = "s0";
    public static final String STATION_S1 = "s1";
    public static final String STATION_S2 = "s2";
    public static final String STATION_S3 = "s3";

    private TowerStructurePresets() {
    }

    /** 四腿收缩格构塔：H=0/8/16/24，X 撑，一层横担。 */
    public static TowerStructureDesign taperedLatticeTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.setPrimaryMaterial(MaterialMix.single("minecraft:iron_bars"));
        structure.setBraceMaterial(MaterialMix.single("minecraft:iron_bars"));

        TowerStation s0 = new TowerStation(STATION_S0, 0, 5, 5);
        TowerStation s1 = new TowerStation(STATION_S1, 8, 4, 4);
        TowerStation s2 = new TowerStation(STATION_S2, 16, 2, 2);
        TowerStation s3 = new TowerStation(STATION_S3, 24, 1, 1);
        structure.addStation(s0);
        structure.addStation(s1);
        structure.addStation(s2);
        structure.addStation(s3);

        structure.addBay(bayWithBracing(s0.getId(), s1.getId()));
        structure.addBay(bayWithBracing(s1.getId(), s2.getId()));
        structure.addBay(bayWithBracing(s2.getId(), s3.getId()));

        TowerArm arm = new TowerArm("arm_main", 18, 6);
        arm.setSide(TowerArmSide.BOTH);
        arm.setMaterial(MaterialMix.single("minecraft:iron_bars"));
        structure.addArm(arm);
        return structure;
    }

    private static TowerBay bayWithBracing(String lowerId, String upperId) {
        TowerBay bay = new TowerBay(lowerId, upperId);
        bay.setFrontBackBracing(BracingPattern.X);
        bay.setSideBracing(BracingPattern.X);
        bay.setHorizontalRing(true);
        return bay;
    }

    /** 带塔体 + 三相挂点的完整杆塔设计。 */
    public static com.plot.plugin.powerline.design.PoleDesign taperedLatticePoleDesign(String id, String name) {
        com.plot.plugin.powerline.design.PoleDesign design =
            new com.plot.plugin.powerline.design.PoleDesign(id, name);
        design.setTowerStructure(taperedLatticeTower());
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(18.0, -6, 0, 6));
        return design;
    }

    /** 为已有 station 列表自动创建默认 bay。 */
    public static List<TowerBay> defaultBaysForStations(List<TowerStation> stations) {
        List<TowerStation> sorted = new ArrayList<>(stations);
        sorted.sort(java.util.Comparator.comparingDouble(TowerStation::getHeight));
        List<TowerBay> bays = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            bays.add(bayWithBracing(sorted.get(i - 1).getId(), sorted.get(i).getId()));
        }
        return bays;
    }
}
