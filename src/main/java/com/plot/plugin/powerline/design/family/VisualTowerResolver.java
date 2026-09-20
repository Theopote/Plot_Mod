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
        if (family == null) {
            return false;
        }
        if (family.hasSuspensionVariants()) {
            return true;
        }
        return hasLegacyGradedSuspensionVariants(family);
    }

    public static String nextLargerGradedDesign(TowerFamily family, String currentDesignId) {
        if (currentDesignId == null || !hasGradedSuspensionVariants(family)) {
            return null;
        }
        if (family.hasSuspensionVariants()) {
            SuspensionVariant current = family.suspensionVariantForDesignId(currentDesignId);
            SuspensionVariant next = current != null ? current.nextLarger() : null;
            return next != null ? family.getSuspensionVariantDesignId(next) : null;
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
        if (family.hasSuspensionVariants()) {
            return family.getSuspensionVariantDesignId(resolveSuspensionVariant(maxAdjacentSpanBlocks));
        }
        return resolveLegacyGradedSuspension(family, maxAdjacentSpanBlocks);
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

    private static String resolveLegacyGradedSuspension(TowerFamily family, double maxAdjacentSpanBlocks) {
        SuspensionVariant variant = resolveSuspensionVariant(maxAdjacentSpanBlocks);
        return switch (variant) {
            case SMALL -> family.getDesignId(TowerRole.SUSPENSION);
            case MEDIUM -> family.getDesignId(TowerRole.SPECIAL);
            case LARGE -> family.getDesignId(TowerRole.DEAD_END);
        };
    }

    /**
     * 旧数据：SUSPENSION / SPECIAL / DEAD_END 三角色映射三档悬垂尺寸。
     * 新族应使用 {@link TowerFamily#setSuspensionVariantDesignId}。
     */
    private static boolean hasLegacyGradedSuspensionVariants(TowerFamily family) {
        String small = family.getDesignId(TowerRole.SUSPENSION);
        String medium = family.getDesignId(TowerRole.SPECIAL);
        String tall = family.getDesignId(TowerRole.DEAD_END);
        return small != null
            && medium != null
            && tall != null
            && !small.equals(medium)
            && !medium.equals(tall);
    }
}
