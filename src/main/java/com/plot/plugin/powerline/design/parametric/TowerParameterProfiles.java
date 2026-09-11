package com.plot.plugin.powerline.design.parametric;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArmShape;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;

import java.util.List;
import java.util.Map;

/** v1 内置参数 Profile 目录（当前仅 Classic Double Arm）。 */
public final class TowerParameterProfiles {
    public static final String CLASSIC_DOUBLE_ARM_ID = "profile/classic_double_arm";

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#classicDoubleArmTower()} */
    static final double REF_HEIGHT = 36.0;
    static final double REF_BASE_WIDTH = 13.0;
    static final double REF_BASE_HALF_WIDTH = 6.5;
    static final double REF_BASE_HALF_DEPTH = 4.2;
    static final double REF_ARM_SPAN = 24.0;
    static final double REF_DOMINANT_REACH = 12.0;
    static final double DEFAULT_DEPTH_RATIO = (REF_BASE_HALF_DEPTH * 2.0) / REF_BASE_WIDTH;

    private static final MaterialMix LATTICE_LEG = MaterialMix.single("minecraft:iron_block");
    private static final MaterialMix LATTICE_BRACE = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix ARM_MATERIAL = MaterialMix.single("minecraft:iron_bars");

    private TowerParameterProfiles() {
    }

    public static TowerParameterProfile classicDoubleArm() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 10.0 / REF_HEIGHT, 6.0 / REF_BASE_HALF_WIDTH, 3.9 / REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 18.0 / REF_HEIGHT, 5.0 / REF_BASE_HALF_WIDTH, 3.3 / REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 26.0 / REF_HEIGHT, 4.0 / REF_BASE_HALF_WIDTH, 2.7 / REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 32.0 / REF_HEIGHT, 2.8 / REF_BASE_HALF_WIDTH, 1.9 / REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.TOP, 1.0, 1.8 / REF_BASE_HALF_WIDTH, 1.2 / REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                26.0 / REF_HEIGHT,
                1.0,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                4.0 / REF_HEIGHT,
                1.8 / REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                32.0 / REF_HEIGHT,
                10.0 / REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / REF_HEIGHT,
                1.5 / REF_BASE_HALF_DEPTH));

        List<BayDensityConfig> lowBays = List.of(
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));

        List<BayDensityConfig> mediumBays = List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));

        List<BayDensityConfig> highBays = List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, true, true));

        return new TowerParameterProfile(
            CLASSIC_DOUBLE_ARM_ID,
            TowerSilhouette.DOUBLE_ARM,
            new ParameterRange(28.0, 36.0, 52.0),
            new ParameterRange(9.0, 13.0, 16.0),
            new ParameterRange(18.0, 24.0, 30.0),
            new ParameterRange(0.75, 1.0, 1.25),
            DEFAULT_DEPTH_RATIO,
            REF_HEIGHT,
            REF_BASE_WIDTH,
            REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, lowBays,
                StructureDensity.MEDIUM, mediumBays,
                StructureDensity.HIGH, highBays),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL);
    }
}
