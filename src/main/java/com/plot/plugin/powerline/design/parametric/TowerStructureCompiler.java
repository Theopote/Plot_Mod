package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmSide;
import com.plot.plugin.powerline.design.structure.TowerBay;
import com.plot.plugin.powerline.design.structure.TowerDecoration;
import com.plot.plugin.powerline.design.structure.TowerDecorationKind;
import com.plot.plugin.powerline.design.structure.TowerMemberProfile;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;

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
        design.setAttachments(compileAttachments(resolved, design.getTowerStructure()));
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
        for (ResolvedTowerStation station : sortedStations) {
            structure.addStation(new TowerStation(
                station.id(),
                station.height(),
                station.halfWidth(),
                station.halfDepth()));
        }

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
            bay.setPlanDiagonalBracing(config.planDiagonal());
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
            structure.addArm(arm);
        }

        TowerDecoration peak = new TowerDecoration("peak", TowerDecorationKind.ANTENNA, resolved.peakDecorationHeight());
        peak.setSize(resolved.peakDecorationSize());
        peak.setMaterial(profile.braceMaterial());
        structure.addDecoration(peak);
        return structure;
    }

    public static List<ConductorAttachment> compileAttachments(
            ResolvedTowerParameters resolved,
            TowerStructureDesign structure) {
        List<ConductorAttachment> attachments = new ArrayList<>();
        List<TowerArm> arms = structure.getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
        for (TowerArm arm : arms) {
            attachments.addAll(TowerArmAttachmentBinding.createBundledThreePhaseDeck(arm, 2));
        }
        if (!arms.isEmpty()) {
            TowerArm upperArm = arms.get(arms.size() - 1);
            double topWireHeight = TowerArmAttachmentBinding.conductorHangHeight(upperArm) + resolved.topWireLift();
            attachments.addAll(ConductorAttachmentPresets.twinTopWires(topWireHeight, 1.5));
        }
        return attachments;
    }
}
