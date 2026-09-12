package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.TowerArmAttachmentBinding;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.equipment.InsulatorAssemblyCatalog;
import com.plot.plugin.powerline.equipment.InsulatorType;

import java.util.Comparator;
import java.util.List;

/** 将 Profile 导线拓扑策略编译为挂点列表。 */
public final class TowerAttachmentCompiler {
    private TowerAttachmentCompiler() {
    }

    public static List<ConductorAttachment> compile(
            TowerParameterProfile profile,
            ResolvedTowerParameters resolved,
            TowerStructureDesign structure) {
        TowerAttachmentTopology topology = profile.attachmentTopology();
        double baseHeight = resolveAttachmentBaseHeight(topology, structure);
        List<ConductorAttachment> attachments;
        if (topology.deckArrangement() != null) {
            attachments = topology.deckArrangement().toAttachments(
                baseHeight,
                InsulatorType.SUSPENSION,
                2);
        } else {
            attachments = topology.latticeArrangement().createAttachments(
                baseHeight,
                InsulatorType.SUSPENSION,
                latticeInsulatorLength(topology));
        }
        if (topology.deckArrangement() != null && usesMegaInsulatorDefaults(topology)) {
            InsulatorAssemblyCatalog.applyMegaDefaults(toDesign(structure, attachments), InsulatorType.SUSPENSION);
            return toDesign(structure, attachments).getAttachments();
        }
        InsulatorAssemblyCatalog.applyStandardDefaults(toDesign(structure, attachments));
        return toDesign(structure, attachments).getAttachments();
    }

    private static PoleDesign toDesign(TowerStructureDesign structure, List<ConductorAttachment> attachments) {
        PoleDesign design = new PoleDesign("_compile", "Compile");
        design.setTowerStructure(structure);
        design.setAttachments(attachments);
        return design;
    }

    private static double resolveAttachmentBaseHeight(
            TowerAttachmentTopology topology,
            TowerStructureDesign structure) {
        List<TowerArm> arms = structure.getArms().stream()
            .sorted(Comparator.comparingDouble(TowerArm::getBaseHeight))
            .toList();
        if (arms.isEmpty()) {
            return 12.0;
        }
        TowerArm anchor = topology.anchor() == TowerAttachmentTopology.AttachmentAnchor.UPPER_ARM
            ? arms.get(arms.size() - 1)
            : arms.getFirst();
        return TowerArmAttachmentBinding.conductorHangHeight(anchor);
    }

    private static int latticeInsulatorLength(TowerAttachmentTopology topology) {
        var lattice = topology.latticeArrangement();
        if (lattice == com.plot.plugin.powerline.design.family.TowerConductorArrangement.heavyTransmission()
                || lattice == com.plot.plugin.powerline.design.family.TowerConductorArrangement.megaIndustrial()) {
            return 3;
        }
        return 2;
    }

    private static boolean usesMegaInsulatorDefaults(TowerAttachmentTopology topology) {
        var deck = topology.deckArrangement();
        return deck == com.plot.plugin.powerline.design.ConductorArrangement.megaThreeDeck()
            || deck == com.plot.plugin.powerline.design.ConductorArrangement.uhvThreeDeck();
    }
}
