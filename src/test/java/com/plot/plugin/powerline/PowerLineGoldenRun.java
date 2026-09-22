package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 单次 Golden 场景运行产物。 */
public final class PowerLineGoldenRun {
    private final PowerLineGoldenScenario scenario;
    private final PowerLineFootprint footprint;
    private final PowerLineGenerationResult result;
    private final PowerLineGoldenMetrics metrics;

    PowerLineGoldenRun(
            PowerLineGoldenScenario scenario,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            PowerLineGoldenMetrics metrics) {
        this.scenario = scenario;
        this.footprint = footprint;
        this.result = result;
        this.metrics = metrics;
    }

    public PowerLineGoldenScenario scenario() {
        return scenario;
    }

    public PowerLineFootprint footprint() {
        return footprint;
    }

    public PowerLineGenerationResult result() {
        return result;
    }

    public PowerLineGoldenMetrics metrics() {
        return metrics;
    }
}
