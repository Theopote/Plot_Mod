package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.equipment.InsulatorType;

import java.util.ArrayList;
import java.util.List;

/** 塔型挂点拓扑（视觉导线布局，无电气工程语义）。 */
public record TowerConductorArrangement(
        int bundleCount,
        double bundleSpacing,
        double lateralA,
        double lateralB,
        double lateralC,
        TopWireMode topWireMode,
        double topWireLift,
        double topWireHalfSeparation) {

    public enum TopWireMode {
        NONE,
        SINGLE,
        TWIN
    }

    /** 经典格构塔：分裂导线 + 双顶线（标准相间距）。 */
    public static TowerConductorArrangement classicLattice() {
        return new TowerConductorArrangement(2, 0.6, -6, 0, 6, TopWireMode.TWIN, 4, 1.5);
    }

    /** 重型输电塔：更宽相间距 + 分裂导线 + 双顶线。 */
    public static TowerConductorArrangement heavyTransmission() {
        return new TowerConductorArrangement(2, 0.8, -9, 0, 9, TopWireMode.TWIN, 5, 2.0);
    }

    public List<ConductorAttachment> createAttachments(
            double attachmentHeight,
            InsulatorType insulatorType,
            int insulatorLength) {
        List<ConductorAttachment> attachments = new ArrayList<>();
        if (bundleCount <= 1) {
            attachments.addAll(ConductorAttachmentPresets.threePhaseHorizontal(
                attachmentHeight, lateralA, lateralB, lateralC));
        } else {
            attachments.addAll(ConductorAttachmentPresets.bundledThreePhaseHorizontal(
                attachmentHeight, lateralA, lateralB, lateralC, bundleCount, bundleSpacing));
        }
        switch (topWireMode) {
            case TWIN -> attachments.addAll(
                ConductorAttachmentPresets.twinTopWires(attachmentHeight + topWireLift, topWireHalfSeparation));
            case SINGLE -> {
                ConductorAttachment topWire = new ConductorAttachment(
                    TowerFamilyDesignPresets.GROUND_WIRE_ID, "GW");
                topWire.setRole(AttachmentRole.GROUND_WIRE);
                topWire.setVerticalOffset(attachmentHeight + topWireLift);
                topWire.setInsulatorLength(1);
                attachments.add(topWire);
            }
            case NONE -> { }
        }
        for (ConductorAttachment attachment : attachments) {
            attachment.setInsulatorLength(insulatorLength);
            attachment.setInsulatorType(insulatorType);
        }
        return attachments;
    }
}
