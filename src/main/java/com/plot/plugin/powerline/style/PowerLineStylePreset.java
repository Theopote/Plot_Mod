package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;

import java.util.Objects;

/**
 * 内置线路风格目录项：id + i18n + {@link PowerLineStyleDefinition}。
 * <p>
 * 选「Classic Wood」应意味着完整风格包，而不是仅清空 {@code towerFamilyId}。
 */
public final class PowerLineStylePreset {
    public static final String RUSTIC_WOOD_ID = "pack/rustic_wood";
    public static final String DOUBLE_WOOD_ID = "pack/double_wood";
    public static final String URBAN_CONCRETE_ID = "pack/urban_concrete";
    public static final String INDUSTRIAL_STEEL_ID = "pack/industrial_steel";
    public static final String MODERN_UTILITY_ID = "pack/modern_utility";
    public static final String COMPACT_LATTICE_ID = "pack/compact_lattice";
    public static final String CLASSIC_LATTICE_ID = "pack/classic_lattice";
    public static final String HEAVY_LATTICE_ID = "pack/heavy_lattice";
    public static final String SMART_TOWERS_ID = "pack/smart_towers";
    public static final String TAPERED_TOWER_ID = "pack/tapered_tower";
    public static final String FANTASY_COPPER_ID = "pack/fantasy_copper";
    public static final String JAPANESE_STREET_ID = "pack/japanese_street";
    public static final String WASTELAND_WIND_ID = "pack/wasteland_wind";
    public static final String OLD_EUROPEAN_ID = "pack/old_european";
    public static final String STEAMPUNK_BRASS_ID = "pack/steampunk_brass";
    public static final String MODERN_HV_GLASS_ID = "pack/modern_hv_glass";
    public static final String SUBURBAN_LAMP_ID = "pack/suburban_lamp";
    public static final String ABANDONED_ID = "pack/abandoned";
    public static final String RUSTIC_ID = "pack/rustic";
    public static final String MEGA_LATTICE_ID = "pack/mega_lattice";
    public static final String HEAVY_DOUBLE_CIRCUIT_ID = "pack/heavy_double_circuit";
    public static final String INDUSTRIAL_PORTAL_ID = "pack/industrial_portal";
    public static final String MONSTER_PYLON_ID = "pack/monster_pylon";

    private final String id;
    private final String labelKey;
    private final StyleCategory category;
    private final PowerLineStyleDefinition definition;

    public PowerLineStylePreset(
            String id,
            String labelKey,
            StyleCategory category,
            PowerLineStyleDefinition definition) {
        this.id = id;
        this.labelKey = labelKey;
        this.category = category != null ? category : StyleCategory.UTILITY;
        this.definition = definition;
    }

    public String getId() {
        return id;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getDescriptionKey() {
        return labelKey + ".desc";
    }

    public StyleCategory getCategory() {
        return category;
    }

    public PowerLineStyleDefinition getDefinition() {
        return definition;
    }

    public StylePreviewKind getPreviewKind() {
        return definition.getPreviewKind();
    }

    public String getTowerFamilyId() {
        return definition.getTowerFamilyId();
    }

    public String getPoleDesignId() {
        return definition.getPoleDesignId();
    }

    public MaterialMix getWireMaterial() {
        return definition.getWireMaterial();
    }

    public MaterialMix getPoleMaterial() {
        return definition.getPoleMaterial();
    }

    public MaterialMix getTopWireMaterial() {
        return definition.getTopWireMaterial();
    }

    public PowerLineUiPresets.WireSag getSagPreset() {
        return definition.getSagPreset();
    }

    public double getMaxSagDepth() {
        return definition.getMaxSagDepth();
    }

    public com.plot.plugin.powerline.design.ConductorArrangement getConductorArrangement() {
        return definition.getConductorArrangement();
    }

    /** @deprecated use {@link #getConductorArrangement()} */
    @Deprecated
    public ConductorLayout getConductorLayout() {
        return definition.getConductorLayout();
    }

    public int conductorCount() {
        return getConductorArrangement().phaseConductorCount();
    }

    /** 挂点通道总数（含顶线），用于风格卡片展示。 */
    public int attachmentChannelCount() {
        return getConductorArrangement().getChannels().size();
    }

    public PoleSpacingProfile getSpacingProfile() {
        return definition.getSpacingProfile();
    }

    public void apply(PowerLineFootprint line) {
        definition.applyTo(line, id);
    }

    public boolean matches(PowerLineFootprint line) {
        if (line == null) {
            return false;
        }
        if (!matchesBundle(line)) {
            return false;
        }
        String presetId = line.getStylePresetId();
        return presetId == null || presetId.isBlank() || Objects.equals(id, presetId);
    }

    public boolean matchesBundle(PowerLineFootprint line) {
        return definition.matches(line);
    }

    /** 预设代表设计的相线挂点数量（塔族取悬垂代表塔）。 */
    public int expectedConductorCount() {
        return definition.expectedConductorCount();
    }

    static int resolveConductorCount(PowerLineFootprint line) {
        return countConductors(resolveRepresentativeDesign(line));
    }

    static PoleDesign resolveRepresentativeDesign(PowerLineFootprint line) {
        if (line == null) {
            return null;
        }
        if (line.hasTowerFamily()) {
            TowerFamily family = TowerFamilyCatalog.findBuiltin(line.getTowerFamilyId());
            if (family != null) {
                String designId = family.getDesignId(TowerRole.SUSPENSION);
                if (designId == null) {
                    designId = family.getDesignId(TowerRole.SPECIAL);
                }
                PoleDesign design = findDesignById(designId);
                if (design != null) {
                    return design;
                }
            }
        }
        return PoleDesignCatalog.findBuiltin(line.getPoleDesignId());
    }

    private static PoleDesign findDesignById(String designId) {
        if (designId == null || designId.isBlank()) {
            return null;
        }
        PoleDesign catalogDesign = PoleDesignCatalog.findBuiltin(designId);
        if (catalogDesign != null) {
            return catalogDesign;
        }
        for (PoleDesign familyDesign : TowerFamilyCatalog.familyDesigns()) {
            if (designId.equals(familyDesign.getId())) {
                return familyDesign;
            }
        }
        return null;
    }

    static int countConductors(PoleDesign design) {
        if (design == null || !design.hasEnabledAttachments()) {
            return 1;
        }
        int phases = 0;
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            AttachmentRole role = attachment.getRole();
            if (role == AttachmentRole.PHASE_A
                    || role == AttachmentRole.PHASE_B
                    || role == AttachmentRole.PHASE_C) {
                phases++;
            }
        }
        return phases > 0 ? phases : 1;
    }

    /** 导线布局（对应挂点数量与排列）。 */
    public enum ConductorLayout {
        SINGLE(1),
        THREE_PHASE_HORIZONTAL(3),
        THREE_PHASE_VERTICAL(3);

        private final int conductorCount;

        ConductorLayout(int conductorCount) {
            this.conductorCount = conductorCount;
        }

        public int conductorCount() {
            return conductorCount;
        }
    }

    public enum StylePreviewKind {
        WOOD,
        DOUBLE_WOOD,
        URBAN,
        STEEL_POLE,
        MODERN_UTILITY,
        LATTICE_POLE,
        LATTICE,
        HEAVY_LATTICE,
        ADAPTIVE,
        TAPERED,
        COPPER,
        JAPANESE,
        WASTELAND_WIND,
        OLD_EUROPEAN,
        STEAMPUNK,
        MODERN_HV_GLASS,
        SUBURBAN_LAMP,
        ABANDONED,
        RUSTIC,
        MEGA_LATTICE,
        HEAVY_DOUBLE_CIRCUIT,
        INDUSTRIAL_PORTAL,
        MONSTER_PYLON
    }
}
