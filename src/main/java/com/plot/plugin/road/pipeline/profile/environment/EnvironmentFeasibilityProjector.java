package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.plugin.road.pipeline.profile.terrain.GradeLimitedProfileSolver;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects a vertical design candidate onto the environment-feasible domain
 * (water minimum elevation, max slope, grade change) for all vertical modes.
 */
public final class EnvironmentFeasibilityProjector {

    private static final double EPSILON = 1e-9;

    private EnvironmentFeasibilityProjector() {
    }

    public enum FlattenPolicy {
        NONE,
        AUTO_RAISE_UNIFORM,
        STRICT_MANUAL
    }

    public record EnvironmentFeasibilityResult(
            List<Double> designElevations,
            boolean waterConstraintFeasible,
            boolean manualEndpointsFeasible) {
    }

    public static EnvironmentFeasibilityResult project(
            List<Double> candidateDesign,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            EnvironmentProfile solverEnvironment,
            List<WaterCrossing> crossings,
            WaterCrossingSettings settings,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight,
            FlattenPolicy flattenPolicy) {
        if (candidateDesign == null || candidateDesign.isEmpty()) {
            return new EnvironmentFeasibilityResult(List.of(), true, true);
        }
        if (settings.allowUnderwaterRoad()) {
            return projectWithoutWaterMinimum(
                candidateDesign,
                segmentDistances,
                maxSlopePercents,
                preset,
                manualStartHeight,
                manualEndHeight);
        }
        TerrainFollowPreset effectivePreset = preset != null ? preset : TerrainFollowPreset.STANDARD;
        List<Double> working = new ArrayList<>(candidateDesign);
        double requiredMinimum = globalRequiredMinimum(solverEnvironment, crossings, settings);
        boolean waterFeasible = true;

        if (flattenPolicy == FlattenPolicy.AUTO_RAISE_UNIFORM && requiredMinimum > -Double.MAX_VALUE) {
            double candidateFlat = working.getFirst();
            if (candidateFlat + EPSILON < requiredMinimum) {
                for (int i = 0; i < working.size(); i++) {
                    working.set(i, requiredMinimum);
                }
            }
        }

        if (requiredMinimum > -Double.MAX_VALUE) {
            waterFeasible = satisfiesWaterMinimum(
                working,
                solverEnvironment,
                crossings,
                settings,
                manualStartHeight,
                manualEndHeight,
                flattenPolicy);
        }

        List<VerticalStationConstraint> constraints = VerticalStationConstraints.build(
            solverEnvironment,
            crossings,
            working,
            settings);
        VerticalStationConstraints.StationElevationBounds bounds =
            VerticalStationConstraints.toBounds(constraints);

        int profileStart = manualStartHeight != null
            ? manualStartHeight
            : (int) Math.round(working.getFirst());
        boolean manualEndpointsFeasible = GradeLimitedProfileSolver.areManualEndpointsFeasible(
            manualStartHeight,
            manualEndHeight,
            segmentDistances,
            maxSlopePercents,
            profileStart);

        List<Double> projected = GradeLimitedProfileSolver.projectOntoFeasibleDomain(
            working,
            segmentDistances,
            maxSlopePercents,
            effectivePreset,
            manualStartHeight,
            manualEndHeight,
            bounds,
            manualEndpointsFeasible);

        if (requiredMinimum > -Double.MAX_VALUE) {
            waterFeasible &= satisfiesWaterMinimum(
                projected,
                solverEnvironment,
                crossings,
                settings,
                manualStartHeight,
                manualEndHeight,
                flattenPolicy);
        }

        return new EnvironmentFeasibilityResult(
            projected,
            waterFeasible,
            manualEndpointsFeasible);
    }

    private static EnvironmentFeasibilityResult projectWithoutWaterMinimum(
            List<Double> candidateDesign,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            TerrainFollowPreset preset,
            Integer manualStartHeight,
            Integer manualEndHeight) {
        int profileStart = manualStartHeight != null
            ? manualStartHeight
            : (int) Math.round(candidateDesign.getFirst());
        boolean manualEndpointsFeasible = GradeLimitedProfileSolver.areManualEndpointsFeasible(
            manualStartHeight,
            manualEndHeight,
            segmentDistances,
            maxSlopePercents,
            profileStart);
        List<Double> projected = GradeLimitedProfileSolver.projectOntoFeasibleDomain(
            candidateDesign,
            segmentDistances,
            maxSlopePercents,
            preset != null ? preset : TerrainFollowPreset.STANDARD,
            manualStartHeight,
            manualEndHeight,
            VerticalStationConstraints.StationElevationBounds.unbounded(candidateDesign.size()),
            manualEndpointsFeasible);
        return new EnvironmentFeasibilityResult(projected, true, manualEndpointsFeasible);
    }

    public static boolean evaluateWaterFeasibility(
            List<Double> design,
            EnvironmentProfile solverEnvironment,
            List<WaterCrossing> crossings,
            WaterCrossingSettings settings,
            Integer manualStartHeight,
            Integer manualEndHeight,
            FlattenPolicy flattenPolicy) {
        if (settings.allowUnderwaterRoad()) {
            return true;
        }
        return satisfiesWaterMinimum(
            design,
            solverEnvironment,
            crossings,
            settings,
            manualStartHeight,
            manualEndHeight,
            flattenPolicy);
    }

    public static double globalRequiredMinimum(
            EnvironmentProfile environment,
            List<WaterCrossing> crossings,
            WaterCrossingSettings settings) {
        if (environment == null || environment.samples().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        List<VerticalStationConstraint> constraints = VerticalStationConstraints.build(
            environment,
            crossings,
            environment.samples().stream().map(sample -> (double) sample.terrainY()).toList(),
            settings);
        double required = Double.NEGATIVE_INFINITY;
        for (VerticalStationConstraint constraint : constraints) {
            if (constraint.minimumElevation() != null) {
                required = Math.max(required, constraint.minimumElevation());
            }
        }
        return required;
    }

    private static boolean satisfiesWaterMinimum(
            List<Double> design,
            EnvironmentProfile environment,
            List<WaterCrossing> crossings,
            WaterCrossingSettings settings,
            Integer manualStartHeight,
            Integer manualEndHeight,
            FlattenPolicy flattenPolicy) {
        if (environment == null || design == null || design.isEmpty()) {
            return true;
        }
        List<VerticalStationConstraint> constraints = VerticalStationConstraints.build(
            environment,
            crossings,
            design,
            settings);
        for (int i = 0; i < design.size() && i < constraints.size(); i++) {
            Double minimum = constraints.get(i).minimumElevation();
            if (minimum == null) {
                continue;
            }
            if (design.get(i) + EPSILON < minimum) {
                if (flattenPolicy == FlattenPolicy.STRICT_MANUAL) {
                    if (i == 0 && manualStartHeight != null) {
                        return false;
                    }
                    if (i == design.size() - 1 && manualEndHeight != null) {
                        return false;
                    }
                }
                return false;
            }
        }
        return true;
    }
}
