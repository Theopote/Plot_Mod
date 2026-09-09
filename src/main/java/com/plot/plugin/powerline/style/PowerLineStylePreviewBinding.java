package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;

/**
 * 风格卡片缩略图与预设配置的绑定关系（供一致性测试与验收对照）。
 * <p>
 * 逻辑与 {@link com.plot.plugin.powerline.ui.PowerLineStyleCardRenderer#drawPackPreview} 保持同步。
 */
public final class PowerLineStylePreviewBinding {
    private PowerLineStylePreviewBinding() {
    }

    public enum BindingKind {
        /** 缩略图主轮廓使用与 apply() 相同的杆塔设计 ID。 */
        POLE_DESIGN,
        /** 缩略图使用塔型族代表设计（非单一 apply 字段）。 */
        TOWER_FAMILY_REPRESENTATIVE,
        /** 缩略图使用正确杆塔设计，并叠加装饰性导线/配件（不影响几何主体）。 */
        POLE_WITH_DECORATIVE_OVERLAY
    }

    public static BindingKind bindingKind(PowerLineStylePreset preset) {
        if (preset == null) {
            return BindingKind.POLE_DESIGN;
        }
        return switch (preset.getPreviewKind()) {
            case LATTICE, HEAVY_LATTICE, MEGA_LATTICE, HEAVY_DOUBLE_CIRCUIT, INDUSTRIAL_PORTAL, MONSTER_PYLON,
                 ADAPTIVE -> BindingKind.TOWER_FAMILY_REPRESENTATIVE;
            case WOOD, MODERN_UTILITY, JAPANESE, WASTELAND_WIND, OLD_EUROPEAN, STEAMPUNK,
                 MODERN_HV_GLASS, SUBURBAN_LAMP, ABANDONED, RUSTIC -> BindingKind.POLE_WITH_DECORATIVE_OVERLAY;
            default -> BindingKind.POLE_DESIGN;
        };
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
            case MEGA_LATTICE -> TowerFamilyDesignPresets.MEGA_LATTICE_SUSPENSION_ID;
            case HEAVY_DOUBLE_CIRCUIT -> TowerFamilyDesignPresets.HEAVY_DOUBLE_CIRCUIT_SUSPENSION_ID;
            case INDUSTRIAL_PORTAL -> TowerFamilyDesignPresets.INDUSTRIAL_PORTAL_SUSPENSION_ID;
            case MONSTER_PYLON -> TowerFamilyDesignPresets.MONSTER_PYLON_SUSPENSION_ID;
            case ADAPTIVE -> TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID;
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
                || TowerFamilyDesignPresets.HV_TRANSMISSION_SUSPENSION_ID.equals(previewId)
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
