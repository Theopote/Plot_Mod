package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.utils.PlotI18n;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** UI catalog mapping profile ids to display labels and default parameters. */
public final class TowerProfileUiCatalog {
    public record ProfileOption(String id, String labelKey, Supplier<TowerParameterSet> defaults) {
    }

    private static final List<ProfileOption> OPTIONS = List.of(
        new ProfileOption(
            TowerParameterProfiles.SMALL_LATTICE_ID,
            "plugin.powerline.design.parametric_profile_small_lattice",
            TowerParameterSet::smallLatticeDefaults),
        new ProfileOption(
            TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID,
            "plugin.powerline.design.parametric_profile_classic",
            TowerParameterSet::classicDefaults),
        new ProfileOption(
            TowerParameterProfiles.TRIPLE_ARM_ID,
            "plugin.powerline.design.parametric_profile_triple_arm",
            TowerParameterSet::tripleArmDefaults),
        new ProfileOption(
            TowerParameterProfiles.CUP_ID,
            "plugin.powerline.design.parametric_profile_cup",
            TowerParameterSet::cupDefaults),
        new ProfileOption(
            TowerParameterProfiles.HEAVY_ID,
            "plugin.powerline.design.parametric_profile_heavy",
            TowerParameterSet::heavyDefaults),
        new ProfileOption(
            TowerParameterProfiles.MEGA_ID,
            "plugin.powerline.design.parametric_profile_mega",
            TowerParameterSet::megaDefaults),
        new ProfileOption(
            TowerParameterProfiles.PORTAL_ID,
            "plugin.powerline.design.parametric_profile_portal",
            TowerParameterSet::portalDefaults),
        new ProfileOption(
            TowerParameterProfiles.DRUM_ID,
            "plugin.powerline.design.parametric_profile_drum",
            TowerParameterSet::drumDefaults),
        new ProfileOption(
            TowerParameterProfiles.UHV_ID,
            "plugin.powerline.design.parametric_profile_uhv",
            TowerParameterSet::uhvDefaults),
        new ProfileOption(
            TowerParameterProfiles.STEAMPUNK_ID,
            "plugin.powerline.design.parametric_profile_steampunk",
            TowerParameterSet::steampunkDefaults),
        new ProfileOption(
            TowerParameterProfiles.MODERN_HV_GLASS_ID,
            "plugin.powerline.design.parametric_profile_modern_hv_glass",
            TowerParameterSet::modernHvGlassDefaults));

    private TowerProfileUiCatalog() {
    }

    public static List<ProfileOption> all() {
        return OPTIONS;
    }

    public static Optional<ProfileOption> find(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Optional.empty();
        }
        return OPTIONS.stream().filter(option -> option.id().equals(profileId)).findFirst();
    }

    public static String labelFor(String profileId) {
        return find(profileId)
            .map(option -> PlotI18n.tr(stripProfilePrefix(option.labelKey())))
            .orElseGet(() -> PlotI18n.tr("plugin.powerline.design.parametric_profile_classic"));
    }

    public static int indexOf(String profileId) {
        for (int i = 0; i < OPTIONS.size(); i++) {
            if (OPTIONS.get(i).id().equals(profileId)) {
                return i;
            }
        }
        return 1;
    }

    private static String stripProfilePrefix(String labelKey) {
        return labelKey;
    }
}
