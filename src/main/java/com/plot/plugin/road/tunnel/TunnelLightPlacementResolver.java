package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure cross-section light placement shared by tunnel generation and UI preview.
 * Longitudinal spacing is applied by the caller.
 * <p>
 * Lights may only sit on a mountable surface: protruding AIR cells adjacent to
 * lining, or embedded inner-face LINING. Free-floating cavity air (e.g. lane
 * center) is rejected. Custom anchors that fall outside a narrower section are
 * snapped toward the wall/ceiling when possible; unresolved anchors are kept
 * in storage but omitted from the resolved list.
 */
public final class TunnelLightPlacementResolver {
    public enum LightType {
        WALL,
        CEILING
    }

    public record LightPosition(int lateral, int y, LightType type) {
    }

    /** Resolved lights plus how many custom anchors could not be placed. */
    public record ResolveResult(List<LightPosition> lights, int unresolvedAnchors) {
        public ResolveResult {
            lights = lights != null ? List.copyOf(lights) : List.of();
        }
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
        return resolveDetailed(
            shape,
            roadEnvelopeWidth,
            sideClearance,
            clearHeight,
            liningThickness,
            roadY,
            mode,
            TunnelLightMount.PROTRUDING,
            null).lights();
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
        return resolveDetailed(
            shape, roadEnvelopeWidth, sideClearance, clearHeight, liningThickness,
            roadY, mode, mount, customAnchors).lights();
    }

    public static ResolveResult resolveDetailed(
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
            return fromCustomAnchors(customAnchors, mask, roadY, clear, resolvedMount);
        }

        List<LightPosition> protruding = modeDefaultsProtruding(
            resolved, mask, roadEnvelopeWidth, side, clear, roadY, mode);
        if (resolvedMount == TunnelLightMount.PROTRUDING) {
            return new ResolveResult(protruding, 0);
        }
        List<LightPosition> embedded = new ArrayList<>(protruding.size());
        for (LightPosition light : protruding) {
            LightPosition snapped = toEmbedded(mask, light);
            if (snapped != null && isValidLightCell(mask, snapped.lateral(), snapped.y(), roadY, resolvedMount)) {
                embedded.add(snapped);
            }
        }
        return new ResolveResult(dedupe(embedded), 0);
    }

    public static List<LightPosition> resolve(ResolvedTunnelStyle style, int roadEnvelopeWidth, int roadY) {
        return resolveDetailed(style, roadEnvelopeWidth, roadY).lights();
    }

    public static ResolveResult resolveDetailed(ResolvedTunnelStyle style, int roadEnvelopeWidth, int roadY) {
        if (style == null) {
            return new ResolveResult(List.of(), 0);
        }
        return resolveDetailed(
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

    /**
     * Valid click / placement target for the mount.
     * Protruding: AIR adjacent to lining (wall or ceiling face).
     * Embedded: inner-face LINING. Free cavity air is never valid.
     */
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
        if (resolved == TunnelLightMount.EMBEDDED) {
            return isInnerFaceLining(mask, lateral, y);
        }
        return isMaskAir(mask, lateral, y) && isAdjacentToLining(mask, lateral, y);
    }

    public static LightType inferLightType(int lateral, int y, int roadY, int clearHeight) {
        int top = roadY + Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight);
        if (lateral == 0 && y >= top - 1) {
            return LightType.CEILING;
        }
        return LightType.WALL;
    }

    public static LightType inferLightType(
            TunnelCrossSectionMask mask,
            int lateral,
            int y,
            int roadY,
            int clearHeight) {
        if (mask != null && mask.kindAt(lateral, y + 1) == TunnelCrossSectionMask.CellKind.LINING) {
            boolean sideLining = mask.kindAt(lateral - 1, y) == TunnelCrossSectionMask.CellKind.LINING
                || mask.kindAt(lateral + 1, y) == TunnelCrossSectionMask.CellKind.LINING;
            if (!sideLining || lateral == 0) {
                return LightType.CEILING;
            }
        }
        return inferLightType(lateral, y, roadY, clearHeight);
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

        List<TunnelLightAnchor> base;
        if (style.getStoredLightAnchors() != null) {
            base = new ArrayList<>(style.getStoredLightAnchors());
        } else {
            base = new ArrayList<>();
            for (LightPosition light : current) {
                base.add(TunnelLightAnchor.fromAbsolute(
                    light.lateral(), light.y(), roadY, light.type()));
            }
        }

        if (occupied) {
            base.removeIf(anchor -> {
                if (anchor == null) {
                    return false;
                }
                if (anchor.lateral() == lateral && anchor.absoluteY(roadY) == y) {
                    return true;
                }
                LightPosition placed = placeCustomAnchor(
                    anchor, mask, roadY, style.getClearHeight(), mount);
                return placed != null && placed.lateral() == lateral && placed.y() == y;
            });
        } else {
            LightType type = inferLightType(mask, lateral, y, roadY, style.getClearHeight());
            base.add(TunnelLightAnchor.fromAbsolute(lateral, y, roadY, type));
        }
        style.setLightAnchors(base);
        return true;
    }

    /**
     * Mount flips are resolved at placement time; stored anchors are left intact so
     * variable-width sections can still recover original intent.
     */
    public static void syncCustomAnchorsToMount(
            TunnelStyle style,
            int roadEnvelopeWidth,
            int roadY) {
        // Intentionally no-op: rewriting would drop anchors that only fit wider envelopes.
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
            if (isValidLightCell(mask, 0, ceilingY, roadY, TunnelLightMount.PROTRUDING)) {
                lights.add(new LightPosition(0, ceilingY, LightType.CEILING));
            }
        }
        return lights;
    }

    private static ResolveResult fromCustomAnchors(
            List<TunnelLightAnchor> customAnchors,
            TunnelCrossSectionMask mask,
            int roadY,
            int clearHeight,
            TunnelLightMount mount) {
        List<LightPosition> lights = new ArrayList<>();
        int unresolved = 0;
        for (TunnelLightAnchor anchor : customAnchors) {
            if (anchor == null) {
                continue;
            }
            LightPosition placed = placeCustomAnchor(anchor, mask, roadY, clearHeight, mount);
            if (placed != null) {
                lights.add(placed);
            } else {
                unresolved++;
            }
        }
        return new ResolveResult(dedupe(lights), unresolved);
    }

    private static LightPosition placeCustomAnchor(
            TunnelLightAnchor anchor,
            TunnelCrossSectionMask mask,
            int roadY,
            int clearHeight,
            TunnelLightMount mount) {
        int y = anchor.absoluteY(roadY);
        if (y <= roadY) {
            return null;
        }
        LightType type = anchor.type() != null ? anchor.type() : LightType.WALL;
        LightPosition exact = new LightPosition(anchor.lateral(), y, type);
        if (isValidLightCell(mask, exact.lateral(), exact.y(), roadY, mount)) {
            return exact;
        }
        if (mount == TunnelLightMount.EMBEDDED && isMaskAir(mask, exact.lateral(), exact.y())) {
            LightPosition embedded = toEmbedded(mask, exact);
            if (embedded != null && isValidLightCell(mask, embedded.lateral(), embedded.y(), roadY, mount)) {
                return embedded;
            }
        }
        if (mount == TunnelLightMount.PROTRUDING && isInnerFaceLining(mask, exact.lateral(), exact.y())) {
            LightPosition protruding = toProtruding(mask, exact);
            if (protruding != null && isValidLightCell(mask, protruding.lateral(), protruding.y(), roadY, mount)) {
                return protruding;
            }
        }
        return snapToMountSurface(mask, exact, roadY, clearHeight, mount);
    }

    /**
     * When an anchor falls outside a narrower cavity, walk toward the near wall
     * (or ceiling) for a valid mount cell at a similar elevation.
     */
    private static LightPosition snapToMountSurface(
            TunnelCrossSectionMask mask,
            LightPosition desired,
            int roadY,
            int clearHeight,
            TunnelLightMount mount) {
        if (desired.type() == LightType.CEILING) {
            int yMin = Math.max(roadY + 1, desired.y() - 2);
            int yMax = desired.y() + Math.max(2, clearHeight);
            for (int y = yMin; y <= yMax; y++) {
                for (int lateral = 0; lateral <= 2; lateral++) {
                    int[] sides = lateral == 0 ? new int[]{0} : new int[]{lateral, -lateral};
                    for (int side : sides) {
                        if (!isValidLightCell(mask, side, y, roadY, mount)) {
                            continue;
                        }
                        if (mount == TunnelLightMount.EMBEDDED
                                || mask.kindAt(side, y + 1) == TunnelCrossSectionMask.CellKind.LINING) {
                            return new LightPosition(side, y, LightType.CEILING);
                        }
                    }
                }
            }
            return null;
        }

        int direction = desired.lateral() >= 0 ? 1 : -1;
        for (int dy = 0; dy <= 3; dy++) {
            for (int yDelta : dy == 0 ? new int[]{0} : new int[]{dy, -dy}) {
                int y = desired.y() + yDelta;
                if (y <= roadY) {
                    continue;
                }
                for (int step = 0; step <= 16; step++) {
                    int lateral = desired.lateral() + direction * step;
                    if (isValidLightCell(mask, lateral, y, roadY, mount)) {
                        return new LightPosition(lateral, y, LightType.WALL);
                    }
                    if (step > 0) {
                        int opposite = desired.lateral() - direction * step;
                        if (isValidLightCell(mask, opposite, y, roadY, mount)) {
                            return new LightPosition(opposite, y, LightType.WALL);
                        }
                    }
                }
            }
        }
        return null;
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
        if (isMaskAir(mask, embedded.lateral(), embedded.y())
                && isAdjacentToLining(mask, embedded.lateral(), embedded.y())) {
            return embedded;
        }
        if (!isInnerFaceLining(mask, embedded.lateral(), embedded.y())) {
            return null;
        }
        for (int[] d : DIRS) {
            int nl = embedded.lateral() + d[0];
            int ny = embedded.y() + d[1];
            if (isMaskAir(mask, nl, ny) && isAdjacentToLining(mask, nl, ny)) {
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

    private static boolean isAdjacentToLining(TunnelCrossSectionMask mask, int lateral, int y) {
        for (int[] d : DIRS) {
            if (mask.kindAt(lateral + d[0], y + d[1]) == TunnelCrossSectionMask.CellKind.LINING) {
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
            // Search valid mount cells in this column before stepping inward:
            // preferred Y → nearby heights → remaining column heights.
            Integer y = firstValidWallLightYInColumn(mask, lateral, preferredY, roadY, clearHeight);
            if (y != null) {
                lights.add(new LightPosition(lateral, y, LightType.WALL));
                return;
            }
            if (lateral == 0) {
                break;
            }
        }
    }

    /**
     * Finds a protruding wall-light Y in one lateral column. Unlike a plain AIR
     * scan, this only accepts cells that already satisfy {@link #isValidLightCell}.
     */
    private static Integer firstValidWallLightYInColumn(
            TunnelCrossSectionMask mask,
            int lateral,
            int preferredY,
            int roadY,
            int clearHeight) {
        int mid = roadY + Math.max(2, clearHeight / 2);
        int[] priority = {preferredY, mid};
        for (int y : priority) {
            if (y > roadY && y <= roadY + clearHeight
                    && isValidLightCell(mask, lateral, y, roadY, TunnelLightMount.PROTRUDING)) {
                return y;
            }
        }
        // Expand around the preferred height first so curved walls keep a sensible band.
        for (int delta = 1; delta <= clearHeight; delta++) {
            int up = preferredY + delta;
            int down = preferredY - delta;
            if (up <= roadY + clearHeight
                    && isValidLightCell(mask, lateral, up, roadY, TunnelLightMount.PROTRUDING)) {
                return up;
            }
            if (down > roadY
                    && isValidLightCell(mask, lateral, down, roadY, TunnelLightMount.PROTRUDING)) {
                return down;
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
