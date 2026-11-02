package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure cross-section light placement shared by tunnel generation and UI preview.
 * Longitudinal spacing is applied by the caller.
 */
public final class TunnelLightPlacementResolver {
    public enum LightType {
        WALL,
        CEILING
    }

    public record LightPosition(int lateral, int y, LightType type) {
    }

    private TunnelLightPlacementResolver() {
    }

    public static List<LightPosition> resolve(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode mode) {
        List<LightPosition> lights = new ArrayList<>();
        if (mode == null || mode == TunnelLightingMode.NONE) {
            return lights;
        }
        int side = Math.max(TunnelStyle.MIN_SIDE_CLEARANCE, sideClearance);
        int clear = Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight);
        int lining = Math.max(TunnelStyle.MIN_LINING_THICKNESS, liningThickness);
        int cavityWidth = roadEnvelopeWidth + side * 2;
        int cavityMin = RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int halfCavityWidth = cavityWidth / 2;
        TunnelShape resolved = shape != null ? shape : TunnelShape.ARCH;
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            resolved, roadEnvelopeWidth, side, clear, lining, roadY, false);

        if (mode.placesWallLights()) {
            addWallLight(lights, mask, cavityMin, +1, roadY, clear);
            addWallLight(lights, mask, cavityMax, -1, roadY, clear);
        }
        if (mode.placesCeilingLights()) {
            int ceilingY = TunnelProfile.innerCeilingY(resolved, 0, halfCavityWidth, roadY, clear);
            if (isMaskAir(mask, 0, ceilingY)) {
                lights.add(new LightPosition(0, ceilingY, LightType.CEILING));
            }
        }
        return lights;
    }

    public static List<LightPosition> resolve(ResolvedTunnelStyle style, int roadEnvelopeWidth, int roadY) {
        if (style == null) {
            return List.of();
        }
        return resolve(
            style.shape(),
            roadEnvelopeWidth,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            roadY,
            style.lightingMode());
    }

    private static void addWallLight(
            List<LightPosition> lights,
            TunnelCrossSectionMask mask,
            int edgeLateral,
            int inwardStep,
            int roadY,
            int clearHeight) {
        int preferredY = roadY + 2;
        for (int lateral = edgeLateral; ; lateral += inwardStep) {
            if (inwardStep > 0 && lateral > 0) {
                break;
            }
            if (inwardStep < 0 && lateral < 0) {
                break;
            }
            Integer y = wallLightYInColumn(mask, lateral, preferredY, roadY, clearHeight);
            if (y != null) {
                lights.add(new LightPosition(lateral, y, LightType.WALL));
                return;
            }
            if (lateral == 0) {
                break;
            }
        }
    }

    private static Integer wallLightYInColumn(
            TunnelCrossSectionMask mask,
            int lateral,
            int preferredY,
            int roadY,
            int clearHeight) {
        if (isMaskAir(mask, lateral, preferredY)) {
            return preferredY;
        }
        int mid = roadY + Math.max(2, clearHeight / 2);
        if (isMaskAir(mask, lateral, mid)) {
            return mid;
        }
        for (int y = roadY + 1; y <= roadY + clearHeight; y++) {
            if (isMaskAir(mask, lateral, y)) {
                return y;
            }
        }
        return null;
    }

    private static boolean isMaskAir(TunnelCrossSectionMask mask, int lateral, int y) {
        return mask != null && mask.kindAt(lateral, y) == TunnelCrossSectionMask.CellKind.AIR;
    }
}
