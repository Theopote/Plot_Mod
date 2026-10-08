package com.plot.plugin.road.tunnel;

/**
 * Shape-specific tunnel cavity membership for voxel placement.
 */
public final class TunnelProfile {
    private TunnelProfile() {
    }

    /**
     * @param lateral signed offset from road center in blocks
     * @param y world elevation
     * @param roadY road surface elevation
     * @param halfCavityWidth half of interior cavity width in blocks
     * @param clearHeight interior clearance above road surface
     */
    public static boolean isCavityAir(
            TunnelShape shape,
            int lateral,
            int y,
            int roadY,
            int halfCavityWidth,
            int clearHeight) {
        if (y <= roadY || y > roadY + clearHeight + 2) {
            return false;
        }
        int distFromCenter = Math.abs(lateral);
        if (distFromCenter > halfCavityWidth) {
            return false;
        }
        int ceilingY = ceilingY(shape, distFromCenter, halfCavityWidth, roadY, clearHeight);
        if (y > ceilingY) {
            return false;
        }
        if (shape == TunnelShape.HORSESHOE) {
            int floorY = floorY(distFromCenter, halfCavityWidth, roadY);
            if (y < floorY) {
                return false;
            }
        }
        return true;
    }

    public static int ceilingY(
            TunnelShape shape,
            int distFromCenter,
            int halfCavityWidth,
            int roadY,
            int clearHeight) {
        if (shape == TunnelShape.RECTANGULAR) {
            return roadY + clearHeight;
        }
        int archDepth = Math.max(1, clearHeight - 1);
        double norm = halfCavityWidth > 0 ? (double) distFromCenter / halfCavityWidth : 0.0;
        int drop = (int) Math.round(norm * norm * archDepth);
        return roadY + clearHeight - drop;
    }

    private static int floorY(int distFromCenter, int halfCavityWidth, int roadY) {
        int inset = Math.max(1, halfCavityWidth / 3);
        if (distFromCenter > halfCavityWidth - inset) {
            return roadY + 2;
        }
        return roadY + 1;
    }
}
