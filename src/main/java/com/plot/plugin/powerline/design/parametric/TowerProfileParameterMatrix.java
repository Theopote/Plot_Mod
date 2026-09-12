package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Profile × MIN/DEFAULT/MAX parameter samples for visual QA and regression tests.
 * Does not alter resolver logic — only enumerates profile range corners.
 */
public final class TowerProfileParameterMatrix {
    public enum SampleKind {
        MIN,
        DEFAULT,
        MAX
    }

    public record ProfileEntry(String label, String profileId, Supplier<TowerParameterSet> defaultsSupplier) {
    }

    public record Sample(String label, SampleKind kind, TowerParameterProfile profile, TowerParameterSet parameters) {
        public String displayName() {
            return label + " / " + kind.name();
        }
    }

    private static final List<ProfileEntry> PROFILES = List.of(
        new ProfileEntry("Small", TowerParameterProfiles.SMALL_LATTICE_ID, TowerParameterSet::smallLatticeDefaults),
        new ProfileEntry("Classic", TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID, TowerParameterSet::classicDefaults),
        new ProfileEntry("Triple", TowerParameterProfiles.TRIPLE_ARM_ID, TowerParameterSet::tripleArmDefaults),
        new ProfileEntry("Cup", TowerParameterProfiles.CUP_ID, TowerParameterSet::cupDefaults),
        new ProfileEntry("Heavy", TowerParameterProfiles.HEAVY_ID, TowerParameterSet::heavyDefaults),
        new ProfileEntry("Mega", TowerParameterProfiles.MEGA_ID, TowerParameterSet::megaDefaults),
        new ProfileEntry("Portal", TowerParameterProfiles.PORTAL_ID, TowerParameterSet::portalDefaults),
        new ProfileEntry("Drum", TowerParameterProfiles.DRUM_ID, TowerParameterSet::drumDefaults),
        new ProfileEntry("UHV", TowerParameterProfiles.UHV_ID, TowerParameterSet::uhvDefaults),
        new ProfileEntry("Steampunk", TowerParameterProfiles.STEAMPUNK_ID, TowerParameterSet::steampunkDefaults),
        new ProfileEntry(
            "Modern HV Glass",
            TowerParameterProfiles.MODERN_HV_GLASS_ID,
            TowerParameterSet::modernHvGlassDefaults));

    private TowerProfileParameterMatrix() {
    }

    public static List<ProfileEntry> profiles() {
        return PROFILES;
    }

    public static List<Sample> allSamples() {
        List<Sample> samples = new ArrayList<>(PROFILES.size() * SampleKind.values().length);
        for (ProfileEntry entry : PROFILES) {
            TowerParameterProfile profile = TowerParameterProfiles.find(entry.profileId())
                .orElseThrow(() -> new IllegalStateException("Missing profile: " + entry.profileId()));
            TowerParameterSet defaults = entry.defaultsSupplier().get();
            for (SampleKind kind : SampleKind.values()) {
                samples.add(new Sample(entry.label(), kind, profile, parametersFor(kind, profile, defaults)));
            }
        }
        return samples;
    }

    public static List<Sample> samplesFor(String profileLabel) {
        return allSamples().stream().filter(sample -> sample.label().equals(profileLabel)).toList();
    }

    public static TowerConstraintResult resolve(Sample sample, TowerBuildEnvelope envelope) {
        return TowerParametricDesignFactory.resolveProfile(sample.profile(), sample.parameters(), envelope);
    }

    public static PoleDesign compile(Sample sample) {
        return TowerParametricDesignFactory.compileProfile(sample.profile(), sample.parameters(), null);
    }

    public static PoleDesign compile(Sample sample, TowerBuildEnvelope envelope) {
        return TowerParametricDesignFactory.compileProfile(sample.profile(), sample.parameters(), envelope);
    }

    static TowerParameterSet parametersFor(
            SampleKind kind,
            TowerParameterProfile profile,
            TowerParameterSet defaults) {
        double height = scalar(kind, profile.heightRange(), defaults.height());
        double baseWidth = scalar(kind, profile.baseWidthRange(), defaults.baseWidth());
        double armSpan = scalar(kind, profile.armSpanRange(), defaults.armSpan());
        double depthScale = scalar(kind, profile.depthScaleRange(), defaults.depthScale());
        return new TowerParameterSet(
            height,
            baseWidth,
            armSpan,
            depthScale,
            defaults.waistRatio(),
            defaults.armLevelScales(),
            defaults.density());
    }

    private static double scalar(SampleKind kind, ParameterRange range, double defaultValue) {
        return switch (kind) {
            case MIN -> range.min();
            case MAX -> range.max();
            case DEFAULT -> range.clamp(defaultValue);
        };
    }
}
