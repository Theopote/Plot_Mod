package com.plot.plugin.building.overlay;

import java.util.Set;

/** 生成 Preview 在画布叠加层上的诊断高亮（与 Ghost 预览无关）。 */
public record BuildingOverlayDiagnostics(Set<String> previewedBuildingIds, Set<String> warningBuildingIds) {
    public static final BuildingOverlayDiagnostics EMPTY = new BuildingOverlayDiagnostics(Set.of(), Set.of());

    public static BuildingOverlayDiagnostics of(Set<String> previewed, Set<String> warnings) {
        if ((previewed == null || previewed.isEmpty()) && (warnings == null || warnings.isEmpty())) {
            return EMPTY;
        }
        return new BuildingOverlayDiagnostics(
            previewed != null ? Set.copyOf(previewed) : Set.of(),
            warnings != null ? Set.copyOf(warnings) : Set.of());
    }
}
