package com.plot.plugin.powerline.design.parametric;

import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStyleParametricCatalog;

import java.util.Optional;

/** Build-time guard for parametric tower constraint errors on a line. */
public final class TowerParametricBuildPolicy {
    private TowerParametricBuildPolicy() {
    }

    public static boolean hasBlockingIssues(PowerLineFootprint line, PoleDesignResolver resolver) {
        return hasBlockingIssues(line, resolver, null, null);
    }

    public static boolean hasBlockingIssues(
            PowerLineFootprint line,
            PoleDesignResolver resolver,
            TerrainSampler terrain,
            ICoordinateService coordinates) {
        if (line == null || !line.hasParametricTowerConfig()) {
            return false;
        }
        if (!line.hasPoleDesign() && !line.hasTowerFamily()) {
            return false;
        }
        PoleDesign base = resolveBaseDesign(line, resolver);
        if (base == null) {
            return false;
        }

        TowerBuildEnvelope envelope = resolveEnvelope(line, terrain, coordinates);
        PoleDesign design = ParametricStyleTowerApplicator.apply(
            base,
            line.getParametricTowerConfig(),
            envelope);
        if (design == null || !design.isParametricMode()) {
            return false;
        }

        Optional<TowerLineBuildEnvelope> lineEnvelope = resolveLineEnvelope(line, terrain, coordinates);
        if (lineEnvelope.isPresent()) {
            design = TowerParametricLinePlacement.prepare(design, lineEnvelope.get()).design();
        }
        return TowerParametricEditor.hasBlockingErrors(design, envelope);
    }

    private static PoleDesign resolveBaseDesign(PowerLineFootprint line, PoleDesignResolver resolver) {
        if (line.hasPoleDesign()) {
            if (resolver != null) {
                PoleDesign resolved = resolver.find(line.getPoleDesignId());
                if (resolved != null) {
                    return resolved;
                }
            }
            PoleDesign builtin = PoleDesignCatalog.findBuiltin(line.getPoleDesignId());
            if (builtin != null) {
                return builtin;
            }
        }
        return PowerLineStyleParametricCatalog.compileRepresentative(line.getParametricTowerConfig());
    }

    private static TowerBuildEnvelope resolveEnvelope(
            PowerLineFootprint line,
            TerrainSampler terrain,
            ICoordinateService coordinates) {
        return resolveLineEnvelope(line, terrain, coordinates)
            .map(TowerLineBuildEnvelope::constraintEnvelope)
            .orElse(TowerBuildEnvelopeResolver.tryFromClientPlayer().orElse(null));
    }

    private static Optional<TowerLineBuildEnvelope> resolveLineEnvelope(
            PowerLineFootprint line,
            TerrainSampler terrain,
            ICoordinateService coordinates) {
        if (line == null || terrain == null || coordinates == null) {
            return Optional.empty();
        }
        return TowerBuildEnvelopeResolver.tryFromFootprint(line, terrain, coordinates);
    }
}
