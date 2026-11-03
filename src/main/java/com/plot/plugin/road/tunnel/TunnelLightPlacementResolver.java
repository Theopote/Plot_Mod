package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private TunnelLightPlacementResolver() {
    }

    /** Mode-default protruding lights (legacy signature). */
    public static List<LightPosition> resolve(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode mode) {
        return resolve(
            shape,
            roadEnvelopeWidth,
            sideClearance,
            clearHeight,
            liningThickness,
            roadY,
            mode,
            TunnelLightMount.PROTRUDING,
            null);
    }

    public static List<LightPosition> resolve(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode mode,
            TunnelLightMount mount,
            List<TunnelLightAnchor> customAnchors) {
        TunnelShape resolved = shape != null ? shape : TunnelShape.ARCH;
        TunnelLightMount resolvedMount = mount != null ? mount : TunnelLightMount.PROTRUDING;
        int side = Math.max(TunnelStyle.MIN_SIDE_CLEARANCE, sideClearance);
        int clear = Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight);
        int lining = Math.max(TunnelStyle.MIN_LINING_THICKNESS, liningThickness);
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            resolved, roadEnvelopeWidth, side, clear, lining, roadY, false);

        if (customAnchors != null) {
            // Custom anchors are validated for the active mount (with cross-mount snap).
            return fromCustomAnchors(customAnchors, mask, roadY, resolvedMount);
        }

        List<LightPosition> protruding = modeDefaultsProtruding(
            resolved, mask, roadEnvelopeWidth, side, clear, roadY, mode);
        if (resolvedMount == TunnelLightMount.PROTRUDING) {
            return List.copyOf(protruding);
        }
        List<LightPosition> embedded = new ArrayList<>(protruding.size());
        for (LightPosition light : protruding) {
            LightPosition snapped = toEmbedded(mask, light);
            if (snapped != null) {
                embedded.add(snapped);
            }
        }
        return dedupe(embedded);
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
            style.lightingMode(),
            style.lightMount(),
            style.lightAnchors());
    }

    /**
     * Mode-default protruding positions as anchors relative to {@code roadY}
     * (used when seeding a custom layout from the current mode).
     */
    public static List<TunnelLightAnchor> defaultAnchors(
            TunnelShape shape,
            int roadEnvelopeWidth,
            int sideClearance,
            int clearHeight,
            int liningThickness,
            int roadY,
            TunnelLightingMode mode) {
        List<LightPosition> lights = resolve(
            shape, roadEnvelopeWidth, sideClearance, clearHeight, liningThickness, roadY, mode,
            TunnelLightMount.PROTRUDING, null);
        List<TunnelLightAnchor> anchors = new ArrayList<>(lights.size());
        for (LightPosition light : lights) {
            anchors.add(TunnelLightAnchor.fromAbsolute(light.lateral(), light.y(), roadY, light.type()));
        }
        return anchors;
    }

    /** Whether a cell is a valid click target for the given mount. */
    public static boolean isValidLightCell(
            TunnelCrossSectionMask mask,
            int lateral,
            int y,
            int roadY,
            TunnelLightMount mount) {
        if (mask == null || y <= roadY) {
            return false;
        }
        TunnelLightMount resolved = mount != null ? mount : TunnelLightMount.PROTRUDING;
        if (resolved == TunnelLightMount.PROTRUDING) {
            return isMaskAir(mask, lateral, y);
        }
        return isInnerFaceLining(mask, lateral, y);
    }

    public static LightType inferLightType(int lateral, int y, int roadY, int clearHeight) {
        int top = roadY + Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight);
        if (lateral == 0 && y >= top - 1) {
            return LightType.CEILING;
        }
        return LightType.WALL;
    }

    /**
     * Toggle a light at the given absolute cell on {@code style}'s custom layout.
     * Seeds from the current resolved layout on the first edit.
     *
     * @return true when the style's light anchors changed
     */
    public static boolean toggleLightOnStyle(
            TunnelStyle style,
            int roadEnvelopeWidth,
            int roadY,
            int lateral,
            int y) {
        if (style == null || y <= roadY) {
            return false;
        }
        int envelope = Math.max(1, roadEnvelopeWidth);
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            style.getShape(),
            envelope,
            style.getSideClearance(),
            style.getClearHeight(),
            style.getLiningThickness(),
            roadY,
            false);
        TunnelLightMount mount = style.getLightMount();
        List<LightPosition> current = resolve(
            style.getShape(),
            envelope,
            style.getSideClearance(),
            style.getClearHeight(),
            style.getLiningThickness(),
            roadY,
            style.getLightingMode(),
            mount,
            style.getStoredLightAnchors());

        boolean occupied = current.stream().anyMatch(l -> l.lateral() == lateral && l.y() == y);
        if (!occupied && !isValidLightCell(mask, lateral, y, roadY, mount)) {
            return false;
        }

        // Always rewrite anchors from the currently resolved cells so mount flips stay consistent.
        List<TunnelLightAnchor> next = new ArrayList<>();
        for (LightPosition light : current) {
            if (light.lateral() == lateral && light.y() == y) {
                continue;
            }
            next.add(TunnelLightAnchor.fromAbsolute(
                light.lateral(), light.y(), roadY, light.type()));
        }
        if (!occupied) {
            LightType type = inferLightType(lateral, y, roadY, style.getClearHeight());
            next.add(TunnelLightAnchor.fromAbsolute(lateral, y, roadY, type));
        }
        style.setLightAnchors(next);
        return true;
    }

    /** Re-store custom anchors at the cells implied by the current mount (no-op if not custom). */
    public static void syncCustomAnchorsToMount(
            TunnelStyle style,
            int roadEnvelopeWidth,
            int roadY) {
        if (style == null || !style.hasCustomLightLayout()) {
            return;
        }
        int envelope = Math.max(1, roadEnvelopeWidth);
        List<LightPosition> lights = resolve(
            style.getShape(),
            envelope,
            style.getSideClearance(),
            style.getClearHeight(),
            style.getLiningThickness(),
            roadY,
            style.getLightingMode(),
            style.getLightMount(),
            style.getStoredLightAnchors());
        List<TunnelLightAnchor> anchors = new ArrayList<>(lights.size());
        for (LightPosition light : lights) {
            anchors.add(TunnelLightAnchor.fromAbsolute(
                light.lateral(), light.y(), roadY, light.type()));
        }
        style.setLightAnchors(anchors);
    }

    private static List<LightPosition> modeDefaultsProtruding(
            TunnelShape shape,
            TunnelCrossSectionMask mask,
            int roadEnvelopeWidth,
            int side,
            int clear,
            int roadY,
            TunnelLightingMode mode) {
        List<LightPosition> lights = new ArrayList<>();
        if (mode == null || mode == TunnelLightingMode.NONE) {
            return lights;
        }
        int cavityWidth = roadEnvelopeWidth + side * 2;
        int cavityMin = RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int halfCavityWidth = cavityWidth / 2;

        if (mode.placesWallLights()) {
            addWallLight(lights, mask, cavityMin, +1, roadY, clear);
            addWallLight(lights, mask, cavityMax, -1, roadY, clear);
        }
        if (mode.placesCeilingLights()) {
            int ceilingY = TunnelProfile.innerCeilingY(shape, 0, halfCavityWidth, roadY, clear);
            if (isMaskAir(mask, 0, ceilingY)) {
                lights.add(new LightPosition(0, ceilingY, LightType.CEILING));
            }
        }
        return lights;
    }

    /**
     * Interprets custom anchors. Stored anchors are always the protruding (AIR) cell
     * when mount is PROTRUDING, or the intended click cell for EMBEDDED (inner LINING).
     * For EMBEDDED UI clicks we store the lining cell; resolve then returns it directly
     * when mount is EMBEDDED. For PROTRUDING, store/validate AIR.
     */
    private static List<LightPosition> fromCustomAnchors(
            List<TunnelLightAnchor> customAnchors,
            TunnelCrossSectionMask mask,
            int roadY,
            TunnelLightMount mount) {
        List<LightPosition> lights = new ArrayList<>();
        for (TunnelLightAnchor anchor : customAnchors) {
            if (anchor == null) {
                continue;
            }
            int y = anchor.absoluteY(roadY);
            if (y <= roadY) {
                continue;
            }
            LightType type = anchor.type() != null ? anchor.type() : LightType.WALL;
            if (mount == TunnelLightMount.EMBEDDED) {
                if (isInnerFaceLining(mask, anchor.lateral(), y)) {
                    lights.add(new LightPosition(anchor.lateral(), y, type));
                } else if (isMaskAir(mask, anchor.lateral(), y)) {
                    // Anchor saved while protruding; convert when mount flipped.
                    LightPosition embedded = toEmbedded(
                        mask, new LightPosition(anchor.lateral(), y, type));
                    if (embedded != null) {
                        lights.add(embedded);
                    }
                }
            } else {
                if (isMaskAir(mask, anchor.lateral(), y)) {
                    lights.add(new LightPosition(anchor.lateral(), y, type));
                } else if (isInnerFaceLining(mask, anchor.lateral(), y)) {
                    LightPosition protruding = toProtruding(
                        mask, new LightPosition(anchor.lateral(), y, type));
                    if (protruding != null) {
                        lights.add(protruding);
                    }
                }
            }
        }
        return dedupe(lights);
    }

    private static LightPosition toEmbedded(TunnelCrossSectionMask mask, LightPosition protruding) {
        if (protruding == null) {
            return null;
        }
        if (isInnerFaceLining(mask, protruding.lateral(), protruding.y())) {
            return protruding;
        }
        if (!isMaskAir(mask, protruding.lateral(), protruding.y())) {
            return null;
        }
        // Prefer outward/upward for walls/ceiling.
        int[] preferred;
        if (protruding.type() == LightType.CEILING) {
            preferred = new int[]{0, 1, 0, -1, 1, 0, -1, 0};
        } else if (protruding.lateral() < 0) {
            preferred = new int[]{-1, 0, 0, 1, 0, -1, 1, 0};
        } else if (protruding.lateral() > 0) {
            preferred = new int[]{1, 0, 0, 1, 0, -1, -1, 0};
        } else {
            preferred = new int[]{0, 1, -1, 0, 1, 0, 0, -1};
        }
        for (int i = 0; i + 1 < preferred.length; i += 2) {
            int nl = protruding.lateral() + preferred[i];
            int ny = protruding.y() + preferred[i + 1];
            if (isInnerFaceLining(mask, nl, ny)) {
                return new LightPosition(nl, ny, protruding.type());
            }
        }
        for (int[] d : DIRS) {
            int nl = protruding.lateral() + d[0];
            int ny = protruding.y() + d[1];
            if (isInnerFaceLining(mask, nl, ny)) {
                return new LightPosition(nl, ny, protruding.type());
            }
        }
        return null;
    }

    private static LightPosition toProtruding(TunnelCrossSectionMask mask, LightPosition embedded) {
        if (embedded == null) {
            return null;
        }
        if (isMaskAir(mask, embedded.lateral(), embedded.y())) {
            return embedded;
        }
        if (!isInnerFaceLining(mask, embedded.lateral(), embedded.y())) {
            return null;
        }
        for (int[] d : DIRS) {
            int nl = embedded.lateral() + d[0];
            int ny = embedded.y() + d[1];
            if (isMaskAir(mask, nl, ny)) {
                return new LightPosition(nl, ny, embedded.type());
            }
        }
        return null;
    }

    private static boolean isInnerFaceLining(TunnelCrossSectionMask mask, int lateral, int y) {
        if (mask == null || mask.kindAt(lateral, y) != TunnelCrossSectionMask.CellKind.LINING) {
            return false;
        }
        for (int[] d : DIRS) {
            if (isMaskAir(mask, lateral + d[0], y + d[1])) {
                return true;
            }
        }
        return false;
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

    private static List<LightPosition> dedupe(List<LightPosition> lights) {
        Map<Long, LightPosition> unique = new LinkedHashMap<>();
        for (LightPosition light : lights) {
            long key = (((long) light.lateral()) << 32) ^ (light.y() & 0xffffffffL);
            unique.putIfAbsent(key, light);
        }
        return new ArrayList<>(unique.values());
    }
}
