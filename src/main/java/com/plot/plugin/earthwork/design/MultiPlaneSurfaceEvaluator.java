package com.plot.plugin.earthwork.design;
import com.plot.plugin.earthwork.grading.GradingPlane;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceFacet;
import com.plot.plugin.earthwork.model.DesignSurfaceKind;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link DesignSurfaceKind#MULTI_PLANE} 子坡面求值。
 */
public final class MultiPlaneSurfaceEvaluator {

    private MultiPlaneSurfaceEvaluator() {
    }

    public static DesignSurfaceResolver.ZoneTargetEvaluator createEvaluator(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        List<FacetContext> facets = buildFacetContexts(zone, surface, terrain, transformer);
        if (facets.isEmpty()) {
            return null;
        }
        return cell -> {
            DesignSurfaceFacet facet = findFacet(facets, cell.center());
            if (facet == null) {
                return cell.existingGroundY();
            }
            FacetContext context = facets.stream()
                .filter(item -> item.facet() == facet)
                .findFirst()
                .orElse(null);
            if (context == null || context.evaluator() == null) {
                return cell.existingGroundY();
            }
            return context.evaluator().evaluateAt(cell);
        };
    }

    private static List<FacetContext> buildFacetContexts(
            GradingZone zone,
            DesignSurface surface,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        List<FacetContext> contexts = new ArrayList<>();
        for (DesignSurfaceFacet facet : surface.getFacets()) {
            if (facet == null || facet.getGeometry().isEmpty()) {
                continue;
            }
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator = resolveFacetEvaluator(
                zone, facet, terrain, transformer);
            if (evaluator == null) {
                continue;
            }
            double area = facet.getGeometry().area();
            contexts.add(new FacetContext(facet, area, evaluator));
        }
        contexts.sort(Comparator.comparingDouble(FacetContext::area));
        return contexts;
    }

    private static DesignSurfaceResolver.ZoneTargetEvaluator resolveFacetEvaluator(
            GradingZone zone,
            DesignSurfaceFacet facet,
            TerrainSnapshot terrain,
            ICoordinateService transformer) {
        DesignSurface plane = facet.getPlane();
        DesignSurfaceKind kind = plane.getKind();
        if (kind == DesignSurfaceKind.MATCH_EXISTING) {
            int offset = plane.getVerticalOffset();
            return cell -> cell.existingGroundY() + offset;
        }
        if (kind == DesignSurfaceKind.MULTI_PLANE) {
            return null;
        }
        GradingRegion facetRegion = buildFacetRegion(zone, facet, plane);
        List<Vec2d> centers = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            if (!facet.containsCanvasPoint(column.center())) {
                continue;
            }
            centers.add(column.center());
            heights.add(column.groundY());
        }
        GradingSurfaceResolver.ResolvedSurface resolved = GradingSurfaceResolver.resolve(
            facetRegion, centers, heights, transformer);
        GradingPlane gradingPlane = resolved.plane();
        return cell -> gradingPlane.evaluateAt(cell.worldX(), cell.worldZ());
    }

    private static GradingRegion buildFacetRegion(
            GradingZone zone,
            DesignSurfaceFacet facet,
            DesignSurface plane) {
        GradingRegion region = new GradingRegion(
            zone.getId() + ":" + facet.getId(),
            facet.getGeometry());
        plane.applyTo(region);
        return region;
    }

    private static DesignSurfaceFacet findFacet(List<FacetContext> facets, Vec2d point) {
        DesignSurfaceFacet winner = null;
        double winnerArea = Double.MAX_VALUE;
        for (FacetContext context : facets) {
            if (!context.facet().containsCanvasPoint(point)) {
                continue;
            }
            if (context.area() < winnerArea) {
                winnerArea = context.area();
                winner = context.facet();
            }
        }
        return winner;
    }

    private record FacetContext(
            DesignSurfaceFacet facet,
            double area,
            DesignSurfaceResolver.ZoneTargetEvaluator evaluator) {
    }
}
