package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerBuildEnvelope;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.engineering.TowerEngineeringMetadata;
import com.plot.plugin.powerline.model.PowerLineFootprint;

import java.util.ArrayList;
import java.util.List;

/** 将线路 Style 级参数化配置应用到解析后的杆塔设计。 */
public final class ParametricStyleTowerApplicator {
    private ParametricStyleTowerApplicator() {
    }

    public static PoleDesign apply(PoleDesign source, TowerGeneratorConfig styleConfig) {
        return apply(source, styleConfig, null, null);
    }

    public static PoleDesign apply(
            PoleDesign source,
            TowerGeneratorConfig styleConfig,
            TowerBuildEnvelope envelope) {
        return apply(source, styleConfig, envelope, null);
    }

    public static PoleDesign apply(
            PoleDesign source,
            TowerGeneratorConfig styleConfig,
            TowerBuildEnvelope envelope,
            PowerLineFootprint footprint) {
        if (source == null || styleConfig == null || !styleConfig.isParametric()) {
            return source;
        }
        if (footprint != null && footprint.hasTowerFamily() && TowerFamilyRoleParametricCatalog.isFamilyRoleDesign(source)) {
            return applyToFamilyRole(source, styleConfig, envelope, footprint);
        }
        return applyToGenericDesign(source, styleConfig, envelope);
    }

    private static PoleDesign applyToFamilyRole(
            PoleDesign source,
            TowerGeneratorConfig styleConfig,
            TowerBuildEnvelope envelope,
            PowerLineFootprint footprint) {
        TowerGeneratorConfig familyDefault = PowerLineStyleParametricCatalog.forTowerFamilyId(
            footprint.getTowerFamilyId());
        TowerGeneratorConfig roleSeed = TowerFamilyRoleParametricCatalog.seedForRoleDesign(source);
        if (familyDefault == null || roleSeed == null) {
            return applyToGenericDesign(source, styleConfig, envelope);
        }
        if (PowerLineStyleParametricCatalog.parametersMatch(familyDefault, styleConfig)) {
            return source.copy();
        }

        TowerParameterSet merged = TowerFamilyRoleParametricCatalog.mergeTunedParameters(
            roleSeed.parameters(),
            familyDefault.parameters(),
            styleConfig.parameters());
        TowerGeneratorConfig mergedConfig = new TowerGeneratorConfig(
            roleSeed.profileId(),
            roleSeed.mode(),
            merged);

        PoleDesign design = source.copy();
        design.setGeneratorConfig(mergedConfig.copy());
        TowerParametricEditor.recompile(design, envelope, roleSeed.profileId());
        preserveRoleIdentity(source, design);
        return design;
    }

    private static PoleDesign applyToGenericDesign(
            PoleDesign source,
            TowerGeneratorConfig styleConfig,
            TowerBuildEnvelope envelope) {
        String previousProfileId = source.getGeneratorConfig() != null && source.isParametricMode()
            ? source.getGeneratorConfig().profileId()
            : null;
        PoleDesign design = source.copy();
        design.setGeneratorConfig(styleConfig.copy());
        TowerParametricEditor.recompile(design, envelope, previousProfileId);
        return design;
    }

    private static void preserveRoleIdentity(PoleDesign source, PoleDesign design) {
        if (source.getAttachments() != null && !source.getAttachments().isEmpty()) {
            List<ConductorAttachment> attachments = new ArrayList<>(source.getAttachments().size());
            for (ConductorAttachment attachment : source.getAttachments()) {
                if (attachment != null) {
                    attachments.add(attachment.copy());
                }
            }
            design.setAttachments(attachments);
        }
        TowerEngineeringMetadata metadata = source.getEngineeringMetadata();
        if (metadata != null) {
            design.setEngineeringMetadata(metadata.copy());
        }
    }
}
