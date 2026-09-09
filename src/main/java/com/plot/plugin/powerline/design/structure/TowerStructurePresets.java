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

    /** 超大型工业格构塔：H≈56，宽塔身，三层横担。 */
    public static TowerStructureDesign megaLatticeTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.setPrimaryMaterial(MaterialMix.single("minecraft:iron_block"));
        structure.setBraceMaterial(MaterialMix.single("minecraft:iron_bars"));
        addTaperedStations(structure, new double[] {0, 12, 24, 36, 48, 56}, new double[] {10, 9, 7, 5, 3, 2});
        addArms(structure, new double[] {38, 46, 52}, 14, 4);
        return structure;
    }

    /** 工业门架塔：宽柱 + 多层横梁，适合厂区/变电站。 */
    public static TowerStructureDesign industrialPortalTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.setPrimaryMaterial(MaterialMix.single("minecraft:iron_block"));
        structure.setBraceMaterial(MaterialMix.single("minecraft:iron_bars"));
        structure.addStation(new TowerStation("s0", 0, 12, 12));
        structure.addStation(new TowerStation("s1", 16, 12, 12));
        structure.addStation(new TowerStation("s2", 32, 10, 10));
        structure.addStation(new TowerStation("s3", 44, 8, 8));
        structure.addStation(new TowerStation("s4", 54, 4, 4));
        structure.addBay(bayWithBracing("s0", "s1"));
        structure.addBay(bayWithBracing("s1", "s2"));
        structure.addBay(bayWithBracing("s2", "s3"));
        structure.addBay(bayWithBracing("s3", "s4"));
        addArms(structure, new double[] {18, 28, 38}, 16, 5);
        return structure;
    }

    /** 怪物级输电塔：H≈88，极宽塔身，四层横担。 */
    public static TowerStructureDesign monsterPylonTower() {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.setPrimaryMaterial(MaterialMix.single("minecraft:iron_block"));
        structure.setBraceMaterial(MaterialMix.single("minecraft:iron_bars"));
        addTaperedStations(structure, new double[] {0, 15, 30, 45, 60, 75, 88}, new double[] {14, 13, 11, 9, 7, 5, 3});
        addArms(structure, new double[] {58, 68, 78, 84}, 18, 6);
        return structure;
    }

    private static void addTaperedStations(TowerStructureDesign structure, double[] heights, double[] halfWidths) {
        for (int i = 0; i < heights.length; i++) {
            double hw = halfWidths[i];
            structure.addStation(new TowerStation("s" + i, heights[i], hw, hw));
            if (i > 0) {
                structure.addBay(bayWithBracing("s" + (i - 1), "s" + i));
            }
        }
    }

    private static void addArms(TowerStructureDesign structure, double[] heights, double reach, int verticalDrop) {
        for (int i = 0; i < heights.length; i++) {
            TowerArm arm = new TowerArm("arm_" + i, heights[i], reach);
            arm.setSide(TowerArmSide.BOTH);
            arm.setVerticalDrop(verticalDrop);
            arm.setMaterial(MaterialMix.single("minecraft:iron_bars"));
            structure.addArm(arm);
        }
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
