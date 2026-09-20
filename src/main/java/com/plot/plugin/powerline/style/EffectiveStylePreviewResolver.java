package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
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
        EffectivePoleDesignResolver.applyPoleMaterialOverride(preview, line.getPoleMaterial());
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
        if (line.hasTowerFamily()) {
            PoleDesign fromFamily = resolveFamilyBuildRepresentative(line, resolver);
            if (fromFamily != null) {
                return applyLineParametric(line, fromFamily);
            }
        }
        if (line.hasPoleDesign()) {
            PoleDesign fromLine = findDesign(line.getPoleDesignId(), resolver);
            if (fromLine != null) {
                return applyLineParametric(line, fromLine);
            }
        }
        if (line.hasParametricTowerConfig()) {
            PoleDesign parametric = PowerLineStyleParametricCatalog.compileRepresentative(
                line.getParametricTowerConfig());
            if (parametric != null) {
                return parametric;
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

    private static PoleDesign applyLineParametric(PowerLineFootprint line, PoleDesign source) {
        if (source == null || !line.hasParametricTowerConfig()) {
            return source;
        }
        return ParametricStyleTowerApplicator.apply(
            source,
            line.getParametricTowerConfig(),
            null,
            line);
    }

    /**
     * 与落地生成一致：按线路档距选择分档悬垂塔型，而非族内默认小号代表塔。
     */
    private static PoleDesign resolveFamilyBuildRepresentative(
            PowerLineFootprint line,
            PoleDesignResolver resolver) {
        if (TowerFamily.GRADED_LATTICE_3_PHASE_ID.equals(line.getTowerFamilyId())) {
            PoleDesign tall = findDesign(TowerFamilyDesignPresets.LATTICE_SUSPENSION_TALL_ID, resolver);
            if (tall != null) {
                return tall;
            }
        }
        Vec2d anchor = line.getPathPoints().isEmpty()
            ? new Vec2d(0, 0)
            : line.getPathPoints().getFirst();
        PowerPoleSite site = new PowerPoleSite(anchor);
        site.setRole(TowerRole.SUSPENSION);
        double spanHint = Math.max(1.0, line.getMaxPoleSpacing());
        PoleDesignAssignmentResolver assignments = new PoleDesignAssignmentResolver(
            resolver,
            new TowerFamilyResolver());
        PoleDesignAssignmentResolver.AssignmentResult result = assignments.resolve(site, line, spanHint);
        return result.design();
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
        design.addLayer(new PoleLayer(PoleLayer.Shape.COLUMN, height, material));
        return design;
    }

    private static MaterialMix copyMix(MaterialMix mix) {
        return mix != null ? mix.copy() : null;
    }
}
