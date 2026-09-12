package com.plot.plugin.powerline.design.parametric;

import java.util.ArrayList;
import java.util.List;

/**
 * 整条线路的多杆位世界包络：以各杆位 {@link TowerBuildEnvelope#availableLocalHeight()}
 * 的最小值作为线路级高度约束。
 */
public record TowerLineBuildEnvelope(
        List<TowerBuildEnvelope> siteEnvelopes,
        int limitingSiteIndex,
        double limitingAvailableHeight) {

    public TowerLineBuildEnvelope {
        siteEnvelopes = siteEnvelopes != null ? List.copyOf(siteEnvelopes) : List.of();
        if (siteEnvelopes.isEmpty()) {
            throw new IllegalArgumentException("siteEnvelopes must not be empty");
        }
        if (limitingSiteIndex < 0 || limitingSiteIndex >= siteEnvelopes.size()) {
            throw new IllegalArgumentException("limitingSiteIndex out of range");
        }
    }

    public int siteCount() {
        return siteEnvelopes.size();
    }

    public TowerBuildEnvelope limitingSiteEnvelope() {
        return siteEnvelopes.get(limitingSiteIndex);
    }

    public double maxAvailableHeight() {
        double max = siteEnvelopes.getFirst().availableLocalHeight();
        for (int i = 1; i < siteEnvelopes.size(); i++) {
            max = Math.max(max, siteEnvelopes.get(i).availableLocalHeight());
        }
        return max;
    }

    public TowerBuildEnvelope siteEnvelope(int siteIndex) {
        if (siteIndex < 0 || siteIndex >= siteEnvelopes.size()) {
            throw new IndexOutOfBoundsException("siteIndex out of range: " + siteIndex);
        }
        return siteEnvelopes.get(siteIndex);
    }

    /** 供参数化约束求解使用的合成包络（{@code availableLocalHeight == limitingAvailableHeight}）。 */
    public TowerBuildEnvelope constraintEnvelope() {
        TowerBuildEnvelope reference = siteEnvelopes.getFirst();
        return TowerBuildEnvelope.forLineHeightLimit(
            reference.worldBottomY(),
            reference.worldTopExclusiveY(),
            reference.topSafetyMargin(),
            limitingAvailableHeight);
    }

    public static TowerLineBuildEnvelope fromSiteEnvelopes(List<TowerBuildEnvelope> siteEnvelopes) {
        if (siteEnvelopes == null || siteEnvelopes.isEmpty()) {
            throw new IllegalArgumentException("siteEnvelopes must not be empty");
        }
        int limitingIndex = 0;
        double limitingAvailable = siteEnvelopes.getFirst().availableLocalHeight();
        for (int i = 1; i < siteEnvelopes.size(); i++) {
            double available = siteEnvelopes.get(i).availableLocalHeight();
            if (available < limitingAvailable) {
                limitingAvailable = available;
                limitingIndex = i;
            }
        }
        return new TowerLineBuildEnvelope(siteEnvelopes, limitingIndex, limitingAvailable);
    }

    public static TowerLineBuildEnvelope singleSite(TowerBuildEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope is required");
        }
        return new TowerLineBuildEnvelope(
            List.of(envelope),
            0,
            envelope.availableLocalHeight());
    }

    public static TowerLineBuildEnvelope minimumOf(List<TowerLineBuildEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            throw new IllegalArgumentException("envelopes must not be empty");
        }
        List<TowerBuildEnvelope> combinedSites = new ArrayList<>();
        for (TowerLineBuildEnvelope envelope : envelopes) {
            combinedSites.addAll(envelope.siteEnvelopes());
        }
        return fromSiteEnvelopes(combinedSites);
    }
}
