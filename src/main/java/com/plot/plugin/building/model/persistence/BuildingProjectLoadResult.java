package com.plot.plugin.building.model.persistence;

import com.plot.plugin.building.model.BuildingProject;

import java.util.List;

/**
 * 建筑项目 JSON 加载结果，含被跳过的无效栋诊断。
 */
public final class BuildingProjectLoadResult {
    private final BuildingProject project;
    private final List<BuildingLoadDiagnostic> skippedBuildings;

    public BuildingProjectLoadResult(BuildingProject project, List<BuildingLoadDiagnostic> skippedBuildings) {
        this.project = project != null ? project : new BuildingProject();
        this.skippedBuildings = skippedBuildings != null
            ? List.copyOf(skippedBuildings)
            : List.of();
    }

    public static BuildingProjectLoadResult empty() {
        return new BuildingProjectLoadResult(new BuildingProject(), List.of());
    }

    public BuildingProject project() {
        return project;
    }

    public List<BuildingLoadDiagnostic> skippedBuildings() {
        return skippedBuildings;
    }

    public boolean hasSkippedBuildings() {
        return !skippedBuildings.isEmpty();
    }

    public int skippedBuildingCount() {
        return skippedBuildings.size();
    }

    /**
     * 单栋被跳过的加载诊断。
     *
     * @param reasonKey i18n key，如 {@code plugin.building.load.skip_insufficient_outer_points}
     */
    public record BuildingLoadDiagnostic(String id, String name, String reasonKey) {
        public BuildingLoadDiagnostic {
            reasonKey = reasonKey != null && !reasonKey.isBlank()
                ? reasonKey
                : "plugin.building.load.skip_unknown";
        }

        public String displayLabel() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            if (id != null && !id.isBlank()) {
                return id;
            }
            return "?";
        }
    }
}
