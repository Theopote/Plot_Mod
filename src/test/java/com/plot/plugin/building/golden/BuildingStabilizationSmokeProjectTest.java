package com.plot.plugin.building.golden;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出 stabilization smoke 项目并做生成前校验。
 */
class BuildingStabilizationSmokeProjectTest {

    @Test
    void smokeBuildingsGenerateNonEmptyMass() {
        BuildingProject project = BuildingStabilizationSmokeProject.create();
        Map<String, BuildingFootprint> byName = project.getBuildings().values().stream()
            .collect(Collectors.toMap(BuildingFootprint::getName, Function.identity()));

        for (BuildingStabilizationSmokeProject.SmokeBuilding smoke : BuildingStabilizationSmokeProject.BUILDINGS) {
            BuildingFootprint footprint = byName.get(smoke.name());
            assertNotNull(footprint, smoke.name());
            GoldenBuildingMetrics metrics = GoldenBuildingHarness.generate(footprint);

            assertTrue(metrics.totalBlocks() > 0, smoke.name() + " must not disappear");
            assertTrue(metrics.wallBlocks() > 0, smoke.name() + " must have wall mass");
        }

        GoldenBuildingMetrics b07 = GoldenBuildingHarness.generate(byName.get("B07 Narrow Corridor"));
        assertEquals(0, b07.floorBlocks());
        assertTrue(b07.warnings().contains("plugin.building.warn.inner_offset_failed"));
    }

    @Test
    void exportSmokeProjectForDevClient() throws Exception {
        Path devProjects = Path.of("run", "config", "plugins", "building", "projects");
        Path artifact = Path.of("build", "stabilization-smoke.building.json");

        BuildingStabilizationSmokeProject.exportTo(devProjects.resolve("stabilization-smoke.json"));
        BuildingStabilizationSmokeProject.exportTo(devProjects.resolve("default.json"));
        BuildingStabilizationSmokeProject.exportTo(artifact);

        BuildingProject loaded = BuildingProject.loadFrom(devProjects.resolve("default.json"));
        assertEquals(4, loaded.getBuildingCount());
        for (BuildingStabilizationSmokeProject.SmokeBuilding smoke : BuildingStabilizationSmokeProject.BUILDINGS) {
            assertTrue(
                loaded.getBuildings().values().stream()
                    .anyMatch(building -> smoke.name().equals(building.getName())),
                smoke.name());
        }
    }
}
