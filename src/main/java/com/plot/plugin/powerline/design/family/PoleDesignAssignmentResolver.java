package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.PowerLineGenerationI18n;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.List;

/** 集中解析每座杆塔应使用的 PoleDesign。 */
public final class PoleDesignAssignmentResolver {
    private final PoleDesignResolver designResolver;
    private final TowerFamilyResolver familyResolver;

    public PoleDesignAssignmentResolver(
            PoleDesignResolver designResolver,
            TowerFamilyResolver familyResolver) {
        this.designResolver = designResolver;
        this.familyResolver = familyResolver != null
            ? familyResolver
            : new TowerFamilyResolver();
    }

    public record AssignmentResult(PoleDesign design, String resolvedDesignId, List<String> warnings) {
        public static AssignmentResult empty() {
            return new AssignmentResult(null, null, List.of());
        }
    }

    public AssignmentResult resolve(PowerPoleSite site, PowerLineFootprint footprint) {
        return resolve(site, footprint, 0.0);
    }

    public AssignmentResult resolve(
            PowerPoleSite site,
            PowerLineFootprint footprint,
            double maxAdjacentSpanBlocks) {
        List<String> warnings = new ArrayList<>();
        if (site == null || footprint == null) {
            return AssignmentResult.empty();
        }

        String overrideId = site.getPoleDesignOverrideId();
        if (overrideId != null && !overrideId.isBlank()) {
            PoleDesign design = designResolver.find(overrideId);
            if (design == null) {
                warnings.add(PowerLineGenerationI18n.poleDesignOverrideNotFound(overrideId));
            } else {
                return new AssignmentResult(design, overrideId, warnings);
            }
        }

        if (footprint.hasTowerFamily()) {
            TowerFamily family = familyResolver.find(footprint.getTowerFamilyId());
            if (family == null) {
                warnings.add(PowerLineGenerationI18n.towerFamilyNotFound(footprint.getTowerFamilyId()));
            } else {
                AssignmentResult fromFamily = resolveFromFamily(
                    site,
                    family,
                    footprint,
                    maxAdjacentSpanBlocks,
                    warnings);
                if (fromFamily.design != null) {
                    return fromFamily;
                }
            }
        }

        if (footprint.hasPoleDesign()) {
            String id = footprint.getPoleDesignId();
            PoleDesign design = designResolver.find(id);
            if (design == null) {
                warnings.add(PowerLineGenerationI18n.linePoleDesignNotFound(id));
                return new AssignmentResult(null, id, warnings);
            }
            return new AssignmentResult(design, id, warnings);
        }

        return AssignmentResult.empty();
    }

    private AssignmentResult resolveFromFamily(
            PowerPoleSite site,
            TowerFamily family,
            PowerLineFootprint footprint,
            double maxAdjacentSpanBlocks,
            List<String> warnings) {
        TowerRole role = site.getRole();
        String designId = VisualTowerResolver.resolveDesignId(site, family, maxAdjacentSpanBlocks);
        if (designId == null || designId.isBlank()) {
            warnings.add(PowerLineGenerationI18n.noDesignForRole(role, family.getId()));
            designId = family.getDesignId(TowerRole.SUSPENSION);
        }
        if (designId == null || designId.isBlank()) {
            if (footprint.hasPoleDesign()) {
                designId = footprint.getPoleDesignId();
            }
        }
        if (designId == null || designId.isBlank()) {
            return AssignmentResult.empty();
        }
        PoleDesign design = designResolver.find(designId);
        if (design == null) {
            warnings.add(PowerLineGenerationI18n.familyRoleDesignNotFound(designId));
            return new AssignmentResult(null, designId, warnings);
        }
        return new AssignmentResult(design, designId, warnings);
    }
}
