package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.design.ConductorArrangement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 用户另存为的造型模板（工程级共享 {@link PoleDesign}，非线路私有实例）。 */
public final class UserPoleDesignTemplateCatalog {
    public static final String PRESET_ID_PREFIX = "template/";

    private UserPoleDesignTemplateCatalog() {
    }

    public static boolean isUserTemplatePresetId(String presetId) {
        return presetId != null && presetId.startsWith(PRESET_ID_PREFIX);
    }

    public static String presetIdFor(String designId) {
        if (designId == null || designId.isBlank()) {
            return null;
        }
        return PRESET_ID_PREFIX + designId;
    }

    public static String designIdFromPresetId(String presetId) {
        if (!isUserTemplatePresetId(presetId)) {
            return null;
        }
        return presetId.substring(PRESET_ID_PREFIX.length());
    }

    public static List<PoleDesign> listTemplates(PowerLineDesignProject project) {
        if (project == null) {
            return List.of();
        }
        List<PoleDesign> templates = new ArrayList<>();
        for (PoleDesign design : project.getDesigns().values()) {
            if (design == null || LinePoleDesignOverrides.isLineInstanceDesignId(design.getId())) {
                continue;
            }
            templates.add(design);
        }
        templates.sort(Comparator.comparing(PoleDesign::getName, String.CASE_INSENSITIVE_ORDER));
        return templates;
    }

    public static PowerLineStylePreset resolvePreset(
            PowerLineFootprint line,
            PowerLineDesignProject project) {
        if (line == null || project == null) {
            return null;
        }
        String presetId = line.getStylePresetId();
        if (!isUserTemplatePresetId(presetId)) {
            return null;
        }
        PoleDesign design = project.getDesign(designIdFromPresetId(presetId));
        return design != null ? toPreset(design, line) : null;
    }

    public static PowerLineStylePreset toPreset(PoleDesign design) {
        return toPreset(design, null);
    }

    public static PowerLineStylePreset toPreset(PoleDesign design, PowerLineFootprint lineContext) {
        if (design == null) {
            return null;
        }
        ConductorArrangement arrangement = ConductorArrangement.fromAttachments(
            "user-template:" + design.getId(),
            design.getAttachments());
        MaterialMix wireMaterial = lineContext != null && lineContext.getWireMaterial() != null
            ? lineContext.getWireMaterial()
            : MaterialMix.single("minecraft:iron_bars");
        MaterialMix poleMaterial = lineContext != null && lineContext.getPoleMaterial() != null
            ? lineContext.getPoleMaterial()
            : MaterialMix.single("minecraft:oak_fence");
        MaterialMix topWireMaterial = lineContext != null && lineContext.getTopWireMaterial() != null
            ? lineContext.getTopWireMaterial()
            : MaterialMix.single("minecraft:chain");
        PowerLineStyleDefinition definition = new PowerLineStyleDefinition(
            previewKindFor(design),
            null,
            design.getId(),
            wireMaterial,
            poleMaterial,
            topWireMaterial,
            PowerLineUiPresets.WireSag.NATURAL,
            PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH,
            arrangement,
            PoleSpacingProfile.streetWood(),
            design.getGeneratorConfig());
        return new PowerLineStylePreset(
            presetIdFor(design.getId()),
            design.getName(),
            StyleCategory.UTILITY,
            definition);
    }

    public static String displayLabel(PowerLineStylePreset preset) {
        if (preset == null) {
            return "";
        }
        if (isUserTemplatePresetId(preset.getId())) {
            return preset.getLabelKey();
        }
        return PlotI18n.tr(preset.getLabelKey());
    }

    public static boolean isTemplateSelected(PowerLineFootprint line, PoleDesign design) {
        if (line == null || design == null) {
            return false;
        }
        return Objects.equals(presetIdFor(design.getId()), line.getStylePresetId());
    }

    public static int attachmentChannelCount(PoleDesign design) {
        if (design == null || !design.hasEnabledAttachments()) {
            return 1;
        }
        int channels = 0;
        for (var attachment : design.getAttachments()) {
            if (attachment != null && attachment.isEnabled()) {
                channels++;
            }
        }
        return channels > 0 ? channels : 1;
    }

    private static PowerLineStylePreset.StylePreviewKind previewKindFor(PoleDesign design) {
        if (design != null && design.hasTowerStructure()) {
            return PowerLineStylePreset.StylePreviewKind.LATTICE;
        }
        return PowerLineStylePreset.StylePreviewKind.WOOD;
    }
}
