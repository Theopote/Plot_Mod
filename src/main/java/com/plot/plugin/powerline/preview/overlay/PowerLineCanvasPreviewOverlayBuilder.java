package com.plot.plugin.powerline.preview.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PoleLayoutConstraint;
import com.plot.plugin.powerline.model.PoleOverride;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.List;

/** 从 {@link PowerLineGenerationResult} 构建画布预览叠加层。 */
public final class PowerLineCanvasPreviewOverlayBuilder {
    private static final double STATION_MATCH_TOLERANCE = 2.0;

    private PowerLineCanvasPreviewOverlayBuilder() {
    }

    public static PowerLineCanvasPreviewOverlay build(
            PowerLineGenerationResult result,
            PowerLineFootprint footprint,
            PoleDesignResolver resolver) {
        if (result == null || footprint == null || result.poleSites.isEmpty()) {
            return null;
        }
        List<TowerPreviewMarker> markers = new ArrayList<>(result.poleSites.size());
        List<Vec2d> spanPath = new ArrayList<>(result.poleSites.size());
        int angleCount = 0;
        int terrainInsertCount = 0;
        int userOverrideCount = 0;

        int siteCount = result.poleSites.size();
        boolean closedLoop = footprint.isClosedLoop();
        for (int i = 0; i < siteCount; i++) {
            PowerPoleSite site = result.poleSites.get(i);
            PolePlacement placement = i < result.polePlacements.size()
                ? result.polePlacements.get(i)
                : null;
            MarkerSource source = resolveSource(site, footprint, i, siteCount, closedLoop);
            switch (source) {
                case CORNER -> angleCount++;
                case TERRAIN_AUTO_INSERT -> terrainInsertCount++;
                case USER_OVERRIDE -> userOverrideCount++;
                default -> {
                }
            }
            Vec2d crossarmAxis = resolveCrossarmAxis(placement);
            markers.add(new TowerPreviewMarker(
                site.getPlanPosition(),
                crossarmAxis,
                site.getRole(),
                source,
                site.getId(),
                site.getStationing(),
                resolveDesignLabel(placement, resolver),
                resolveSourceReasonKey(source, site, footprint)));
            spanPath.add(site.getPlanPosition());
        }

        int spanCount = closedLoop
            ? Math.max(0, siteCount)
            : Math.max(0, siteCount - 1);
        PowerLineCanvasPreviewOverlay.PreviewStats stats =
            new PowerLineCanvasPreviewOverlay.PreviewStats(
                siteCount,
                spanCount,
                angleCount,
                terrainInsertCount,
                userOverrideCount);
        return new PowerLineCanvasPreviewOverlay(
            footprint.getId(),
            markers,
            spanPath,
            closedLoop,
            stats);
    }

    private static Vec2d resolveCrossarmAxis(PolePlacement placement) {
        if (placement == null || placement.frame() == null) {
            return new Vec2d(0, 1);
        }
        Vec2d right = placement.frame().right();
        if (right == null || right.lengthSquared() < 1e-12) {
            return new Vec2d(0, 1);
        }
        return right.normalize();
    }

    private static String resolveDesignLabel(PolePlacement placement, PoleDesignResolver resolver) {
        if (placement == null) {
            return "";
        }
        String designId = placement.resolvedDesignId();
        if (designId == null || designId.isBlank()) {
            return "";
        }
        if (resolver != null) {
            PoleDesign design = resolver.find(designId);
            if (design != null && design.getName() != null && !design.getName().isBlank()) {
                return design.getName();
            }
        }
        PoleDesign builtin = PoleDesignCatalog.findBuiltin(designId);
        if (builtin != null && builtin.getName() != null && !builtin.getName().isBlank()) {
            return builtin.getName();
        }
        return designId;
    }

    private static MarkerSource resolveSource(
            PowerPoleSite site,
            PowerLineFootprint footprint,
            int index,
            int siteCount,
            boolean closedLoop) {
        double station = site.getStationing();
        if (matchesAutoConstraint(footprint.getDerivedLayout().autoLayoutConstraints(), station)) {
            return MarkerSource.TERRAIN_AUTO_INSERT;
        }
        if (matchesUserOverride(footprint.getPoleOverrides(), station)
                || matchesUserConstraint(footprint.getLayoutConstraints(), station)) {
            return MarkerSource.USER_OVERRIDE;
        }
        TowerRole role = site.getRole();
        if (role == TowerRole.ANGLE) {
            return MarkerSource.CORNER;
        }
        if ((role == TowerRole.TERMINAL || role == TowerRole.DEAD_END) && isEndpoint(index, siteCount, closedLoop)) {
            return MarkerSource.ENDPOINT;
        }
        if (role == TowerRole.SPECIAL) {
            return MarkerSource.STANDALONE;
        }
        return MarkerSource.AUTO_LAYOUT;
    }

    private static String resolveSourceReasonKey(
            MarkerSource source,
            PowerPoleSite site,
            PowerLineFootprint footprint) {
        if (source != MarkerSource.TERRAIN_AUTO_INSERT) {
            return "";
        }
        for (PoleLayoutConstraint constraint : footprint.getDerivedLayout().autoLayoutConstraints()) {
            if (matchesStation(constraint.getRequiredStationing(), site.getStationing())) {
                return constraint.getReason() != null ? constraint.getReason() : "";
            }
        }
        return "";
    }

    private static boolean matchesAutoConstraint(List<PoleLayoutConstraint> constraints, double station) {
        if (constraints == null) {
            return false;
        }
        for (PoleLayoutConstraint constraint : constraints) {
            if (constraint != null && matchesStation(constraint.getRequiredStationing(), station)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesUserConstraint(List<PoleLayoutConstraint> constraints, double station) {
        return matchesAutoConstraint(constraints, station);
    }

    private static boolean matchesUserOverride(List<PoleOverride> overrides, double station) {
        if (overrides == null) {
            return false;
        }
        for (PoleOverride override : overrides) {
            if (override != null && matchesStation(override.getPathDistance(), station)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStation(double expected, double actual) {
        return Math.abs(expected - actual) <= STATION_MATCH_TOLERANCE;
    }

    private static boolean isEndpoint(int index, int siteCount, boolean closedLoop) {
        if (siteCount <= 1) {
            return true;
        }
        if (closedLoop) {
            return false;
        }
        return index == 0 || index == siteCount - 1;
    }
}
