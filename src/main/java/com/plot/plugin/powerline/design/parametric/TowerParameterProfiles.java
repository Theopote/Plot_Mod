package com.plot.plugin.powerline.design.parametric;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArmShape;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** v1 内置参数 Profile 目录。 */
public final class TowerParameterProfiles {
    public static final String CLASSIC_DOUBLE_ARM_ID = "profile/classic_double_arm";
    public static final String SMALL_LATTICE_ID = "profile/small_lattice";
    public static final String TRIPLE_ARM_ID = "profile/triple_arm";
    public static final String CUP_ID = "profile/cup";
    public static final String HEAVY_ID = "profile/heavy";
    public static final String MEGA_ID = "profile/mega";
    public static final String PORTAL_ID = "profile/portal";
    public static final String DRUM_ID = "profile/drum";
    public static final String UHV_ID = "profile/uhv";
    public static final String STEAMPUNK_ID = "profile/steampunk";
    public static final String MODERN_HV_GLASS_ID = "profile/modern_hv_glass";

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#classicDoubleArmTower()} */
    static final double CLASSIC_REF_HEIGHT = 36.0;
    static final double CLASSIC_REF_BASE_WIDTH = 13.0;
    static final double CLASSIC_REF_BASE_HALF_WIDTH = 6.5;
    static final double CLASSIC_REF_BASE_HALF_DEPTH = 4.2;
    static final double CLASSIC_REF_ARM_SPAN = 24.0;
    static final double CLASSIC_REF_DOMINANT_REACH = 12.0;
    static final double CLASSIC_DEPTH_RATIO = (CLASSIC_REF_BASE_HALF_DEPTH * 2.0) / CLASSIC_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#smallLatticeTower()} */
    static final double SMALL_REF_HEIGHT = 24.0;
    static final double SMALL_REF_BASE_WIDTH = 9.0;
    static final double SMALL_REF_BASE_HALF_WIDTH = 4.5;
    static final double SMALL_REF_BASE_HALF_DEPTH = 3.0;
    static final double SMALL_REF_ARM_SPAN = 16.0;
    static final double SMALL_REF_DOMINANT_REACH = 8.0;
    static final double SMALL_DEPTH_RATIO = (SMALL_REF_BASE_HALF_DEPTH * 2.0) / SMALL_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#tripleArmTower()} */
    static final double TRIPLE_REF_HEIGHT = 50.0;
    static final double TRIPLE_REF_BASE_WIDTH = 16.0;
    static final double TRIPLE_REF_BASE_HALF_WIDTH = 8.0;
    static final double TRIPLE_REF_BASE_HALF_DEPTH = 5.2;
    static final double TRIPLE_REF_ARM_SPAN = 29.0;
    static final double TRIPLE_REF_DOMINANT_REACH = 14.5;
    static final double TRIPLE_DEPTH_RATIO = (TRIPLE_REF_BASE_HALF_DEPTH * 2.0) / TRIPLE_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#cupTower()} */
    static final double CUP_REF_HEIGHT = 40.0;
    static final double CUP_REF_BASE_WIDTH = 10.0;
    static final double CUP_REF_BASE_HALF_WIDTH = 5.0;
    static final double CUP_REF_BASE_HALF_DEPTH = 3.3;
    static final double CUP_REF_ARM_SPAN = 32.0;
    static final double CUP_REF_DOMINANT_REACH = 16.0;
    static final double CUP_DEPTH_RATIO = (CUP_REF_BASE_HALF_DEPTH * 2.0) / CUP_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#heavyTransmissionTower()} */
    static final double HEAVY_REF_HEIGHT = 32.0;
    static final double HEAVY_REF_BASE_WIDTH = 11.0;
    static final double HEAVY_REF_BASE_HALF_WIDTH = 5.5;
    static final double HEAVY_REF_BASE_HALF_DEPTH = 3.6;
    static final double HEAVY_REF_ARM_SPAN = 24.0;
    static final double HEAVY_REF_DOMINANT_REACH = 12.0;
    static final double HEAVY_DEPTH_RATIO = (HEAVY_REF_BASE_HALF_DEPTH * 2.0) / HEAVY_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#megaLatticeTower()} */
    static final double MEGA_REF_HEIGHT = 60.0;
    static final double MEGA_REF_BASE_WIDTH = 18.0;
    static final double MEGA_REF_BASE_HALF_WIDTH = 9.0;
    static final double MEGA_REF_BASE_HALF_DEPTH = 6.0;
    static final double MEGA_REF_ARM_SPAN = 30.0;
    static final double MEGA_REF_DOMINANT_REACH = 15.0;
    static final double MEGA_DEPTH_RATIO = (MEGA_REF_BASE_HALF_DEPTH * 2.0) / MEGA_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#portalTower()} */
    static final double PORTAL_REF_HEIGHT = 42.0;
    static final double PORTAL_REF_BASE_WIDTH = 21.0;
    static final double PORTAL_REF_BASE_HALF_WIDTH = 10.5;
    static final double PORTAL_REF_BASE_HALF_DEPTH = 4.5;
    static final double PORTAL_REF_ARM_SPAN = 32.0;
    static final double PORTAL_REF_DOMINANT_REACH = 16.0;
    static final double PORTAL_DEPTH_RATIO = (PORTAL_REF_BASE_HALF_DEPTH * 2.0) / PORTAL_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#doubleCircuitDrumTower()} */
    static final double DRUM_REF_HEIGHT = 58.0;
    static final double DRUM_REF_BASE_WIDTH = 17.0;
    static final double DRUM_REF_BASE_HALF_WIDTH = 8.5;
    static final double DRUM_REF_BASE_HALF_DEPTH = 5.5;
    static final double DRUM_REF_ARM_SPAN = 28.0;
    static final double DRUM_REF_DOMINANT_REACH = 14.0;
    static final double DRUM_DEPTH_RATIO = (DRUM_REF_BASE_HALF_DEPTH * 2.0) / DRUM_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#uhvGiantTower()} */
    static final double UHV_REF_HEIGHT = 80.0;
    static final double UHV_REF_BASE_WIDTH = 28.0;
    static final double UHV_REF_BASE_HALF_WIDTH = 14.0;
    static final double UHV_REF_BASE_HALF_DEPTH = 9.0;
    static final double UHV_REF_ARM_SPAN = 52.0;
    static final double UHV_REF_DOMINANT_REACH = 26.0;
    static final double UHV_DEPTH_RATIO = (UHV_REF_BASE_HALF_DEPTH * 2.0) / UHV_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.PoleDesignCatalog#steampunkBrassTower()} */
    static final double STEAMPUNK_REF_HEIGHT = 28.0;
    static final double STEAMPUNK_REF_BASE_WIDTH = 8.0;
    static final double STEAMPUNK_REF_BASE_HALF_WIDTH = 4.0;
    static final double STEAMPUNK_REF_BASE_HALF_DEPTH = 2.6;
    static final double STEAMPUNK_REF_ARM_SPAN = 14.0;
    static final double STEAMPUNK_REF_DOMINANT_REACH = 7.0;
    static final double STEAMPUNK_DEPTH_RATIO = (STEAMPUNK_REF_BASE_HALF_DEPTH * 2.0) / STEAMPUNK_REF_BASE_WIDTH;

    /** 参考几何：{@link com.plot.plugin.powerline.design.PoleDesignCatalog#modernHvGlassTower()} */
    static final double MODERN_HV_REF_HEIGHT = 34.0;
    static final double MODERN_HV_REF_BASE_WIDTH = 10.0;
    static final double MODERN_HV_REF_BASE_HALF_WIDTH = 5.0;
    static final double MODERN_HV_REF_BASE_HALF_DEPTH = 3.2;
    static final double MODERN_HV_REF_ARM_SPAN = 18.0;
    static final double MODERN_HV_REF_DOMINANT_REACH = 9.0;
    static final double MODERN_HV_DEPTH_RATIO = (MODERN_HV_REF_BASE_HALF_DEPTH * 2.0) / MODERN_HV_REF_BASE_WIDTH;

    private static final MaterialMix LATTICE_LEG = MaterialMix.single("minecraft:iron_block");
    private static final MaterialMix LATTICE_BRACE = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix SMALL_LATTICE_MEMBER = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix ARM_MATERIAL = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix STEAMPUNK_LEG = MaterialMix.single("minecraft:copper_block");
    private static final MaterialMix STEAMPUNK_BRACE = MaterialMix.single("minecraft:cut_copper");
    private static final MaterialMix STEAMPUNK_ARM = MaterialMix.single("minecraft:gold_block");
    private static final MaterialMix MODERN_HV_LEG = MaterialMix.single("minecraft:iron_block");
    private static final MaterialMix MODERN_HV_BRACE = MaterialMix.single("minecraft:iron_bars");
    private static final MaterialMix MODERN_HV_ARM = MaterialMix.single("minecraft:sea_lantern");

    private TowerParameterProfiles() {
    }

    public static Optional<TowerParameterProfile> find(String profileId) {
        if (CLASSIC_DOUBLE_ARM_ID.equals(profileId)) {
            return Optional.of(classicDoubleArm());
        }
        if (SMALL_LATTICE_ID.equals(profileId)) {
            return Optional.of(smallLattice());
        }
        if (TRIPLE_ARM_ID.equals(profileId)) {
            return Optional.of(tripleArm());
        }
        if (CUP_ID.equals(profileId)) {
            return Optional.of(cup());
        }
        if (HEAVY_ID.equals(profileId)) {
            return Optional.of(heavy());
        }
        if (MEGA_ID.equals(profileId)) {
            return Optional.of(mega());
        }
        if (PORTAL_ID.equals(profileId)) {
            return Optional.of(portal());
        }
        if (DRUM_ID.equals(profileId)) {
            return Optional.of(drum());
        }
        if (UHV_ID.equals(profileId)) {
            return Optional.of(uhv());
        }
        if (STEAMPUNK_ID.equals(profileId)) {
            return Optional.of(steampunk());
        }
        if (MODERN_HV_GLASS_ID.equals(profileId)) {
            return Optional.of(modernHvGlass());
        }
        return Optional.empty();
    }

    public static TowerParameterProfile classicDoubleArm() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / CLASSIC_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 10.0 / CLASSIC_REF_HEIGHT, 6.0 / CLASSIC_REF_BASE_HALF_WIDTH, 3.9 / CLASSIC_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 18.0 / CLASSIC_REF_HEIGHT, 5.0 / CLASSIC_REF_BASE_HALF_WIDTH, 3.3 / CLASSIC_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 26.0 / CLASSIC_REF_HEIGHT, 4.0 / CLASSIC_REF_BASE_HALF_WIDTH, 2.7 / CLASSIC_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 32.0 / CLASSIC_REF_HEIGHT, 2.8 / CLASSIC_REF_BASE_HALF_WIDTH, 1.9 / CLASSIC_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.TOP, 1.0, 1.8 / CLASSIC_REF_BASE_HALF_WIDTH, 1.2 / CLASSIC_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                26.0 / CLASSIC_REF_HEIGHT,
                1.0,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                4.0 / CLASSIC_REF_HEIGHT,
                1.8 / CLASSIC_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                32.0 / CLASSIC_REF_HEIGHT,
                10.0 / CLASSIC_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / CLASSIC_REF_HEIGHT,
                1.5 / CLASSIC_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            CLASSIC_DOUBLE_ARM_ID,
            TowerSilhouette.DOUBLE_ARM,
            new ParameterRange(28.0, 36.0, 52.0),
            new ParameterRange(9.0, 13.0, 16.0),
            new ParameterRange(18.0, 24.0, 30.0),
            new ParameterRange(0.75, 1.0, 1.25),
            CLASSIC_DEPTH_RATIO,
            CLASSIC_REF_HEIGHT,
            CLASSIC_REF_BASE_WIDTH,
            CLASSIC_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, classicLowBays(),
                StructureDensity.MEDIUM, classicMediumBays(),
                StructureDensity.HIGH, classicHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.classicLattice());
    }

    public static TowerParameterProfile smallLattice() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / SMALL_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 8.0 / SMALL_REF_HEIGHT, 3.8 / SMALL_REF_BASE_HALF_WIDTH, 2.5 / SMALL_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 16.0 / SMALL_REF_HEIGHT, 2.5 / SMALL_REF_BASE_HALF_WIDTH, 1.7 / SMALL_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.TOP, 1.0, 1.5 / SMALL_REF_BASE_HALF_WIDTH, 1.0 / SMALL_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_main",
                TowerArmRole.MAIN,
                20.0 / SMALL_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / SMALL_REF_HEIGHT,
                1.3 / SMALL_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            SMALL_LATTICE_ID,
            TowerSilhouette.TAPERED_LATTICE,
            new ParameterRange(18.0, 24.0, 36.0),
            new ParameterRange(6.0, 9.0, 12.0),
            new ParameterRange(12.0, 16.0, 20.0),
            new ParameterRange(0.75, 1.0, 1.25),
            SMALL_DEPTH_RATIO,
            SMALL_REF_HEIGHT,
            SMALL_REF_BASE_WIDTH,
            SMALL_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, smallLowBays(),
                StructureDensity.MEDIUM, smallMediumBays(),
                StructureDensity.HIGH, smallHighBays()),
            SMALL_LATTICE_MEMBER,
            SMALL_LATTICE_MEMBER,
            ARM_MATERIAL,
            TowerAttachmentTopology.classicLattice());
    }

    public static TowerParameterProfile tripleArm() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / TRIPLE_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 12.0 / TRIPLE_REF_HEIGHT, 7.2 / TRIPLE_REF_BASE_HALF_WIDTH, 4.7 / TRIPLE_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 22.0 / TRIPLE_REF_HEIGHT, 6.0 / TRIPLE_REF_BASE_HALF_WIDTH, 4.0 / TRIPLE_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 32.0 / TRIPLE_REF_HEIGHT, 4.5 / TRIPLE_REF_BASE_HALF_WIDTH, 3.0 / TRIPLE_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 42.0 / TRIPLE_REF_HEIGHT, 3.0 / TRIPLE_REF_BASE_HALF_WIDTH, 2.0 / TRIPLE_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.TOP, 1.0, 2.0 / TRIPLE_REF_BASE_HALF_WIDTH, 1.3 / TRIPLE_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                36.0 / TRIPLE_REF_HEIGHT,
                11.0 / TRIPLE_REF_DOMINANT_REACH,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                4.0 / TRIPLE_REF_HEIGHT,
                2.0 / TRIPLE_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_middle",
                TowerArmRole.MIDDLE,
                42.0 / TRIPLE_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                4.0 / TRIPLE_REF_HEIGHT,
                2.2 / TRIPLE_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                48.0 / TRIPLE_REF_HEIGHT,
                10.5 / TRIPLE_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / TRIPLE_REF_HEIGHT,
                1.8 / TRIPLE_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            TRIPLE_ARM_ID,
            TowerSilhouette.TRIPLE_ARM,
            new ParameterRange(40.0, 50.0, 70.0),
            new ParameterRange(12.0, 16.0, 20.0),
            new ParameterRange(22.0, 29.0, 38.0),
            new ParameterRange(0.75, 1.0, 1.25),
            TRIPLE_DEPTH_RATIO,
            TRIPLE_REF_HEIGHT,
            TRIPLE_REF_BASE_WIDTH,
            TRIPLE_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, tripleLowBays(),
                StructureDensity.MEDIUM, tripleMediumBays(),
                StructureDensity.HIGH, tripleHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.doubleCircuitThreeDeck());
    }

    public static TowerParameterProfile cup() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / CUP_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 10.0 / CUP_REF_HEIGHT, 4.5 / CUP_REF_BASE_HALF_WIDTH, 3.0 / CUP_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 18.0 / CUP_REF_HEIGHT, 3.2 / CUP_REF_BASE_HALF_WIDTH, 2.2 / CUP_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 26.0 / CUP_REF_HEIGHT, 2.2 / CUP_REF_BASE_HALF_WIDTH, 1.5 / CUP_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.HEAD, 32.0 / CUP_REF_HEIGHT, 7.5 / CUP_REF_BASE_HALF_WIDTH, 5.0 / CUP_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.TOP, 1.0, 2.2 / CUP_REF_BASE_HALF_WIDTH, 1.6 / CUP_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_cup",
                TowerArmRole.CUP,
                32.0 / CUP_REF_HEIGHT,
                1.0,
                TowerArmShape.UPSWEEP,
                BracingPattern.X,
                3.0 / CUP_REF_HEIGHT,
                2.8 / CUP_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            CUP_ID,
            TowerSilhouette.CUP,
            new ParameterRange(32.0, 40.0, 52.0),
            new ParameterRange(8.0, 10.0, 13.0),
            new ParameterRange(24.0, 32.0, 40.0),
            new ParameterRange(0.75, 1.0, 1.25),
            CUP_DEPTH_RATIO,
            CUP_REF_HEIGHT,
            CUP_REF_BASE_WIDTH,
            CUP_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, cupLowBays(),
                StructureDensity.MEDIUM, cupMediumBays(),
                StructureDensity.HIGH, cupHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.heavyTransmissionCup());
    }

    public static TowerParameterProfile heavy() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / HEAVY_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 9.0 / HEAVY_REF_HEIGHT, 5.0 / HEAVY_REF_BASE_HALF_WIDTH, 3.3 / HEAVY_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 17.0 / HEAVY_REF_HEIGHT, 4.0 / HEAVY_REF_BASE_HALF_WIDTH, 2.7 / HEAVY_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 24.0 / HEAVY_REF_HEIGHT, 3.0 / HEAVY_REF_BASE_HALF_WIDTH, 2.0 / HEAVY_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 30.0 / HEAVY_REF_HEIGHT, 2.2 / HEAVY_REF_BASE_HALF_WIDTH, 1.5 / HEAVY_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.TOP, 1.0, 1.6 / HEAVY_REF_BASE_HALF_WIDTH, 1.1 / HEAVY_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                24.0 / HEAVY_REF_HEIGHT,
                1.0,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                4.0 / HEAVY_REF_HEIGHT,
                1.8 / HEAVY_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                30.0 / HEAVY_REF_HEIGHT,
                10.0 / HEAVY_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / HEAVY_REF_HEIGHT,
                1.6 / HEAVY_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            HEAVY_ID,
            TowerSilhouette.DOUBLE_ARM,
            new ParameterRange(24.0, 32.0, 44.0),
            new ParameterRange(8.0, 11.0, 14.0),
            new ParameterRange(18.0, 24.0, 28.0),
            new ParameterRange(0.75, 1.0, 1.25),
            HEAVY_DEPTH_RATIO,
            HEAVY_REF_HEIGHT,
            HEAVY_REF_BASE_WIDTH,
            HEAVY_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, heavyLowBays(),
                StructureDensity.MEDIUM, heavyMediumBays(),
                StructureDensity.HIGH, heavyHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.heavyTransmissionMega());
    }

    public static TowerParameterProfile mega() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / MEGA_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 14.0 / MEGA_REF_HEIGHT, 8.5 / MEGA_REF_BASE_HALF_WIDTH, 5.7 / MEGA_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 26.0 / MEGA_REF_HEIGHT, 7.0 / MEGA_REF_BASE_HALF_WIDTH, 4.7 / MEGA_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 38.0 / MEGA_REF_HEIGHT, 4.0 / MEGA_REF_BASE_HALF_WIDTH, 2.8 / MEGA_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 50.0 / MEGA_REF_HEIGHT, 2.8 / MEGA_REF_BASE_HALF_WIDTH, 1.9 / MEGA_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.TOP, 1.0, 1.8 / MEGA_REF_BASE_HALF_WIDTH, 1.2 / MEGA_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                38.0 / MEGA_REF_HEIGHT,
                11.0 / MEGA_REF_DOMINANT_REACH,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                4.0 / MEGA_REF_HEIGHT,
                2.0 / MEGA_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_middle",
                TowerArmRole.MIDDLE,
                47.0 / MEGA_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                4.0 / MEGA_REF_HEIGHT,
                2.5 / MEGA_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                56.0 / MEGA_REF_HEIGHT,
                11.0 / MEGA_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / MEGA_REF_HEIGHT,
                2.0 / MEGA_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            MEGA_ID,
            TowerSilhouette.GIANT,
            new ParameterRange(48.0, 60.0, 80.0),
            new ParameterRange(14.0, 18.0, 22.0),
            new ParameterRange(24.0, 30.0, 38.0),
            new ParameterRange(0.75, 1.0, 1.25),
            MEGA_DEPTH_RATIO,
            MEGA_REF_HEIGHT,
            MEGA_REF_BASE_WIDTH,
            MEGA_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, megaLowBays(),
                StructureDensity.MEDIUM, megaMediumBays(),
                StructureDensity.HIGH, megaHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.megaThreeDeck());
    }

    public static TowerParameterProfile portal() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / PORTAL_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 12.0 / PORTAL_REF_HEIGHT, 10.5 / PORTAL_REF_BASE_HALF_WIDTH, 4.5 / PORTAL_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 24.0 / PORTAL_REF_HEIGHT, 9.5 / PORTAL_REF_BASE_HALF_WIDTH, 4.0 / PORTAL_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 34.0 / PORTAL_REF_HEIGHT, 6.5 / PORTAL_REF_BASE_HALF_WIDTH, 2.8 / PORTAL_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.TOP, 1.0, 3.5 / PORTAL_REF_BASE_HALF_WIDTH, 1.6 / PORTAL_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                16.0 / PORTAL_REF_HEIGHT,
                13.0 / PORTAL_REF_DOMINANT_REACH,
                TowerArmShape.FLAT,
                BracingPattern.X,
                2.0 / PORTAL_REF_HEIGHT,
                2.0 / PORTAL_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_middle",
                TowerArmRole.MIDDLE,
                26.0 / PORTAL_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / PORTAL_REF_HEIGHT,
                2.4 / PORTAL_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                36.0 / PORTAL_REF_HEIGHT,
                13.0 / PORTAL_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / PORTAL_REF_HEIGHT,
                2.0 / PORTAL_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            PORTAL_ID,
            TowerSilhouette.PORTAL,
            new ParameterRange(34.0, 42.0, 56.0),
            new ParameterRange(16.0, 21.0, 26.0),
            new ParameterRange(24.0, 32.0, 40.0),
            new ParameterRange(0.75, 1.0, 1.25),
            PORTAL_DEPTH_RATIO,
            PORTAL_REF_HEIGHT,
            PORTAL_REF_BASE_WIDTH,
            PORTAL_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, portalLowBays(),
                StructureDensity.MEDIUM, portalMediumBays(),
                StructureDensity.HIGH, portalHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.heavyDoubleCircuit());
    }

    public static TowerParameterProfile drum() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / DRUM_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 12.0 / DRUM_REF_HEIGHT, 7.5 / DRUM_REF_BASE_HALF_WIDTH, 4.9 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 22.0 / DRUM_REF_HEIGHT, 5.5 / DRUM_REF_BASE_HALF_WIDTH, 3.6 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 32.0 / DRUM_REF_HEIGHT, 3.5 / DRUM_REF_BASE_HALF_WIDTH, 2.3 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 42.0 / DRUM_REF_HEIGHT, 3.2 / DRUM_REF_BASE_HALF_WIDTH, 2.1 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.HEAD, 52.0 / DRUM_REF_HEIGHT, 2.8 / DRUM_REF_BASE_HALF_WIDTH, 1.8 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s6", TowerStationRole.TOP, 1.0, 2.0 / DRUM_REF_BASE_HALF_WIDTH, 1.3 / DRUM_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                36.0 / DRUM_REF_HEIGHT,
                11.0 / DRUM_REF_DOMINANT_REACH,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                4.0 / DRUM_REF_HEIGHT,
                2.0 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_middle",
                TowerArmRole.MIDDLE,
                44.0 / DRUM_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                4.0 / DRUM_REF_HEIGHT,
                2.2 / DRUM_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                52.0 / DRUM_REF_HEIGHT,
                11.0 / DRUM_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / DRUM_REF_HEIGHT,
                2.0 / DRUM_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            DRUM_ID,
            TowerSilhouette.TRIPLE_ARM,
            new ParameterRange(46.0, 58.0, 72.0),
            new ParameterRange(13.0, 17.0, 21.0),
            new ParameterRange(22.0, 28.0, 36.0),
            new ParameterRange(0.75, 1.0, 1.25),
            DRUM_DEPTH_RATIO,
            DRUM_REF_HEIGHT,
            DRUM_REF_BASE_WIDTH,
            DRUM_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, drumLowBays(),
                StructureDensity.MEDIUM, drumMediumBays(),
                StructureDensity.HIGH, drumHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.doubleCircuitDrum());
    }

    public static TowerParameterProfile uhv() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / UHV_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 14.0 / UHV_REF_HEIGHT, 13.0 / UHV_REF_BASE_HALF_WIDTH, 8.5 / UHV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 28.0 / UHV_REF_HEIGHT, 11.0 / UHV_REF_BASE_HALF_WIDTH, 7.2 / UHV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.WAIST, 42.0 / UHV_REF_HEIGHT, 8.5 / UHV_REF_BASE_HALF_WIDTH, 5.6 / UHV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.SHOULDER, 56.0 / UHV_REF_HEIGHT, 5.5 / UHV_REF_BASE_HALF_WIDTH, 3.6 / UHV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s5", TowerStationRole.HEAD, 70.0 / UHV_REF_HEIGHT, 3.2 / UHV_REF_BASE_HALF_WIDTH, 2.1 / UHV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s6", TowerStationRole.TOP, 1.0, 2.0 / UHV_REF_BASE_HALF_WIDTH, 1.3 / UHV_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_lower",
                TowerArmRole.LOWER,
                56.0 / UHV_REF_HEIGHT,
                20.0 / UHV_REF_DOMINANT_REACH,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                5.0 / UHV_REF_HEIGHT,
                2.8 / UHV_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_main",
                TowerArmRole.MAIN,
                66.0 / UHV_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                5.0 / UHV_REF_HEIGHT,
                3.2 / UHV_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_upper",
                TowerArmRole.UPPER,
                74.0 / UHV_REF_HEIGHT,
                22.0 / UHV_REF_DOMINANT_REACH,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                4.0 / UHV_REF_HEIGHT,
                2.8 / UHV_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            UHV_ID,
            TowerSilhouette.GIANT,
            new ParameterRange(64.0, 80.0, 100.0),
            new ParameterRange(22.0, 28.0, 34.0),
            new ParameterRange(42.0, 52.0, 64.0),
            new ParameterRange(0.75, 1.0, 1.25),
            UHV_DEPTH_RATIO,
            UHV_REF_HEIGHT,
            UHV_REF_BASE_WIDTH,
            UHV_REF_ARM_SPAN,
            4.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, uhvLowBays(),
                StructureDensity.MEDIUM, uhvMediumBays(),
                StructureDensity.HIGH, uhvHighBays()),
            LATTICE_LEG,
            LATTICE_BRACE,
            ARM_MATERIAL,
            TowerAttachmentTopology.uhvThreeDeck());
    }

    public static TowerParameterProfile steampunk() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / STEAMPUNK_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 7.0 / STEAMPUNK_REF_HEIGHT, 3.5 / STEAMPUNK_REF_BASE_HALF_WIDTH, 2.3 / STEAMPUNK_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 14.0 / STEAMPUNK_REF_HEIGHT, 3.0 / STEAMPUNK_REF_BASE_HALF_WIDTH, 2.0 / STEAMPUNK_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.SHOULDER, 22.0 / STEAMPUNK_REF_HEIGHT, 2.2 / STEAMPUNK_REF_BASE_HALF_WIDTH, 1.5 / STEAMPUNK_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s4", TowerStationRole.TOP, 1.0, 1.5 / STEAMPUNK_REF_BASE_HALF_WIDTH, 1.0 / STEAMPUNK_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_brass",
                TowerArmRole.LOWER,
                14.0 / STEAMPUNK_REF_HEIGHT,
                6.0 / STEAMPUNK_REF_DOMINANT_REACH,
                TowerArmShape.TAPERED,
                BracingPattern.X,
                3.0 / STEAMPUNK_REF_HEIGHT,
                1.4 / STEAMPUNK_REF_BASE_HALF_DEPTH),
            new TowerArmTemplate(
                "arm_rod",
                TowerArmRole.UPPER,
                24.0 / STEAMPUNK_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.SINGLE_DIAGONAL,
                2.0 / STEAMPUNK_REF_HEIGHT,
                1.2 / STEAMPUNK_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            STEAMPUNK_ID,
            TowerSilhouette.DOUBLE_ARM,
            new ParameterRange(22.0, 28.0, 36.0),
            new ParameterRange(6.0, 8.0, 10.0),
            new ParameterRange(10.0, 14.0, 18.0),
            new ParameterRange(0.75, 1.0, 1.25),
            STEAMPUNK_DEPTH_RATIO,
            STEAMPUNK_REF_HEIGHT,
            STEAMPUNK_REF_BASE_WIDTH,
            STEAMPUNK_REF_ARM_SPAN,
            3.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, heavyLowBays(),
                StructureDensity.MEDIUM, heavyMediumBays(),
                StructureDensity.HIGH, heavyHighBays()),
            STEAMPUNK_LEG,
            STEAMPUNK_BRACE,
            STEAMPUNK_ARM,
            TowerAttachmentTopology.classicLattice());
    }

    public static TowerParameterProfile modernHvGlass() {
        List<TowerStationTemplate> stations = List.of(
            new TowerStationTemplate("s0", TowerStationRole.BASE, 0.0 / MODERN_HV_REF_HEIGHT, 1.0, 1.0),
            new TowerStationTemplate("s1", TowerStationRole.LOWER_BODY, 12.0 / MODERN_HV_REF_HEIGHT, 4.5 / MODERN_HV_REF_BASE_HALF_WIDTH, 2.9 / MODERN_HV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s2", TowerStationRole.MID_BODY, 22.0 / MODERN_HV_REF_HEIGHT, 3.5 / MODERN_HV_REF_BASE_HALF_WIDTH, 2.2 / MODERN_HV_REF_BASE_HALF_DEPTH),
            new TowerStationTemplate("s3", TowerStationRole.TOP, 1.0, 2.5 / MODERN_HV_REF_BASE_HALF_WIDTH, 1.6 / MODERN_HV_REF_BASE_HALF_DEPTH));

        List<TowerArmTemplate> arms = List.of(
            new TowerArmTemplate(
                "arm_glass",
                TowerArmRole.MAIN,
                30.0 / MODERN_HV_REF_HEIGHT,
                1.0,
                TowerArmShape.TRUSS,
                BracingPattern.X,
                3.0 / MODERN_HV_REF_HEIGHT,
                1.8 / MODERN_HV_REF_BASE_HALF_DEPTH));

        return new TowerParameterProfile(
            MODERN_HV_GLASS_ID,
            TowerSilhouette.TAPERED_LATTICE,
            new ParameterRange(26.0, 34.0, 42.0),
            new ParameterRange(8.0, 10.0, 12.0),
            new ParameterRange(14.0, 18.0, 22.0),
            new ParameterRange(0.75, 1.0, 1.25),
            MODERN_HV_DEPTH_RATIO,
            MODERN_HV_REF_HEIGHT,
            MODERN_HV_REF_BASE_WIDTH,
            MODERN_HV_REF_ARM_SPAN,
            3.0,
            stations,
            arms,
            Map.of(
                StructureDensity.LOW, smallLowBays(),
                StructureDensity.MEDIUM, smallMediumBays(),
                StructureDensity.HIGH, smallHighBays()),
            MODERN_HV_LEG,
            MODERN_HV_BRACE,
            MODERN_HV_ARM,
            TowerAttachmentTopology.threeHorizontal());
    }

    private static List<BayDensityConfig> classicLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> classicMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> classicHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, true, true));
    }

    private static List<BayDensityConfig> smallLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, false, false));
    }

    private static List<BayDensityConfig> smallMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, false, false));
    }

    private static List<BayDensityConfig> smallHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> tripleLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, true),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> tripleMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> tripleHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, true, true));
    }

    private static List<BayDensityConfig> cupLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.NONE, false, false));
    }

    private static List<BayDensityConfig> cupMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.NONE, false, false));
    }

    private static List<BayDensityConfig> cupHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.NONE, false, false));
    }

    private static List<BayDensityConfig> heavyLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, true),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> heavyMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> heavyHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> megaLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, true),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> megaMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> megaHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> portalLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> portalMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> portalHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> drumLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, true),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> drumMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> drumHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.V, true, true),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> uhvLowBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.SINGLE_DIAGONAL, true, true),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, true, false),
            new BayDensityConfig(BracingPattern.X, false, false));
    }

    private static List<BayDensityConfig> uhvMediumBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.K, true, false),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }

    private static List<BayDensityConfig> uhvHighBays() {
        return List.of(
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.K, true, true),
            new BayDensityConfig(BracingPattern.X, true, true),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, true, false),
            new BayDensityConfig(BracingPattern.V, false, false));
    }
}
