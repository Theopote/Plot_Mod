package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterProfiles;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.utils.PlotI18n;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** UI catalog mapping profile ids to display labels and enable actions. */
public final class TowerProfileUiCatalog {
    public record ProfileOption(String id, String labelKey, Consumer<PoleDesign> enabler) {
    }

    private static final List<ProfileOption> OPTIONS = List.of(
        new ProfileOption(
            TowerParameterProfiles.SMALL_LATTICE_ID,
            "plugin.powerline.design.parametric_profile_small_lattice",
            design -> TowerParametricEditor.enableParametricSmallLattice(
                design, TowerParameterSet.smallLatticeDefaults())),
        new ProfileOption(
            TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID,
            "plugin.powerline.design.parametric_profile_classic",
            design -> TowerParametricEditor.enableParametricClassic(
                design, TowerParameterSet.classicDefaults())),
        new ProfileOption(
            TowerParameterProfiles.TRIPLE_ARM_ID,
            "plugin.powerline.design.parametric_profile_triple_arm",
            design -> TowerParametricEditor.enableParametricTripleArm(
                design, TowerParameterSet.tripleArmDefaults())),
        new ProfileOption(
            TowerParameterProfiles.CUP_ID,
            "plugin.powerline.design.parametric_profile_cup",
            design -> TowerParametricEditor.enableParametricCup(
                design, TowerParameterSet.cupDefaults())),
        new ProfileOption(
            TowerParameterProfiles.HEAVY_ID,
            "plugin.powerline.design.parametric_profile_heavy",
            design -> TowerParametricEditor.enableParametricHeavy(
                design, TowerParameterSet.heavyDefaults())),
        new ProfileOption(
            TowerParameterProfiles.MEGA_ID,
            "plugin.powerline.design.parametric_profile_mega",
            design -> TowerParametricEditor.enableParametricMega(
                design, TowerParameterSet.megaDefaults())),
        new ProfileOption(
            TowerParameterProfiles.PORTAL_ID,
            "plugin.powerline.design.parametric_profile_portal",
            design -> TowerParametricEditor.enableParametricPortal(
                design, TowerParameterSet.portalDefaults())),
        new ProfileOption(
            TowerParameterProfiles.DRUM_ID,
            "plugin.powerline.design.parametric_profile_drum",
            design -> TowerParametricEditor.enableParametricDrum(
                design, TowerParameterSet.drumDefaults())),
        new ProfileOption(
            TowerParameterProfiles.UHV_ID,
            "plugin.powerline.design.parametric_profile_uhv",
            design -> TowerParametricEditor.enableParametricUhv(
                design, TowerParameterSet.uhvDefaults())),
        new ProfileOption(
            TowerParameterProfiles.STEAMPUNK_ID,
            "plugin.powerline.design.parametric_profile_steampunk",
            design -> TowerParametricEditor.enableParametricSteampunk(
                design, TowerParameterSet.steampunkDefaults())),
        new ProfileOption(
            TowerParameterProfiles.MODERN_HV_GLASS_ID,
            "plugin.powerline.design.parametric_profile_modern_hv_glass",
            design -> TowerParametricEditor.enableParametricModernHvGlass(
                design, TowerParameterSet.modernHvGlassDefaults())));

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
