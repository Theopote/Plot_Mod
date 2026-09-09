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
 * 线路风格预设：杆塔/塔族、材质、垂度、导线布局等一整套装饰默认值。
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

    private static final double SAG_MATCH_TOLERANCE = 0.01;

    private final String id;
    private final String labelKey;
    private final StylePreviewKind previewKind;
    private final String towerFamilyId;
    private final String poleDesignId;
    private final MaterialMix wireMaterial;
    private final MaterialMix poleMaterial;
    private final MaterialMix topWireMaterial;
    private final PowerLineUiPresets.WireSag sagPreset;
    private final ConductorLayout conductorLayout;
    private final PoleSpacingProfile spacingProfile;

    public PowerLineStylePreset(
            String id,
            String labelKey,
            StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix topWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            ConductorLayout conductorLayout,
            PoleSpacingProfile spacingProfile) {
        this.id = id;
        this.labelKey = labelKey;
        this.previewKind = previewKind;
        this.towerFamilyId = towerFamilyId;
        this.poleDesignId = poleDesignId;
        this.wireMaterial = wireMaterial;
        this.poleMaterial = poleMaterial;
        this.topWireMaterial = topWireMaterial;
        this.sagPreset = sagPreset;
        this.conductorLayout = conductorLayout != null ? conductorLayout : ConductorLayout.SINGLE;
        this.spacingProfile = spacingProfile != null
            ? spacingProfile
            : PoleSpacingProfile.streetWood();
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

    public StylePreviewKind getPreviewKind() {
        return previewKind;
    }

    public String getTowerFamilyId() {
        return towerFamilyId;
    }

    public String getPoleDesignId() {
        return poleDesignId;
    }

    public MaterialMix getWireMaterial() {
        return wireMaterial;
    }

    public MaterialMix getPoleMaterial() {
        return poleMaterial;
    }

    public MaterialMix getTopWireMaterial() {
        return topWireMaterial;
    }

    /** @deprecated use {@link #getTopWireMaterial()} */
    @Deprecated
    public MaterialMix getGroundWireMaterial() {
        return getTopWireMaterial();
    }

    public PowerLineUiPresets.WireSag getSagPreset() {
        return sagPreset;
    }

    public ConductorLayout getConductorLayout() {
        return conductorLayout;
    }

    public int conductorCount() {
        return conductorLayout.conductorCount();
    }

    public PoleSpacingProfile getSpacingProfile() {
        return spacingProfile;
    }

    public void apply(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        line.setStylePresetId(id);
        if (towerFamilyId != null && !towerFamilyId.isBlank()) {
            line.setTowerFamilyId(towerFamilyId);
            line.setPoleDesignId(null);
        } else {
            line.setTowerFamilyId(null);
            line.setPoleDesignId(poleDesignId);
        }
        line.setWireMaterial(wireMaterial);
        line.setPoleMaterial(poleMaterial);
        line.setTopWireMaterial(topWireMaterial);
        PowerLineUiPresets.applySag(line, sagPreset);
        if (!line.isSpacingCustomized()) {
            PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, spacingProfile);
        }
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
        if (line == null) {
            return false;
        }
        if (!Objects.equals(normalize(towerFamilyId), normalize(line.getTowerFamilyId()))) {
            return false;
        }
        if (!Objects.equals(normalize(poleDesignId), normalize(line.getPoleDesignId()))) {
            return false;
        }
        if (!materialMatches(wireMaterial, line.getWireMaterial())) {
            return false;
        }
        if (!materialMatches(poleMaterial, line.getPoleMaterial())) {
            return false;
        }
        if (!materialMatches(topWireMaterial, line.getTopWireMaterial())) {
            return false;
        }
        if (!sagMatches(sagPreset, line.getSagRatio())) {
            return false;
        }
        return expectedConductorCount() == resolveConductorCount(line);
    }

    /** 预设代表设计的相线挂点数量（塔族取悬垂代表塔）。 */
    public int expectedConductorCount() {
        int fromDesign = countConductors(representativeDesignForPreset());
        return fromDesign > 0 ? fromDesign : conductorLayout.conductorCount();
    }

    static int resolveConductorCount(PowerLineFootprint line) {
        return countConductors(resolveRepresentativeDesign(line));
    }

    private PoleDesign representativeDesignForPreset() {
        if (towerFamilyId != null && !towerFamilyId.isBlank()) {
            TowerFamily family = TowerFamilyCatalog.findBuiltin(towerFamilyId);
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
        if (poleDesignId != null && !poleDesignId.isBlank()) {
            return PoleDesignCatalog.findBuiltin(poleDesignId);
        }
        return null;
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

    private static boolean sagMatches(PowerLineUiPresets.WireSag preset, double ratio) {
        return Math.abs(preset.ratio() - ratio) <= SAG_MATCH_TOLERANCE;
    }

    private static boolean materialMatches(MaterialMix expected, MaterialMix actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return Objects.equals(expected.getPrimaryMaterial(), actual.getPrimaryMaterial());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
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
        RUSTIC
    }
}
