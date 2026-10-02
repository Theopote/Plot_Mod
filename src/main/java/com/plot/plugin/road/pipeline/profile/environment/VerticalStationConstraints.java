package com.plot.plugin.road.pipeline.profile.environment;

import java.util.ArrayList;
import java.util.List;

/** Builds per-station elevation bounds from environment, crossings, and terrain trend. */
public final class VerticalStationConstraints {

    private static final double EPSILON = 1e-9;

    private VerticalStationConstraints() {
    }

    public static List<VerticalStationConstraint> build(
            EnvironmentProfile environment,
            List<WaterCrossing> crossings,
            List<Double> trendElevations,
            WaterCrossingSettings settings) {
        if (environment == null || environment.samples().isEmpty()) {
            return List.of();
        }
        List<EnvironmentSample> samples = environment.samples();
        List<VerticalStationConstraint> constraints = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            EnvironmentSample sample = samples.get(i);
            double preferred = trendElevations != null && i < trendElevations.size()
                ? trendElevations.get(i)
                : sample.terrainY();
            constraints.add(resolveConstraint(sample, crossings, preferred, settings));
        }
        return List.copyOf(constraints);
    }

    private static VerticalStationConstraint resolveConstraint(
            EnvironmentSample sample,
            List<WaterCrossing> crossings,
            double preferred,
            WaterCrossingSettings settings) {
        WaterCrossing crossing = findCrossing(crossings, sample.station());
        if (crossing == null || !sample.hasWater()) {
            if (crossing != null && crossing.isInApproachZone(sample.station())) {
                return crossingTransitionConstraint(sample, crossing, preferred, settings);
            }
            if (crossing != null && crossing.isInExitZone(sample.station())) {
                return crossingTransitionConstraint(sample, crossing, preferred, settings);
            }
            return VerticalStationConstraint.land(preferred);
        }
        return waterConstraint(sample, crossing, preferred, settings);
    }

    private static VerticalStationConstraint waterConstraint(
            EnvironmentSample sample,
            WaterCrossing crossing,
            double preferred,
            WaterCrossingSettings settings) {
        WaterCrossingStrategy strategy = crossing.strategy();
        if (strategy == WaterCrossingStrategy.TUNNEL_CANDIDATE) {
            return new VerticalStationConstraint(
                preferred,
                null,
                null,
                SurfaceContext.DEEP_WATER);
        }
        double crossingMinimum = crossingTargetMinimumElevation(crossing, settings);
        double rampedMinimum = crossingMinimum;
        SurfaceContext context = sample.context();
        if (crossing.isInApproachZone(sample.station())) {
            rampedMinimum = rampMinimum(
                sample.station(),
                crossing.approachStartStation(),
                crossing.crossingStartStation(),
                crossing.entryBankTerrainY(),
                crossingMinimum);
            context = SurfaceContext.SHORE;
        } else if (crossing.isInExitZone(sample.station())) {
            rampedMinimum = rampMinimum(
                sample.station(),
                crossing.crossingEndStation(),
                crossing.exitEndStation(),
                crossingMinimum,
                crossing.exitBankTerrainY());
            context = SurfaceContext.SHORE;
        }
        return new VerticalStationConstraint(
            Math.max(preferred, crossingMinimum),
            rampedMinimum,
            null,
            context);
    }

    private static VerticalStationConstraint crossingTransitionConstraint(
            EnvironmentSample sample,
            WaterCrossing crossing,
            double preferred,
            WaterCrossingSettings settings) {
        double crossingMinimum = crossingTargetMinimumElevation(crossing, settings);
        double rampedMinimum;
        if (crossing.isInApproachZone(sample.station())) {
            rampedMinimum = rampMinimum(
                sample.station(),
                crossing.approachStartStation(),
                crossing.crossingStartStation(),
                crossing.entryBankTerrainY(),
                crossingMinimum);
        } else {
            rampedMinimum = rampMinimum(
                sample.station(),
                crossing.crossingEndStation(),
                crossing.exitEndStation(),
                crossingMinimum,
                crossing.exitBankTerrainY());
        }
        return new VerticalStationConstraint(
            Math.max(preferred, crossingMinimum),
            rampedMinimum,
            null,
            SurfaceContext.SHORE);
    }

    static double crossingTargetMinimumElevation(
            WaterCrossing crossing,
            WaterCrossingSettings settings) {
        if (crossing.strategy() == WaterCrossingStrategy.CAUSEWAY) {
            return crossing.waterSurfaceY();
        }
        return crossing.waterSurfaceY() + settings.waterRoadClearanceBlocks();
    }

    private static double rampMinimum(
            double station,
            double zoneStart,
            double zoneEnd,
            double startMinimum,
            double endMinimum) {
        double span = zoneEnd - zoneStart;
        if (span <= EPSILON) {
            return endMinimum;
        }
        double blend = (station - zoneStart) / span;
        blend = Math.clamp(blend, 0.0, 1.0);
        return startMinimum + (endMinimum - startMinimum) * blend;
    }

    private static WaterCrossing findCrossing(List<WaterCrossing> crossings, double station) {
        if (crossings == null) {
            return null;
        }
        for (WaterCrossing crossing : crossings) {
            if (crossing.containsStation(station)) {
                return crossing;
            }
        }
        return null;
    }

    public static StationElevationBounds toBounds(List<VerticalStationConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return StationElevationBounds.unbounded(0);
        }
        int count = constraints.size();
        double[] minimum = new double[count];
        double[] maximum = new double[count];
        for (int i = 0; i < count; i++) {
            VerticalStationConstraint constraint = constraints.get(i);
            minimum[i] = constraint.minimumElevation() != null
                ? constraint.minimumElevation()
                : Double.NaN;
            maximum[i] = constraint.maximumElevation() != null
                ? constraint.maximumElevation()
                : Double.NaN;
        }
        return new StationElevationBounds(minimum, maximum);
    }

    public record StationElevationBounds(double[] minimumElevations, double[] maximumElevations) {

        public static StationElevationBounds unbounded(int stationCount) {
            double[] min = new double[stationCount];
            double[] max = new double[stationCount];
            for (int i = 0; i < stationCount; i++) {
                min[i] = Double.NaN;
                max[i] = Double.NaN;
            }
            return new StationElevationBounds(min, max);
        }
    }
}
