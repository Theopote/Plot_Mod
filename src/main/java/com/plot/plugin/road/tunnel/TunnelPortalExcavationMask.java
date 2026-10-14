package com.plot.plugin.road.tunnel;

/**
 * Portal-specific excavation volume derived from {@link TunnelCrossSectionMask}.
 * Limits corridor clearance to designed cavity AIR cells only.
 */
public final class TunnelPortalExcavationMask {
    private final TunnelCrossSectionMask crossSection;
    private final int roadY;
    private final int roofTopY;

    private TunnelPortalExcavationMask(
            TunnelCrossSectionMask crossSection,
            int roadY,
            int roofTopY) {
        this.crossSection = crossSection;
        this.roadY = roadY;
        this.roofTopY = roofTopY;
    }

    public static TunnelPortalExcavationMask forPortal(
            ResolvedTunnelStyle style,
            int roadEnvelopeWidth,
            int roadY) {
        TunnelCrossSectionMask section = TunnelCrossSectionMask.build(
            style.shape(),
            roadEnvelopeWidth,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            roadY,
            true);
        return new TunnelPortalExcavationMask(section, roadY, roadY + style.roofTopOffset());
    }

    public int roofTopY() {
        return roofTopY;
    }

    public boolean allowsClearance(int lateral, int y) {
        if (y <= roadY || y > roofTopY) {
            return false;
        }
        return crossSection.kindAt(lateral, y) == TunnelCrossSectionMask.CellKind.AIR;
    }

    public boolean shouldPreserve(int lateral, int y) {
        if (y <= roadY) {
            return true;
        }
        if (y > roofTopY) {
            return true;
        }
        return crossSection.kindAt(lateral, y) != TunnelCrossSectionMask.CellKind.AIR;
    }

    public TunnelCrossSectionMask crossSection() {
        return crossSection;
    }
}
