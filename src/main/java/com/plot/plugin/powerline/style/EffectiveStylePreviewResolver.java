package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;

/**
 * 解析线路当前真正生效的风格预览（Quick Tune / Overrides 之后），
 * 而不是 base preset 的默认代表设计。
 */
public final class EffectiveStylePreviewResolver {
    private EffectiveStylePreviewResolver() {
    }

    /**
     * @return 预览用深拷贝设计（已应用杆材覆盖）；无法解析时返回 {@code null}
     */
    public static EffectiveStylePreview resolve(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PoleDesignResolver resolver) {
        if (line == null) {
            return null;
        }
        PoleDesign source = resolveSourceDesign(line, base, resolver);
        PoleDesign preview = source != null ? source.copy() : synthesizeLegacyPreview(line);
        if (preview == null) {
            return null;
        }
        applyPoleMaterialOverride(preview, line.getPoleMaterial());
        return new EffectiveStylePreview(
            preview,
            copyMix(line.getPoleMaterial()),
            copyMix(line.getWireMaterial()),
            copyMix(line.getTopWireMaterial()),
            base);
    }

    static PoleDesign resolveSourceDesign(
            PowerLineFootprint line,
            PowerLineStylePreset base,
            PoleDesignResolver resolver) {
        if (line.hasParametricTowerConfig()) {
            PoleDesign parametric = PowerLineStyleParametricCatalog.compileRepresentative(
                line.getParametricTowerConfig());
            if (parametric != null) {
                return parametric;
            }
        }
        if (line.hasPoleDesign()) {
            PoleDesign fromLine = findDesign(line.getPoleDesignId(), resolver);
            if (fromLine != null) {
                return fromLine;
            }
        }
        if (line.hasTowerFamily()) {
            PoleDesign fromFamily = resolveFamilyRepresentative(line.getTowerFamilyId(), resolver);
            if (fromFamily != null) {
                return fromFamily;
            }
        }
        if (base != null) {
            String previewId = PowerLineStylePreviewBinding.primaryPreviewDesignId(base);
            PoleDesign fromBase = findDesign(previewId, resolver);
            if (fromBase != null) {
                return fromBase;
            }
            if (base.getPoleDesignId() != null) {
                return findDesign(base.getPoleDesignId(), resolver);
            }
        }
        return null;
    }

    private static PoleDesign resolveFamilyRepresentative(String familyId, PoleDesignResolver resolver) {
        TowerFamily family = TowerFamilyCatalog.findBuiltin(familyId);
        if (family == null) {
            return null;
        }
        String designId = family.getDesignId(TowerRole.SUSPENSION);
        if (designId == null) {
            designId = family.getDesignId(TowerRole.SPECIAL);
        }
        return findDesign(designId, resolver);
    }

    private static PoleDesign findDesign(String designId, PoleDesignResolver resolver) {
        if (designId == null || designId.isBlank()) {
            return null;
        }
        if (resolver != null) {
            PoleDesign resolved = resolver.find(designId);
            if (resolved != null) {
                return resolved;
            }
        }
        PoleDesign builtin = PoleDesignCatalog.findBuiltin(designId);
        if (builtin != null) {
            return builtin;
        }
        return TowerFamilyCatalog.familyDesigns().stream()
            .filter(design -> designId.equals(design.getId()))
            .findFirst()
            .orElse(null);
    }

    private static PoleDesign synthesizeLegacyPreview(PowerLineFootprint line) {
        int height = Math.max(1, (int) Math.round(line.getPoleHeight()));
        MaterialMix material = line.getPoleMaterial() != null
            ? line.getPoleMaterial().copy()
            : MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
        PoleDesign design = new PoleDesign("_effective_preview", "Preview");
        design.getLayers().add(new PoleLayer(PoleLayer.Shape.COLUMN, height, material));
        return design;
    }

    /** 立面预览：COLUMN（及塔体主材）跟随线路当前杆材 override。 */
    static void applyPoleMaterialOverride(PoleDesign design, MaterialMix poleMaterial) {
        if (design == null || poleMaterial == null) {
            return;
        }
        MaterialMix mix = poleMaterial.copy();
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.COLUMN) {
                layer.setMaterial(mix.copy());
            }
        }
        if (design.hasTowerStructure()) {
            design.getTowerStructure().setPrimaryMaterial(mix.copy());
        }
    }

    private static MaterialMix copyMix(MaterialMix mix) {
        return mix != null ? mix.copy() : null;
    }
}
