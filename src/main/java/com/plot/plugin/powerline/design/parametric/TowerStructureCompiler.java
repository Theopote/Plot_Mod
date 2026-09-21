package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerMemberProfile;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.core.material.MaterialMix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 将已解析的连续参数编译为现有 {@link TowerStructureDesign} 数据模型。 */
public final class TowerStructureCompiler {

    private TowerStructureCompiler() {
    }

    public static PoleDesign compile(TowerParameterProfile profile, ResolvedTowerParameters resolved) {
        if (profile == null || resolved == null) {
            throw new IllegalArgumentException("profile and resolved parameters are required");
        }
        PoleDesign design = new PoleDesign("parametric/" + profile.id(), "Parametric Tower");
        design.setTowerStructure(compileStructure(profile, resolved));
        design.setAttachments(TowerAttachmentCompiler.compile(profile, resolved, design.getTowerStructure()));
        return design;
    }

    public static TowerStructureDesign compileStructure(
            TowerParameterProfile profile,
            ResolvedTowerParameters resolved) {
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.setSilhouette(profile.silhouette());
        structure.setPrimaryMaterial(profile.legMaterial());
        structure.setBraceMaterial(profile.braceMaterial());
        structure.setLegProfile(new TowerMemberProfile(1));
        structure.setBraceProfile(new TowerMemberProfile(1));

        List<ResolvedTowerStation> sortedStations = resolved.stations().stream()
            .sorted(Comparator.comparingDouble(ResolvedTowerStation::height))
            .toList();
        List<TowerStation> towerStations = new ArrayList<>(sortedStations.size());
        for (ResolvedTowerStation station : sortedStations) {
            towerStations.add(new TowerStation(
                station.id(),
                station.height(),
                station.halfWidth(),
                station.halfDepth()));
        }
        structure.setStations(towerStations);

        List<BayDensityConfig> bayConfigs = profile.bayConfigsByDensity().get(resolved.density());
        if (bayConfigs == null) {
            bayConfigs = profile.bayConfigsByDensity().get(StructureDensity.MEDIUM);
        }
        for (int i = 1; i < sortedStations.size(); i++) {
            ResolvedTowerStation lower = sortedStations.get(i - 1);
            ResolvedTowerStation upper = sortedStations.get(i);
            BayDensityConfig config = bayConfigs.get(Math.min(i - 1, bayConfigs.size() - 1));
            TowerBay bay = new TowerBay(lower.id(), upper.id());
            bay.setFrontBackBracing(config.bracing());
            bay.setSideBracing(config.bracing());
            bay.setHorizontalRing(config.horizontalRing());
            bay.setPlanDiagonalBracing(false);
            structure.addBay(bay);
        }

        for (ResolvedTowerArm resolvedArm : resolved.arms()) {
            TowerArm arm = new TowerArm(resolvedArm.id(), resolvedArm.baseHeight(), resolvedArm.lateralReach());
            arm.setSide(TowerArmSide.BOTH);
            arm.setShape(resolvedArm.shape());
            arm.setVerticalDrop(resolvedArm.verticalDrop());
            arm.setLongitudinalHalfWidth(resolvedArm.longitudinalHalfWidth());
            arm.setBracing(resolvedArm.bracing());
            arm.setMaterial(profile.armMaterial());
            arm.setBraceMaterial(profile.braceMaterial());
            structure.addArm(arm);
        }

        if (TowerParameterProfiles.STEAMPUNK_ID.equals(profile.id())) {
            addSteampunkDecorations(structure, resolved, profile);
        } else {
            TowerDecoration peak = new TowerDecoration("peak", TowerDecorationKind.ANTENNA, resolved.peakDecorationHeight());
            peak.setSize(resolved.peakDecorationSize());
            peak.setMaterial(profile.braceMaterial());
            structure.addDecoration(peak);
        }
        return structure;
    }

    private static void addSteampunkDecorations(
            TowerStructureDesign structure,
            ResolvedTowerParameters resolved,
            TowerParameterProfile profile) {
        double height = resolved.height();
        double waistLow = 10.0 / TowerParameterProfiles.STEAMPUNK_REF_HEIGHT * height;
        double waistHigh = 18.0 / TowerParameterProfiles.STEAMPUNK_REF_HEIGHT * height;
        MaterialMix brass = profile.armMaterial() != null
            ? profile.armMaterial()
            : MaterialMix.single("minecraft:gold_block");

        TowerDecoration waistLower = new TowerDecoration("waist_low", TowerDecorationKind.MECHANICAL_RING, waistLow);
        waistLower.setMaterial(MaterialMix.single("minecraft:cut_copper"));
        structure.addDecoration(waistLower);

        TowerDecoration waistUpper = new TowerDecoration("waist_high", TowerDecorationKind.MECHANICAL_RING, waistHigh);
        waistUpper.setMaterial(MaterialMix.single("minecraft:exposed_copper"));
        structure.addDecoration(waistUpper);

        TowerDecoration gear = new TowerDecoration("gear", TowerDecorationKind.GEAR_RING, resolved.peakDecorationHeight());
        gear.setSize(3.0);
        gear.setMaterial(brass);
        structure.addDecoration(gear);

        TowerDecoration spire = new TowerDecoration("spire", TowerDecorationKind.ANTENNA, resolved.peakDecorationHeight() + 0.5);
        spire.setSize(2.0);
        spire.setMaterial(MaterialMix.single("minecraft:iron_bars"));
        structure.addDecoration(spire);

        addHangingChain(structure, waistLow - 1.0, 2.5, 0.8, 3.0);
        addHangingChain(structure, waistLow - 1.0, -2.5, 0.8, 3.0);
        addHangingChain(structure, waistHigh - 1.0, 0.0, 2.6, 2.0);
    }

    private static void addHangingChain(
            TowerStructureDesign structure,
            double topHeight,
            double lateralOffset,
            double longitudinalOffset,
            double dropLength) {
        TowerDecoration chain = new TowerDecoration(
            "chain_" + lateralOffset + "_" + longitudinalOffset,
            TowerDecorationKind.HANGING_CHAIN,
            topHeight);
        chain.setLateralOffset(lateralOffset);
        chain.setLongitudinalOffset(longitudinalOffset);
        chain.setSize(dropLength);
        chain.setMaterial(TowerParameterProfiles.STEAMPUNK_CHAIN);
        structure.addDecoration(chain);
    }
}
