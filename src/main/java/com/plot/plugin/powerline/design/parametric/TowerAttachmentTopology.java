package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.family.TowerConductorArrangement;

/**
 * 参数化塔型的导线拓扑策略：几何 Profile 与挂点布局分离。
 * <p>
 * {@link #deckArrangement()} 用于多层 deck / 双回路布局；
 * {@link #latticeArrangement()} 用于单组分裂三相 + 顶线布局。
 */
public record TowerAttachmentTopology(
        ConductorArrangement deckArrangement,
        TowerConductorArrangement latticeArrangement,
        AttachmentAnchor anchor) {

    public enum AttachmentAnchor {
        /** 最低横担高度 — 适配 deck 类 offset 布局。 */
        LOWER_ARM,
        /** 最高横担挂线高度 — 适配 classic / heavy 单组分裂三相。 */
        UPPER_ARM
    }

    public TowerAttachmentTopology {
        if (deckArrangement == null && latticeArrangement == null) {
            throw new IllegalArgumentException("deck or lattice arrangement is required");
        }
        if (deckArrangement != null && latticeArrangement != null) {
            throw new IllegalArgumentException("only one arrangement source may be set");
        }
        anchor = anchor != null ? anchor : AttachmentAnchor.LOWER_ARM;
    }

    public static TowerAttachmentTopology classicLattice() {
        return new TowerAttachmentTopology(
            null,
            TowerConductorArrangement.classicLattice(),
            AttachmentAnchor.UPPER_ARM);
    }

    public static TowerAttachmentTopology heavyTransmission() {
        return new TowerAttachmentTopology(
            null,
            TowerConductorArrangement.heavyTransmission(),
            AttachmentAnchor.UPPER_ARM);
    }

    public static TowerAttachmentTopology megaIndustrial() {
        return new TowerAttachmentTopology(
            null,
            TowerConductorArrangement.megaIndustrial(),
            AttachmentAnchor.UPPER_ARM);
    }

    public static TowerAttachmentTopology doubleCircuitThreeDeck() {
        return new TowerAttachmentTopology(
            ConductorArrangement.doubleCircuitThreeDeck(),
            null,
            AttachmentAnchor.LOWER_ARM);
    }

    public static TowerAttachmentTopology megaThreeDeck() {
        return new TowerAttachmentTopology(
            ConductorArrangement.megaThreeDeck(),
            null,
            AttachmentAnchor.LOWER_ARM);
    }

    public static TowerAttachmentTopology doubleCircuitDrum() {
        return new TowerAttachmentTopology(
            ConductorArrangement.doubleCircuitDrum(),
            null,
            AttachmentAnchor.LOWER_ARM);
    }

    public static TowerAttachmentTopology heavyDoubleCircuit() {
        return new TowerAttachmentTopology(
            ConductorArrangement.heavyDoubleCircuit(),
            null,
            AttachmentAnchor.LOWER_ARM);
    }

    public static TowerAttachmentTopology uhvThreeDeck() {
        return new TowerAttachmentTopology(
            ConductorArrangement.uhvThreeDeck(),
            null,
            AttachmentAnchor.LOWER_ARM);
    }

    public static TowerAttachmentTopology threeHorizontal() {
        return new TowerAttachmentTopology(
            ConductorArrangement.threeHorizontal(),
            null,
            AttachmentAnchor.UPPER_ARM);
    }
}
