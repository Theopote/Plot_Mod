package com.plot.plugin.powerline.design;

import com.plot.plugin.powerline.design.family.TowerConductorArrangement;
import com.plot.plugin.powerline.equipment.InsulatorType;

import java.util.ArrayList;
import java.util.List;

/**
 * 导线布局定义：任意数量挂点通道列表。
 * <p>
 * 取代 {@link com.plot.plugin.powerline.style.PowerLineStylePreset.ConductorLayout} 的 1/3 思维；
 * 塔型族与杆塔设计均可引用同一概念。
 */
public final class ConductorArrangement {
    public static final String CATALOG_SINGLE = "arrangement/single";
    public static final String CATALOG_THREE_HORIZONTAL = "arrangement/three_horizontal";
    public static final String CATALOG_THREE_VERTICAL = "arrangement/three_vertical";
    public static final String CATALOG_DOUBLE_CIRCUIT = "arrangement/double_circuit";
    public static final String CATALOG_DOUBLE_CIRCUIT_DRUM = "arrangement/double_circuit_drum";
    public static final String CATALOG_MEGA_INDUSTRIAL = "arrangement/mega_industrial";
    public static final String CATALOG_MONSTER_QUAD = "arrangement/monster_quad";

    private final String catalogId;
    private final List<ConductorChannel> channels;

    public ConductorArrangement(String catalogId, List<ConductorChannel> channels) {
        this.catalogId = catalogId != null && !catalogId.isBlank() ? catalogId : "arrangement/custom";
        this.channels = channels != null ? List.copyOf(channels) : List.of();
    }

    public String getCatalogId() {
        return catalogId;
    }

    public List<ConductorChannel> getChannels() {
        return channels;
    }

    public List<ConductorAttachment> toAttachments(
            double baseHeight,
            InsulatorType defaultInsulatorType,
            int defaultInsulatorLength) {
        List<ConductorAttachment> attachments = new ArrayList<>(channels.size());
        for (ConductorChannel channel : channels) {
            ConductorAttachment attachment = channel.toAttachment(baseHeight);
            if (attachment.getInsulatorType() == null) {
                attachment.setInsulatorType(defaultInsulatorType);
            }
            if (attachment.getInsulatorLength() <= 0) {
                attachment.setInsulatorLength(defaultInsulatorLength);
            }
            attachments.add(attachment);
        }
        return attachments;
    }

    /** 相线挂点数量（不含顶线/辅助线）。 */
    public int phaseConductorCount() {
        int count = 0;
        for (ConductorChannel channel : channels) {
            AttachmentRole role = channel.role();
            if (role == AttachmentRole.PHASE_A
                    || role == AttachmentRole.PHASE_B
                    || role == AttachmentRole.PHASE_C) {
                count++;
            }
        }
        return count > 0 ? count : 1;
    }

    public static ConductorArrangement single() {
        return new ConductorArrangement(
            CATALOG_SINGLE,
            List.of(new ConductorChannel(
                ConductorAttachmentPresets.PHASE_A_ID, "A", AttachmentRole.PHASE_A, 0, 0)));
    }

    public static ConductorArrangement threeHorizontal() {
        return new ConductorArrangement(
            CATALOG_THREE_HORIZONTAL,
            List.of(
                new ConductorChannel(ConductorAttachmentPresets.PHASE_A_ID, "A", AttachmentRole.PHASE_A, -3, 0),
                new ConductorChannel(ConductorAttachmentPresets.PHASE_B_ID, "B", AttachmentRole.PHASE_B, 0, 0),
                new ConductorChannel(ConductorAttachmentPresets.PHASE_C_ID, "C", AttachmentRole.PHASE_C, 3, 0)));
    }

    public static ConductorArrangement threeVertical() {
        return new ConductorArrangement(
            CATALOG_THREE_VERTICAL,
            List.of(
                new ConductorChannel(ConductorAttachmentPresets.PHASE_A_ID, "A", AttachmentRole.PHASE_A, 0, -1),
                new ConductorChannel(ConductorAttachmentPresets.PHASE_B_ID, "B", AttachmentRole.PHASE_B, 0, 0),
                new ConductorChannel(ConductorAttachmentPresets.PHASE_C_ID, "C", AttachmentRole.PHASE_C, 0, 1)));
    }

    /** 分裂三相 + 双顶线（3 逻辑相线 + TWIN/QUAD 截面 + 2 顶线）。 */
    public static ConductorArrangement megaIndustrialBundled() {
        return fromAttachments(
            CATALOG_MEGA_INDUSTRIAL,
            TowerConductorArrangement.megaIndustrial().createAttachments(0, InsulatorType.SUSPENSION, 2));
    }

    /** 左右双回路（6 主线）+ 双顶线。 */
    public static ConductorArrangement heavyDoubleCircuit() {
        return fromAttachments(
            CATALOG_DOUBLE_CIRCUIT,
            ConductorAttachmentPresets.doubleCircuitHorizontal(0, -14, -11, -8, 8, 11, 14));
    }

    /** 三层鼓形双回路塔：左/右各一相，共 6 根相线 + 双顶线（非 18 根）。 */
    public static ConductorArrangement doubleCircuitDrum() {
        List<ConductorChannel> channels = new ArrayList<>();
        // lower deck — phase C
        channels.add(new ConductorChannel("left_c", "LC", AttachmentRole.PHASE_C, -11, -8));
        channels.add(new ConductorChannel("right_c", "RC", AttachmentRole.PHASE_C, 11, -8));
        // middle deck — phase B (widest)
        channels.add(new ConductorChannel("left_b", "LB", AttachmentRole.PHASE_B, -14, 0));
        channels.add(new ConductorChannel("right_b", "RB", AttachmentRole.PHASE_B, 14, 0));
        // upper deck — phase A
        channels.add(new ConductorChannel("left_a", "LA", AttachmentRole.PHASE_A, -11, 8));
        channels.add(new ConductorChannel("right_a", "RA", AttachmentRole.PHASE_A, 11, 8));
        // shield wires above upper crossarm
        channels.add(new ConductorChannel("top_wire_l", "TWL", AttachmentRole.TOP_WIRE, -2.5, 12));
        channels.add(new ConductorChannel("top_wire_r", "TWR", AttachmentRole.TOP_WIRE, 2.5, 12));
        return new ConductorArrangement(CATALOG_DOUBLE_CIRCUIT_DRUM, channels);
    }

    /** 四层四回路（12 主线）+ 双顶线。 */
    public static ConductorArrangement monsterQuadCircuit() {
        return fromAttachments(
            CATALOG_MONSTER_QUAD,
            ConductorAttachmentPresets.quadCircuitWithTwinTop(0, 20, -11, -8, -5, 5, 8, 11));
    }

    public static ConductorArrangement fromLegacyLayout(
            com.plot.plugin.powerline.style.PowerLineStylePreset.ConductorLayout layout) {
        if (layout == null) {
            return single();
        }
        return switch (layout) {
            case SINGLE -> single();
            case THREE_PHASE_HORIZONTAL -> threeHorizontal();
            case THREE_PHASE_VERTICAL -> threeVertical();
        };
    }

    public static ConductorArrangement fromAttachments(String catalogId, List<ConductorAttachment> attachments) {
        List<ConductorChannel> channels = new ArrayList<>();
        if (attachments != null) {
            for (ConductorAttachment attachment : attachments) {
                if (attachment == null || !attachment.isEnabled()) {
                    continue;
                }
                channels.add(new ConductorChannel(
                    attachment.getId(),
                    attachment.getName(),
                    attachment.getRole(),
                    attachment.getLateralOffset(),
                    attachment.getVerticalOffset(),
                    attachment.getInsulatorType(),
                    attachment.getInsulatorLength(),
                    attachment.getBundleVisual()));
            }
        }
        return new ConductorArrangement(catalogId, channels);
    }
}
