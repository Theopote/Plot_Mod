package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;

import java.util.Objects;

/**
 * 线路风格定义：杆塔/材质/垂度/间距等一整套装饰默认值（不可变）。
 * <p>
 * 目录项 {@link PowerLineStylePreset} = id + i18n + {@link PowerLineStyleDefinition}；
 * 线路实例 {@link PowerLineStyleInstance} = base preset + {@link StyleOverrides}。
 */
public final class PowerLineStyleDefinition {
    private static final double SAG_MATCH_TOLERANCE = 0.01;
    private static final double MAX_SAG_MATCH_TOLERANCE = 0.5;

    private final String towerFamilyId;
    private final String poleDesignId;
    private final MaterialMix wireMaterial;
    private final MaterialMix poleMaterial;
    private final MaterialMix topWireMaterial;
    private final PowerLineUiPresets.WireSag sagPreset;
    private final double maxSagDepth;
    private final ConductorArrangement conductorArrangement;
    private final PoleSpacingProfile spacingProfile;
    private final PowerLineStylePreset.StylePreviewKind previewKind;
    private final TowerGeneratorConfig parametricConfig;

    public PowerLineStyleDefinition(
            PowerLineStylePreset.StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix topWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            ConductorArrangement conductorArrangement,
            PoleSpacingProfile spacingProfile) {
        this(
            previewKind,
            towerFamilyId,
            poleDesignId,
            wireMaterial,
            poleMaterial,
            topWireMaterial,
            sagPreset,
            PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH,
            conductorArrangement,
            spacingProfile,
            null);
    }

    public PowerLineStyleDefinition(
            PowerLineStylePreset.StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix topWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            double maxSagDepth,
            ConductorArrangement conductorArrangement,
            PoleSpacingProfile spacingProfile) {
        this(
            previewKind,
            towerFamilyId,
            poleDesignId,
            wireMaterial,
            poleMaterial,
            topWireMaterial,
            sagPreset,
            maxSagDepth,
            conductorArrangement,
            spacingProfile,
            null);
    }

    public PowerLineStyleDefinition(
            PowerLineStylePreset.StylePreviewKind previewKind,
            String towerFamilyId,
            String poleDesignId,
            MaterialMix wireMaterial,
            MaterialMix poleMaterial,
            MaterialMix topWireMaterial,
            PowerLineUiPresets.WireSag sagPreset,
            double maxSagDepth,
            ConductorArrangement conductorArrangement,
            PoleSpacingProfile spacingProfile,
            TowerGeneratorConfig parametricConfig) {
        this.previewKind = previewKind;
        this.towerFamilyId = blankToNull(towerFamilyId);
        this.poleDesignId = blankToNull(poleDesignId);
        this.wireMaterial = wireMaterial;
        this.poleMaterial = poleMaterial;
        this.topWireMaterial = topWireMaterial;
        this.sagPreset = sagPreset;
        this.maxSagDepth = sanitizeMaxSagDepth(maxSagDepth);
        this.conductorArrangement = conductorArrangement != null
            ? conductorArrangement
            : ConductorArrangement.single();
        this.spacingProfile = spacingProfile != null
            ? spacingProfile
            : PoleSpacingProfile.streetWood();
        this.parametricConfig = parametricConfig != null ? parametricConfig.copy() : null;
    }

    public TowerGeneratorConfig getParametricConfig() {
        return parametricConfig != null ? parametricConfig.copy() : null;
    }

    public boolean hasParametricConfig() {
        return parametricConfig != null && parametricConfig.isParametric();
    }

    public PowerLineStylePreset.StylePreviewKind getPreviewKind() {
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

    public PowerLineUiPresets.WireSag getSagPreset() {
        return sagPreset;
    }

    /** 风格默认最大下垂深度（格），视觉控制用。 */
    public double getMaxSagDepth() {
        return maxSagDepth;
    }

    public ConductorArrangement getConductorArrangement() {
        return conductorArrangement;
    }

    /** @deprecated use {@link #getConductorArrangement()} */
    @Deprecated
    public PowerLineStylePreset.ConductorLayout getConductorLayout() {
        String catalogId = conductorArrangement.getCatalogId();
        if (ConductorArrangement.CATALOG_THREE_VERTICAL.equals(catalogId)) {
            return PowerLineStylePreset.ConductorLayout.THREE_PHASE_VERTICAL;
        }
        if (ConductorArrangement.CATALOG_THREE_HORIZONTAL.equals(catalogId)
                || ConductorArrangement.CATALOG_MEGA_INDUSTRIAL.equals(catalogId)
                || ConductorArrangement.CATALOG_DOUBLE_CIRCUIT.equals(catalogId)
                || ConductorArrangement.CATALOG_MONSTER_QUAD.equals(catalogId)) {
            return PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL;
        }
        return PowerLineStylePreset.ConductorLayout.SINGLE;
    }

    public PoleSpacingProfile getSpacingProfile() {
        return spacingProfile;
    }

    /** 将定义写入 footprint，并记录 base preset id。 */
    public void applyTo(PowerLineFootprint line, String presetId) {
        if (line == null || presetId == null || presetId.isBlank()) {
            return;
        }
        line.setStylePresetId(presetId);
        if (towerFamilyId != null) {
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
        line.setMaxSagDepth(maxSagDepth);
        if (!line.isSpacingCustomized()) {
            PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, spacingProfile);
        }
        line.setParametricTowerConfig(getParametricConfig());
    }

    /** footprint 生效值是否仍与本定义默认 bundle 一致（不含 spacingCustomized）。 */
    public boolean matches(PowerLineFootprint line) {
        if (line == null) {
            return false;
        }
        if (!Objects.equals(towerFamilyId, blankToNull(line.getTowerFamilyId()))) {
            return false;
        }
        if (!Objects.equals(poleDesignId, blankToNull(line.getPoleDesignId()))) {
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
        if (!maxSagMatches(maxSagDepth, line)) {
            return false;
        }
        if (!PowerLineStyleParametricCatalog.parametersMatch(parametricConfig, line.getParametricTowerConfig())) {
            return false;
        }
        return expectedConductorCount() == PowerLineStylePreset.resolveConductorCount(line);
    }

    /** 定义代表设计的相线挂点数量（塔族取悬垂代表塔）。 */
    public int expectedConductorCount() {
        if (hasParametricConfig()) {
            return conductorArrangement.phaseConductorCount();
        }
        int fromDesign = PowerLineStylePreset.countConductors(representativeDesign());
        return fromDesign > 0 ? fromDesign : conductorArrangement.phaseConductorCount();
    }

    PoleDesign representativeDesign() {
        if (hasParametricConfig()) {
            PoleDesign parametric = PowerLineStyleParametricCatalog.compileRepresentative(parametricConfig);
            if (parametric != null) {
                return parametric;
            }
        }
        if (towerFamilyId != null) {
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
        if (poleDesignId != null) {
            return PoleDesignCatalog.findBuiltin(poleDesignId);
        }
        return null;
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

    private static boolean sagMatches(PowerLineUiPresets.WireSag preset, double ratio) {
        return Math.abs(preset.ratio() - ratio) <= SAG_MATCH_TOLERANCE;
    }

    private static boolean maxSagMatches(double expected, PowerLineFootprint line) {
        if (line.isMaxSagDepthUnlimited()) {
            return false;
        }
        return Math.abs(line.getMaxSagDepth() - expected) <= MAX_SAG_MATCH_TOLERANCE;
    }

    private static boolean materialMatches(MaterialMix expected, MaterialMix actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return Objects.equals(expected.getPrimaryMaterial(), actual.getPrimaryMaterial())
            && Objects.equals(blankToNull(expected.getAccentMaterial()), blankToNull(actual.getAccentMaterial()))
            && Float.compare(expected.getAccentRatio(), actual.getAccentRatio()) == 0;
    }

    private static double sanitizeMaxSagDepth(double maxSagDepth) {
        if (maxSagDepth <= 0.0) {
            return PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        }
        return Math.max(1.0, Math.min(64.0, maxSagDepth));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
