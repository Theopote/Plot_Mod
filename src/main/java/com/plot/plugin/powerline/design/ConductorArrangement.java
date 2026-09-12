package com.plot.plugin.powerline.design;

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
    public static final String CATALOG_DOUBLE_CIRCUIT_THREE_DECK = "arrangement/double_circuit_three_deck";
    public static final String CATALOG_MEGA_THREE_DECK = "arrangement/mega_three_deck";
    public static final String CATALOG_UHV_THREE_DECK = "arrangement/uhv_three_deck";
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
        return count;
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

    /** 分裂三相 + 双顶线（三层 deck 版，见 {@link #megaThreeDeck()}）。 */
    public static ConductorArrangement megaIndustrialBundled() {
        return megaThreeDeck();
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
        channels.add(new ConductorChannel("left_c", "LC", AttachmentRole.PHASE_C, -11, -8));
        channels.add(new ConductorChannel("right_c", "RC", AttachmentRole.PHASE_C, 11, -8));
        channels.add(new ConductorChannel("left_b", "LB", AttachmentRole.PHASE_B, -14, 0));
        channels.add(new ConductorChannel("right_b", "RB", AttachmentRole.PHASE_B, 14, 0));
        channels.add(new ConductorChannel("left_a", "LA", AttachmentRole.PHASE_A, -11, 8));
        channels.add(new ConductorChannel("right_a", "RA", AttachmentRole.PHASE_A, 11, 8));
        channels.add(new ConductorChannel("top_wire_l", "TWL", AttachmentRole.TOP_WIRE, -2.5, 12));
        channels.add(new ConductorChannel("top_wire_r", "TWR", AttachmentRole.TOP_WIRE, 2.5, 12));
        return new ConductorArrangement(CATALOG_DOUBLE_CIRCUIT_DRUM, channels);
    }

    /**
     * 三层双回路：每层左右各一相（C / B / A），共 6 相 + 双顶线。
     * <p>
     * 默认偏移适配 {@link com.plot.plugin.powerline.design.structure.TowerStructurePresets#tripleArmTower()}：
     * base 36 → 36 / 42 / 48。
     */
    public static ConductorArrangement doubleCircuitThreeDeck() {
        return buildDoubleCircuitThreeDeck(
            CATALOG_DOUBLE_CIRCUIT_THREE_DECK,
            6.0,
            12.0,
            11.0,
            14.5,
            10.5,
            18.0,
            BundleVisual.SINGLE);
    }

    /**
     * Mega 三层双回路 + 分裂导线视觉：base 38 → 38 / 47 / 56。
     */
    public static ConductorArrangement megaThreeDeck() {
        return buildDoubleCircuitThreeDeck(
            CATALOG_MEGA_THREE_DECK,
            9.0,
            18.0,
            11.0,
            15.0,
            11.0,
            24.0,
            BundleVisual.TWIN);
    }

    /**
     * UHV 三层四回路：每层四角各一相（C / B / A），共 12 相 + 双顶线。
     * <p>
     * base 56 → 56 / 66 / 74，中层最宽以配合主横担 reach 26。
     */
    public static ConductorArrangement uhvThreeDeck() {
        List<ConductorChannel> channels = new ArrayList<>();
        addUhvQuadDeck(channels, "c", 0, 11.0, 5.0);
        addUhvQuadDeck(channels, "b", 10.0, 14.0, 8.0);
        addUhvQuadDeck(channels, "a", 18.0, 11.0, 5.0);
        channels.add(new ConductorChannel("top_wire_l", "TWL", AttachmentRole.TOP_WIRE, -3.0, 24.0));
        channels.add(new ConductorChannel("top_wire_r", "TWR", AttachmentRole.TOP_WIRE, 3.0, 24.0));
        return new ConductorArrangement(CATALOG_UHV_THREE_DECK, channels);
    }

    /** @deprecated 使用 {@link #uhvThreeDeck()} */
    @Deprecated
    public static ConductorArrangement monsterQuadCircuit() {
        return uhvThreeDeck();
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

    private static ConductorArrangement buildDoubleCircuitThreeDeck(
            String catalogId,
            double middleDeckOffset,
            double upperDeckOffset,
            double lowerLateral,
            double middleLateral,
            double upperLateral,
            double topWireOffset,
            BundleVisual bundleVisual) {
        List<ConductorChannel> channels = new ArrayList<>();
        addDoubleCircuitDeck(channels, "c", AttachmentRole.PHASE_C, 0, lowerLateral, bundleVisual);
        addDoubleCircuitDeck(channels, "b", AttachmentRole.PHASE_B, middleDeckOffset, middleLateral, bundleVisual);
        addDoubleCircuitDeck(channels, "a", AttachmentRole.PHASE_A, upperDeckOffset, upperLateral, bundleVisual);
        channels.add(new ConductorChannel(
            "top_wire_l", "TWL", AttachmentRole.TOP_WIRE, -2.5, topWireOffset, InsulatorType.SUSPENSION, 1, bundleVisual));
        channels.add(new ConductorChannel(
            "top_wire_r", "TWR", AttachmentRole.TOP_WIRE, 2.5, topWireOffset, InsulatorType.SUSPENSION, 1, bundleVisual));
        return new ConductorArrangement(catalogId, channels);
    }

    private static void addDoubleCircuitDeck(
            List<ConductorChannel> channels,
            String phaseSuffix,
            AttachmentRole role,
            double deckOffset,
            double lateral,
            BundleVisual bundleVisual) {
        channels.add(new ConductorChannel(
            "left_" + phaseSuffix, "L" + phaseSuffix.toUpperCase(), role, -lateral, deckOffset,
            InsulatorType.SUSPENSION, 2, bundleVisual));
        channels.add(new ConductorChannel(
            "right_" + phaseSuffix, "R" + phaseSuffix.toUpperCase(), role, lateral, deckOffset,
            InsulatorType.SUSPENSION, 2, bundleVisual));
    }

    private static void addUhvQuadDeck(
            List<ConductorChannel> channels,
            String phaseSuffix,
            double deckOffset,
            double outerLateral,
            double innerLateral) {
        AttachmentRole role = switch (phaseSuffix) {
            case "a" -> AttachmentRole.PHASE_A;
            case "b" -> AttachmentRole.PHASE_B;
            default -> AttachmentRole.PHASE_C;
        };
        String upper = phaseSuffix.toUpperCase();
        channels.add(new ConductorChannel("ll_phase_" + phaseSuffix, "LL" + upper, role, -outerLateral, deckOffset));
        channels.add(new ConductorChannel("lr_phase_" + phaseSuffix, "LR" + upper, role, outerLateral, deckOffset));
        channels.add(new ConductorChannel("ul_phase_" + phaseSuffix, "UL" + upper, role, -innerLateral, deckOffset));
        channels.add(new ConductorChannel("ur_phase_" + phaseSuffix, "UR" + upper, role, innerLateral, deckOffset));
    }
}
