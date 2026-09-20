package com.plot.plugin.powerline.design.family;

import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.List;

/** 按杆塔角色与跨度选择视觉塔型（不走工程评分）。 */
public final class VisualTowerResolver {
    static final double MEDIUM_SPAN_THRESHOLD_BLOCKS = 35.0;
    static final double TALL_SPAN_THRESHOLD_BLOCKS = 55.0;

    private VisualTowerResolver() {
    }

    public static String resolveDesignId(
            PowerPoleSite site,
            TowerFamily family,
            double maxAdjacentSpanBlocks) {
        if (site == null || family == null) {
            return null;
        }
        TowerRole role = site.getRole();
        if (role == TowerRole.SUSPENSION && hasGradedSuspensionVariants(family)) {
            return resolveGradedSuspension(family, maxAdjacentSpanBlocks);
        }
        return family.getDesignId(role);
    }

    public static double maxAdjacentSpanBlocks(List<PowerPoleSite> sites, int index, boolean closedLoop) {
        return maxAdjacentSpanBlocks(sites, index, closedLoop, 0.0);
    }

    public static double maxAdjacentSpanBlocks(
            List<PowerPoleSite> sites,
            int index,
            boolean closedLoop,
            double perimeterBlocks) {
        if (sites == null || sites.isEmpty() || index < 0 || index >= sites.size()) {
            return 0.0;
        }
        double incoming = 0.0;
        double outgoing = 0.0;
        if (closedLoop) {
            int count = sites.size();
            int previousIndex = (index - 1 + count) % count;
            int nextIndex = (index + 1) % count;
            incoming = PowerPoleLayoutUtils.worldSpanBlocks(
                sites.get(previousIndex),
                sites.get(index),
                perimeterBlocks,
                true);
            outgoing = PowerPoleLayoutUtils.worldSpanBlocks(
                sites.get(index),
                sites.get(nextIndex),
                perimeterBlocks,
                true);
        } else {
            if (index > 0) {
                incoming = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(index - 1), sites.get(index));
            }
            if (index < sites.size() - 1) {
                outgoing = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(index), sites.get(index + 1));
            }
        }
        return Math.max(incoming, outgoing);
    }

    public static boolean hasGradedSuspensionVariants(TowerFamily family) {
        return family != null && family.hasSuspensionVariants();
    }

    public static String nextLargerGradedDesign(TowerFamily family, String currentDesignId) {
        if (currentDesignId == null || !hasGradedSuspensionVariants(family)) {
            return null;
        }
        SuspensionVariant current = family.suspensionVariantForDesignId(currentDesignId);
        SuspensionVariant next = current != null ? current.nextLarger() : null;
        return next != null ? family.getSuspensionVariantDesignId(next) : null;
    }

    private static String resolveGradedSuspension(TowerFamily family, double maxAdjacentSpanBlocks) {
        return family.getSuspensionVariantDesignId(resolveSuspensionVariant(maxAdjacentSpanBlocks));
    }

    static SuspensionVariant resolveSuspensionVariant(double maxAdjacentSpanBlocks) {
        if (maxAdjacentSpanBlocks > TALL_SPAN_THRESHOLD_BLOCKS) {
            return SuspensionVariant.LARGE;
        }
        if (maxAdjacentSpanBlocks > MEDIUM_SPAN_THRESHOLD_BLOCKS) {
            return SuspensionVariant.MEDIUM;
        }
        return SuspensionVariant.SMALL;
    }

}
