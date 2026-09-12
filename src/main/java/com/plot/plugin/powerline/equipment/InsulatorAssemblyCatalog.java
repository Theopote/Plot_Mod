package com.plot.plugin.powerline.equipment;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内置绝缘子串目录（供挂点 {@code insulatorAssemblyId} 引用）。 */
public final class InsulatorAssemblyCatalog {
    public static final String SHORT_SUSPENSION_ID = "insulator/short_suspension";
    public static final String LONG_SUSPENSION_ID = "insulator/long_suspension";
    public static final String LONG_STRAIN_ID = "insulator/long_strain";
    public static final String TWIN_STRING_ID = "insulator/twin_string";
    public static final String V_PAIR_ID = "insulator/v_pair";
    public static final String TOP_WIRE_SHORT_ID = "insulator/top_wire_short";

    private InsulatorAssemblyCatalog() {
    }

    public static List<InsulatorAssembly> defaultAssemblies() {
        return List.of(
            shortSuspension(),
            longSuspension(),
            longStrain(),
            twinString(),
            vPair(),
            topWireShort());
    }

    public static Map<String, InsulatorAssembly> indexById() {
        Map<String, InsulatorAssembly> indexed = new LinkedHashMap<>();
        for (InsulatorAssembly assembly : defaultAssemblies()) {
            indexed.put(assembly.getId(), assembly);
        }
        return indexed;
    }

    public static InsulatorAssembly find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return indexById().get(id);
    }

    public static InsulatorAssembly shortSuspension() {
        return assembly(SHORT_SUSPENSION_ID, InsulatorType.SUSPENSION, InsulatorMountStyle.COLUMN, 2);
    }

    public static InsulatorAssembly longSuspension() {
        return assembly(LONG_SUSPENSION_ID, InsulatorType.SUSPENSION, InsulatorMountStyle.COLUMN, 4);
    }

    public static InsulatorAssembly longStrain() {
        return assembly(LONG_STRAIN_ID, InsulatorType.STRAIN, InsulatorMountStyle.HORIZONTAL, 4);
    }

    public static InsulatorAssembly twinString() {
        return assembly(TWIN_STRING_ID, InsulatorType.VERTICAL, InsulatorMountStyle.TWIN_COLUMN, 4);
    }

    public static InsulatorAssembly vPair() {
        return assembly(V_PAIR_ID, InsulatorType.SUSPENSION, InsulatorMountStyle.V_PAIR, 5);
    }

    public static InsulatorAssembly topWireShort() {
        InsulatorAssembly assembly = assembly(
            TOP_WIRE_SHORT_ID,
            InsulatorType.SUSPENSION,
            InsulatorMountStyle.COLUMN,
            1);
        assembly.setMaterial(MaterialMix.single("minecraft:chain"));
        return assembly;
    }

    /** 标准塔：沿用挂点上的 type/length，仅补顶线 assembly。 */
    public static void applyStandardDefaults(PoleDesign design) {
        if (design == null || !design.hasEnabledAttachments()) {
            return;
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.getRole() == AttachmentRole.TOP_WIRE) {
                applyAssembly(attachment, topWireShort());
            }
        }
    }

    /** 超大型工业塔：长串 / 双串 / 耐张水平串。 */
    public static void applyMegaDefaults(PoleDesign design, InsulatorType roleType) {
        if (design == null || !design.hasEnabledAttachments()) {
            return;
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.getRole() == AttachmentRole.TOP_WIRE) {
                applyAssembly(attachment, topWireShort());
                continue;
            }
            if (roleType == InsulatorType.STRAIN) {
                applyAssembly(attachment, longStrain());
            } else {
                applyAssembly(attachment, twinString());
            }
        }
    }

    /** 怪物级塔：外相 V 型串，其余双串。 */
    public static void applyMonsterDefaults(PoleDesign design, InsulatorType roleType) {
        if (design == null || !design.hasEnabledAttachments()) {
            return;
        }
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (attachment.getRole() == AttachmentRole.TOP_WIRE) {
                applyAssembly(attachment, topWireShort());
                continue;
            }
            if (roleType == InsulatorType.STRAIN) {
                applyAssembly(attachment, longStrain());
                continue;
            }
            applyAssembly(attachment, usesOuterPhaseInsulator(attachment) ? vPair() : twinString());
        }
    }

    public static void applyAssembly(ConductorAttachment attachment, InsulatorAssembly assembly) {
        if (attachment == null || assembly == null) {
            return;
        }
        attachment.setInsulatorAssemblyId(assembly.getId());
        attachment.setInsulatorType(assembly.getType());
        attachment.setInsulatorLength(assembly.getLength());
        attachment.setInsulatorMaterial(assembly.getMaterial());
    }

    static boolean usesOuterPhaseInsulator(ConductorAttachment attachment) {
        if (attachment == null) {
            return false;
        }
        if (attachment.isOuterPhaseInsulator()) {
            return true;
        }
        return inferLegacyOuterPhaseInsulator(attachment.getId());
    }

    private static boolean inferLegacyOuterPhaseInsulator(String id) {
        return id != null && (id.endsWith("_a") || id.endsWith("_c")
            || id.contains("phase_a") || id.contains("phase_c")
            || id.startsWith("left_") || id.startsWith("right_")
            || id.startsWith("ul_") || id.startsWith("ur_") || id.startsWith("ll_") || id.startsWith("lr_"));
    }

    private static InsulatorAssembly assembly(
            String id,
            InsulatorType type,
            InsulatorMountStyle mountStyle,
            int length) {
        InsulatorAssembly assembly = new InsulatorAssembly(id, type, length);
        assembly.setMountStyle(mountStyle);
        return assembly;
    }
}
