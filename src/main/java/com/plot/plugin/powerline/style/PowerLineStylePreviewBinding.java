package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;

/**
 * 风格预览与预设配置的绑定关系（画廊卡片、tooltip、Quick Tune 大图、建造摘要共用）。
 * <p>
 * 逻辑与 {@link com.plot.plugin.powerline.ui.PowerLineStyleCardRenderer} 的 {@code drawPackPreview}
 * / {@code drawCardPreview} 保持同步。
 */
public final class PowerLineStylePreviewBinding {
    private PowerLineStylePreviewBinding() {
    }

    /** Gallery 卡片预览绑定：体素立面 + 叠加层。 */
    public static StyleCardPreviewBinding cardPreviewBinding(PowerLineStylePreset preset) {
        return new StyleCardPreviewBinding(previewDesign(preset), previewOverlay(preset));
    }

    /**
     * 当前生效设计的预览绑定（Quick Tune / 建造摘要 / tooltip）。
     * 塔体与落地建造共用 {@link com.plot.plugin.powerline.preview.PoleVoxelizer} 体素路径。
     */
    public static StyleCardPreviewBinding bindingForDesign(PoleDesign design, PowerLineStylePreset base) {
        return new StyleCardPreviewBinding(design, previewOverlayForDesign(design, base));
    }

    static PreviewOverlay previewOverlayForDesign(PoleDesign design, PowerLineStylePreset base) {
        if (design != null && design.hasTowerStructure()) {
            return PreviewOverlay.ATTACHMENTS;
        }
        PreviewOverlay fromBase = base != null ? previewOverlay(base) : PreviewOverlay.NONE;
        if (fromBase != PreviewOverlay.NONE) {
            return fromBase;
        }
        if (design != null && design.hasEnabledAttachments()) {
            return PreviewOverlay.DECORATIVE_CONDUCTORS;
        }
        return PreviewOverlay.NONE;
    }

    public static PreviewOverlay previewOverlay(PowerLineStylePreset preset) {
        if (preset == null) {
            return PreviewOverlay.NONE;
        }
        return switch (preset.getPreviewKind()) {
            case LATTICE, HEAVY_LATTICE, TRIPLE_ARM, CUP_TOWER, MEGA_LATTICE, HEAVY_DOUBLE_CIRCUIT,
                 INDUSTRIAL_PORTAL, MONSTER_PYLON, ADAPTIVE, LATTICE_POLE, TAPERED, STEAMPUNK, MODERN_HV_GLASS
                -> PreviewOverlay.ATTACHMENTS;
            case WOOD, DOUBLE_WOOD, STEEL_POLE, URBAN, MODERN_UTILITY, JAPANESE, OLD_EUROPEAN, SUBURBAN_LAMP,
                 ABANDONED, RUSTIC, COPPER, WASTELAND_WIND
                -> PreviewOverlay.DECORATIVE_CONDUCTORS;
        };
    }

    /** 智能铁塔使用高档代表塔，与 Smart Towers 大档距落地一致。 */
    public static boolean usesAdaptiveHeightMarker(PowerLineStylePreset preset) {
        return preset != null && preset.getPreviewKind() == PowerLineStylePreset.StylePreviewKind.ADAPTIVE;
    }

    /** 风格卡片 / tooltip 用的预览 {@link PoleDesign}。 */
    public static PoleDesign previewDesign(PowerLineStylePreset preset) {
        if (preset == null) {
            return null;
        }
        PowerLineStyleDefinition definition = preset.getDefinition();
        if (definition != null && definition.hasParametricConfig()) {
            PoleDesign familyRepresentative = gradedFamilyPreviewDesign(definition);
            if (familyRepresentative != null) {
                return familyRepresentative;
            }
            PoleDesign parametric = PowerLineStyleParametricCatalog.compileRepresentative(
                definition.getParametricConfig());
            if (parametric != null) {
                return parametric;
            }
        }
        return resolvePreviewDesign(primaryPreviewDesignId(preset));
    }

    public static PoleDesign resolvePreviewDesign(String designId) {
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

    /** 缩略图主轮廓对应的杆塔设计 ID；塔型族预设返回族内悬垂代表设计。 */
    public static String primaryPreviewDesignId(PowerLineStylePreset preset) {
        if (preset == null) {
            return null;
        }
        return switch (preset.getPreviewKind()) {
            case WOOD -> PoleDesignCatalog.SIMPLE_WOOD_POLE_ID;
            case DOUBLE_WOOD -> PoleDesignCatalog.DOUBLE_WOOD_POLE_ID;
            case URBAN -> PoleDesignCatalog.URBAN_CONCRETE_POLE_ID;
            case STEEL_POLE -> PoleDesignCatalog.MODERN_STEEL_POLE_ID;
            case MODERN_UTILITY -> PoleDesignCatalog.MODERN_UTILITY_POLE_ID;
            case LATTICE_POLE -> PoleDesignCatalog.LATTICE_STEEL_TOWER_ID;
            case LATTICE -> TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID;
            case HEAVY_LATTICE -> TowerFamilyDesignPresets.HV_TRANSMISSION_SUSPENSION_ID;
            case TRIPLE_ARM -> TowerFamilyDesignPresets.TRIPLE_ARM_SUSPENSION_ID;
            case CUP_TOWER -> TowerFamilyDesignPresets.CUP_TOWER_SUSPENSION_ID;
            case MEGA_LATTICE -> TowerFamilyDesignPresets.MEGA_LATTICE_SUSPENSION_ID;
            case HEAVY_DOUBLE_CIRCUIT -> TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID;
            case INDUSTRIAL_PORTAL -> TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_SUSPENSION_ID;
            case MONSTER_PYLON -> TowerFamilyDesignPresets.MONSTER_PYLON_SUSPENSION_ID;
            case ADAPTIVE -> TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID;
            case TAPERED -> PoleDesignCatalog.TAPERED_LATTICE_TOWER_ID;
            case COPPER -> PoleDesignCatalog.FANTASY_COPPER_POLE_ID;
            case JAPANESE -> PoleDesignCatalog.JAPANESE_STREET_POLE_ID;
            case WASTELAND_WIND -> PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID;
            case OLD_EUROPEAN -> PoleDesignCatalog.OLD_EUROPEAN_POLE_ID;
            case STEAMPUNK -> PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID;
            case MODERN_HV_GLASS -> PoleDesignCatalog.MODERN_HV_GLASS_TOWER_ID;
            case SUBURBAN_LAMP -> PoleDesignCatalog.SUBURBAN_LAMP_POLE_ID;
            case ABANDONED -> PoleDesignCatalog.ABANDONED_POLE_ID;
            case RUSTIC -> PoleDesignCatalog.RUSTIC_WOOD_POLE_ID;
        };
    }

    /**
     * 分档格构族：卡片/Quick Tune 用高档代表塔（与 Smart Towers 大档距一致），
     * 不用族级 classic 参数化默认（否则预览偏宽、落地偏瘦）。
     */
    private static PoleDesign gradedFamilyPreviewDesign(PowerLineStyleDefinition definition) {
        if (definition == null
                || !TowerFamily.GRADED_LATTICE_3_PHASE_ID.equals(definition.getTowerFamilyId())) {
            return null;
        }
        PoleDesign compiled = PowerLineStyleParametricCatalog.compileRepresentative(
            TowerGeneratorConfig.parametricTripleArm(TowerParameterSet.tripleArmDefaults()));
        if (compiled != null) {
            return compiled;
        }
        return resolvePreviewDesign(TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID);
    }

    public static boolean previewAlignsWithApply(PowerLineStylePreset preset) {
        if (preset == null) {
            return false;
        }
        if (preset.getTowerFamilyId() != null && !preset.getTowerFamilyId().isBlank()) {
            TowerFamily family = TowerFamilyCatalog.findBuiltin(preset.getTowerFamilyId());
            if (family == null) {
                return false;
            }
            String previewId = primaryPreviewDesignId(preset);
            return family.getDesignId(com.plot.plugin.powerline.model.TowerRole.SUSPENSION).equals(previewId)
                || TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID.equals(previewId)
                || TowerFamilyDesignPresets.LATTICE_SUSPENSION_MEDIUM_ID.equals(previewId)
                || TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID.equals(previewId)
                || TowerFamilyDesignPresets.HV_TRANSMISSION_SUSPENSION_ID.equals(previewId)
                || TowerFamilyDesignPresets.TRIPLE_ARM_SUSPENSION_ID.equals(previewId)
                || TowerFamilyDesignPresets.CUP_TOWER_SUSPENSION_ID.equals(previewId)
                || TowerFamilyDesignPresets.MEGA_LATTICE_SUSPENSION_ID.equals(previewId)
                || TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID.equals(previewId)
                || TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_SUSPENSION_ID.equals(previewId)
                || TowerFamilyDesignPresets.MONSTER_PYLON_SUSPENSION_ID.equals(previewId);
        }
        String applyId = preset.getPoleDesignId();
        String previewId = primaryPreviewDesignId(preset);
        return applyId != null && applyId.equals(previewId);
    }
}
