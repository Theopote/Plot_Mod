package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;

import java.util.ArrayList;
import java.util.List;

/** 内置塔体结构预设（Visual Language v2：剪影优先、独立宽深、分层横担）。 */
public final class TowerStructurePresets {
    public static final String STATION_S0 = "s0";
    public static final String STATION_S1 = "s1";
    public static final String STATION_S2 = "s2";
    public static final String STATION_S3 = "s3";

    private static final MaterialMix LATTICE_LEG = MaterialMix.single("minecraft:iron_block");
    private static final MaterialMix LATTICE_BRACE = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix SMALL_LATTICE_LEG = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix ARM_MATERIAL = MaterialMix.single("minecraft:iron_bars");

    /** 参考底宽（用于族预设缩放）。 */
    public static final double CLASSIC_BASE_HALF_WIDTH = 6.5;
    public static final double SMALL_BASE_HALF_WIDTH = 4.5;

    private TowerStructurePresets() {
    }

    // -------------------------------------------------------------------------
    // Legacy / compact presets
    // -------------------------------------------------------------------------

    /** 四腿收缩格构塔（紧凑，H=24）：保留兼容 taperedTower 风格。 */
    public static TowerStructureDesign taperedLatticeTower() {
        return smallLatticeTower();
    }

    /** 小型格构塔：66–132 kV 视觉，H≈24。 */
    public static TowerStructureDesign smallLatticeTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.TAPERED_LATTICE,
            SMALL_LATTICE_LEG,
            SMALL_LATTICE_LEG);
        // 底宽:深 ≈ 1 : 0.67，紧凑子输电剪影
        addStations(structure,
            new double[] {0, 8, 16, 24},
            new double[] {4.5, 3.8, 2.5, 1.5},
            new double[] {3.0, 2.5, 1.7, 1.0});
        addVariedBays(structure, 2,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.K, BracingPattern.V);
        addTrussArm(structure, "arm_main", 20, 8.0, TowerArmShape.TRUSS, 3, 1.3);
        addPeak(structure, 24);
        return structure;
    }

    /** 经典双层横担输电塔：插件标志性塔型，H≈36。 */
    public static TowerStructureDesign classicDoubleArmTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.DOUBLE_ARM,
            LATTICE_LEG,
            LATTICE_BRACE);
        // Base / legs (~50%), waist, head — width:depth ≈ 1:0.65
        addStations(structure,
            new double[] {0, 10, 18, 26, 32, 36},
            new double[] {6.5, 6.0, 5.0, 4.0, 2.8, 1.8},
            new double[] {4.2, 3.9, 3.3, 2.7, 1.9, 1.2});
        addVariedBays(structure, 3,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.X, BracingPattern.K,
            BracingPattern.V);
        // 下层横担更宽，上层略短 — 标准输电塔剪影
        addTrussArm(structure, "arm_lower", 26, 12.0, TowerArmShape.TAPERED, 4, 1.8);
        addTrussArm(structure, "arm_upper", 32, 10.0, TowerArmShape.TRUSS, 3, 1.5);
        addPeak(structure, 36);
        return structure;
    }

    /** 三层双回路塔：H≈50。 */
    public static TowerStructureDesign tripleArmTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.TRIPLE_ARM,
            LATTICE_LEG,
            LATTICE_BRACE);
        addStations(structure,
            new double[] {0, 12, 22, 32, 42, 50},
            new double[] {8.0, 7.2, 6.0, 4.5, 3.0, 2.0},
            new double[] {5.2, 4.7, 4.0, 3.0, 2.0, 1.3});
        addVariedBays(structure,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.X, BracingPattern.K,
            BracingPattern.V);
        addTrussArm(structure, "arm_lower", 36, 11.0, TowerArmShape.TAPERED, 4, 2.0);
        addTrussArm(structure, "arm_middle", 42, 14.5, TowerArmShape.TRUSS, 4, 2.2);
        addTrussArm(structure, "arm_upper", 48, 10.5, TowerArmShape.TRUSS, 3, 1.8);
        addPeak(structure, 50);
        return structure;
    }

    /** 重型双回路输电塔：H≈32，双层横担（非缩放小塔）。 */
    public static TowerStructureDesign heavyTransmissionTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.DOUBLE_ARM,
            LATTICE_LEG,
            LATTICE_BRACE);
        addStations(structure,
            new double[] {0, 9, 17, 24, 30, 32},
            new double[] {5.5, 5.0, 4.0, 3.0, 2.2, 1.6},
            new double[] {3.6, 3.3, 2.7, 2.0, 1.5, 1.1});
        addVariedBays(structure,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.K, BracingPattern.V,
            BracingPattern.V);
        addTrussArm(structure, "arm_lower", 24, 12.0, TowerArmShape.TAPERED, 4, 1.8);
        addTrussArm(structure, "arm_upper", 30, 10.0, TowerArmShape.TRUSS, 3, 1.6);
        addPeak(structure, 32);
        return structure;
    }

    /** 酒杯型宽顶塔：H≈40，鼓形来自横担与塔头 station，不靠塔身假鼓包。 */
    public static TowerStructureDesign cupTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.CUP,
            LATTICE_LEG,
            LATTICE_BRACE);
        addStations(structure,
            new double[] {0, 10, 18, 26, 32, 40},
            new double[] {5.0, 4.5, 3.2, 2.2, 7.5, 2.2},
            new double[] {3.3, 3.0, 2.2, 1.5, 5.0, 1.6});
        addVariedBays(structure, 2,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.K, BracingPattern.V,
            BracingPattern.NONE);
        addTrussArm(structure, "arm_cup", 32, 16.0, TowerArmShape.UPSWEEP, 3, 2.8);
        addPeak(structure, 40);
        return structure;
    }

    /** 工业门架塔：宽柱 + 多层横梁。 */
    public static TowerStructureDesign industrialPortalTower() {
        return portalTower();
    }

    /** 工业门架塔（别名）：宽柱 + 多层横梁，中部横担最宽。 */
    public static TowerStructureDesign portalTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.PORTAL,
            LATTICE_LEG,
            LATTICE_BRACE);
        // 门型：底部双柱保持宽度，顶部收窄
        addStations(structure,
            new double[] {0, 12, 24, 34, 42},
            new double[] {10.5, 10.5, 9.5, 6.5, 3.5},
            new double[] {4.5, 4.5, 4.0, 2.8, 1.6});
        addVariedBays(structure, 2,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.K, BracingPattern.V);
        addTrussArm(structure, "arm_lower", 16, 13.0, TowerArmShape.FLAT, 2, 2.0);
        addTrussArm(structure, "arm_middle", 26, 16.0, TowerArmShape.TRUSS, 3, 2.4);
        addTrussArm(structure, "arm_upper", 36, 13.0, TowerArmShape.TRUSS, 3, 2.0);
        return structure;
    }

    /** 超大型工业格构塔：H≈60，三层横担，明显腰收。 */
    public static TowerStructureDesign megaLatticeTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.GIANT,
            LATTICE_LEG,
            LATTICE_BRACE);
        addStations(structure,
            new double[] {0, 14, 26, 38, 50, 60},
            new double[] {9.0, 8.5, 7.0, 4.0, 2.8, 1.8},
            new double[] {6.0, 5.7, 4.7, 2.8, 1.9, 1.2});
        addVariedBays(structure, 3,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.X, BracingPattern.K,
            BracingPattern.V);
        addTrussArm(structure, "arm_lower", 38, 11.0, TowerArmShape.TAPERED, 4, 2.0);
        addTrussArm(structure, "arm_middle", 47, 15.0, TowerArmShape.TRUSS, 4, 2.5);
        addTrussArm(structure, "arm_upper", 56, 11.0, TowerArmShape.TRUSS, 3, 2.0);
        addPeak(structure, 60);
        return structure;
    }

    /** 重型双回路鼓形塔：独立剪影，H≈58。 */
    public static TowerStructureDesign doubleCircuitDrumTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.TRIPLE_ARM,
            LATTICE_LEG,
            LATTICE_BRACE);
        // 塔身轻收腰，鼓形剪影主要靠三层横担
        addStations(structure,
            new double[] {0, 12, 22, 32, 42, 52, 58},
            new double[] {8.5, 7.5, 5.5, 3.5, 3.2, 2.8, 2.0},
            new double[] {5.5, 4.9, 3.6, 2.3, 2.1, 1.8, 1.3});
        addVariedBays(structure, 3,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.K, BracingPattern.K,
            BracingPattern.V, BracingPattern.V);
        addTrussArm(structure, "arm_lower", 36, 11.0, TowerArmShape.TAPERED, 4, 2.0);
        addTrussArm(structure, "arm_middle", 44, 14.0, TowerArmShape.TRUSS, 4, 2.2);
        addTrussArm(structure, "arm_upper", 52, 11.0, TowerArmShape.TRUSS, 3, 2.0);
        addPeak(structure, 58);
        return structure;
    }

    /** 怪物级 / UHV 巨型输电塔：H≈80，水平表现力优先。 */
    public static TowerStructureDesign monsterPylonTower() {
        return uhvGiantTower();
    }

    /** UHV 巨型输电塔：地标级，三组超宽主横担（非四层脚手架）。 */
    public static TowerStructureDesign uhvGiantTower() {
        TowerStructureDesign structure = latticeShell(
            TowerSilhouette.GIANT,
            LATTICE_LEG,
            LATTICE_BRACE);
        addStations(structure,
            new double[] {0, 14, 28, 42, 56, 70, 80},
            new double[] {14.0, 13.0, 11.0, 8.5, 5.5, 3.2, 2.0},
            new double[] {9.0, 8.5, 7.2, 5.6, 3.6, 2.1, 1.3});
        addVariedBays(structure, 3,
            BracingPattern.X, BracingPattern.X,
            BracingPattern.X, BracingPattern.K,
            BracingPattern.V, BracingPattern.V);
        addTrussArm(structure, "arm_lower", 56, 20.0, TowerArmShape.TAPERED, 5, 2.8);
        addTrussArm(structure, "arm_main", 66, 26.0, TowerArmShape.TRUSS, 5, 3.2);
        addTrussArm(structure, "arm_upper", 74, 22.0, TowerArmShape.TRUSS, 4, 2.8);
        addPeak(structure, 80);
        return structure;
    }

    /** 带塔体 + 三相挂点的完整杆塔设计（兼容旧 API）。 */
    public static com.plot.plugin.powerline.design.PoleDesign taperedLatticePoleDesign(String id, String name) {
        com.plot.plugin.powerline.design.PoleDesign design =
            new com.plot.plugin.powerline.design.PoleDesign(id, name);
        design.setTowerStructure(smallLatticeTower());
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TowerStructureDesign latticeShell(
            TowerSilhouette silhouette,
            MaterialMix legMaterial,
            MaterialMix braceMaterial) {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.setSilhouette(silhouette);
        structure.setPrimaryMaterial(legMaterial);
        structure.setBraceMaterial(braceMaterial);
        return structure;
    }

    private static void addStations(
            TowerStructureDesign structure,
            double[] heights,
            double[] halfWidths,
            double[] halfDepths) {
        if (heights.length != halfWidths.length || heights.length != halfDepths.length) {
            throw new IllegalArgumentException("station arrays must have equal length");
        }
        for (int i = 0; i < heights.length; i++) {
            structure.addStation(new TowerStation("s" + i, heights[i], halfWidths[i], halfDepths[i]));
            if (i > 0) {
                structure.addBay(bayWithBracing("s" + (i - 1), "s" + i));
            }
        }
    }

    private static void addVariedBays(
            TowerStructureDesign structure,
            BracingPattern... legBracing) {
        addVariedBays(structure, Integer.MAX_VALUE, legBracing);
    }

    private static void addVariedBays(
            TowerStructureDesign structure,
            int maxPlanDiagonalBays,
            BracingPattern... legBracing) {
        List<TowerStation> stations = structure.sortedStations();
        structure.getBays().clear();
        for (int i = 1; i < stations.size(); i++) {
            BracingPattern pattern = i - 1 < legBracing.length
                ? legBracing[i - 1]
                : BracingPattern.X;
            TowerBay bay = new TowerBay(stations.get(i - 1).getId(), stations.get(i).getId());
            // 四边使用相同斜撑，保持前后/左右对称
            bay.setFrontBackBracing(pattern);
            bay.setSideBracing(pattern);
            boolean hasUpperRing = i < stations.size() - 1;
            bay.setHorizontalRing(hasUpperRing);
            // 水平面对角斜撑：仅在下部关键 bay 使用，避免结构噪声
            bay.setPlanDiagonalBracing(
                hasUpperRing
                    && pattern != BracingPattern.NONE
                    && i <= maxPlanDiagonalBays);
            structure.addBay(bay);
        }
    }

    private static void addTrussArm(
            TowerStructureDesign structure,
            String id,
            double height,
            double reach,
            TowerArmShape shape,
            int verticalDrop,
            double longitudinalHalfWidth) {
        TowerArm arm = new TowerArm(id, height, reach);
        arm.setSide(TowerArmSide.BOTH);
        arm.setShape(shape);
        arm.setVerticalDrop(verticalDrop);
        arm.setLongitudinalHalfWidth(longitudinalHalfWidth);
        if (shape == TowerArmShape.TRUSS || shape == TowerArmShape.TAPERED) {
            arm.setBracing(BracingPattern.X);
        }
        arm.setMaterial(ARM_MATERIAL);
        structure.addArm(arm);
    }

    private static void addPeak(TowerStructureDesign structure, double height) {
        TowerDecoration peak = new TowerDecoration("peak", TowerDecorationKind.ANTENNA, height);
        peak.setSize(2);
        peak.setMaterial(MaterialMix.single("minecraft:iron_bars"));
        structure.addDecoration(peak);
    }

    private static TowerBay bayWithBracing(String lowerId, String upperId) {
        TowerBay bay = new TowerBay(lowerId, upperId);
        bay.setFrontBackBracing(BracingPattern.X);
        bay.setSideBracing(BracingPattern.X);
        bay.setHorizontalRing(true);
        bay.setPlanDiagonalBracing(true);
        return bay;
    }
}
