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
        if (sites == null || sites.isEmpty() || index < 0 || index >= sites.size()) {
            return 0.0;
        }
        double incoming = 0.0;
        double outgoing = 0.0;
        if (closedLoop) {
            int count = sites.size();
            int previousIndex = (index - 1 + count) % count;
            int nextIndex = (index + 1) % count;
            incoming = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(previousIndex), sites.get(index));
            outgoing = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(index), sites.get(nextIndex));
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
        String small = family.getDesignId(TowerRole.SUSPENSION);
        String medium = family.getDesignId(TowerRole.SPECIAL);
        String tall = family.getDesignId(TowerRole.DEAD_END);
        return small != null
            && medium != null
            && tall != null
            && !small.equals(medium)
            && !medium.equals(tall);
    }

    public static String nextLargerGradedDesign(TowerFamily family, String currentDesignId) {
        if (!hasGradedSuspensionVariants(family) || currentDesignId == null) {
            return null;
        }
        String small = family.getDesignId(TowerRole.SUSPENSION);
        String medium = family.getDesignId(TowerRole.SPECIAL);
        String tall = family.getDesignId(TowerRole.DEAD_END);
        if (currentDesignId.equals(small)) {
            return medium;
        }
        if (currentDesignId.equals(medium)) {
            return tall;
        }
        return null;
    }

    private static String resolveGradedSuspension(TowerFamily family, double maxAdjacentSpanBlocks) {
        String small = family.getDesignId(TowerRole.SUSPENSION);
        String medium = family.getDesignId(TowerRole.SPECIAL);
        String tall = family.getDesignId(TowerRole.DEAD_END);
        if (maxAdjacentSpanBlocks > TALL_SPAN_THRESHOLD_BLOCKS) {
            return tall;
        }
        if (maxAdjacentSpanBlocks > MEDIUM_SPAN_THRESHOLD_BLOCKS) {
            return medium;
        }
        return small;
    }
}
