package com.plot.plugin.building;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.model.BuildingFootprint;
import java.util.Collection;
import java.util.Objects;

/**
 * District Massing Phase B：将主建筑的体量/样式参数批量应用到选中建筑。
 * <p>
 * 不改轮廓几何。
 */
public final class BuildingBatchEditor {

    private BuildingBatchEditor() {
    }

    /**
     * 哪些字段从源建筑复制到目标。全 false 时 {@link #apply} 不改任何栋。
     */
    public static final class FieldMask {
        public boolean floors = true;
        public boolean floorHeight = true;
        public boolean wallThickness = true;
        public boolean materials = true;
        public boolean roof = true;
        public boolean windows = true;

        public static FieldMask allMassing() {
            return new FieldMask();
        }

        public boolean anyEnabled() {
            return floors || floorHeight || wallThickness || materials || roof || windows;
        }
    }

    public record ApplyResult(int updated, int skipped) {
        public int total() {
            return updated + skipped;
        }
    }

    /**
     * 将 {@code source} 上 mask 启用的字段复制到 {@code targets}（含 source 自身时跳过无变化写入仍计 updated）。
     */
    public static ApplyResult apply(
            BuildingFootprint source,
            Collection<BuildingFootprint> targets,
            FieldMask mask) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mask, "mask");
        if (targets == null || targets.isEmpty() || !mask.anyEnabled()) {
            return new ApplyResult(0, targets == null ? 0 : targets.size());
        }

        int updated = 0;
        int skipped = 0;
        for (BuildingFootprint target : targets) {
            if (target == null) {
                skipped++;
                continue;
            }
            copyFields(source, target, mask);
            updated++;
        }
        return new ApplyResult(updated, skipped);
    }

    private static void copyFields(BuildingFootprint source, BuildingFootprint target, FieldMask mask) {
        if (mask.floors) {
            target.setFloors(source.getFloors());
        }
        if (mask.floorHeight) {
            target.setFloorHeight(source.getFloorHeight());
        }
        if (mask.wallThickness) {
            target.setWallThickness(source.getWallThickness());
        }
        if (mask.materials) {
            MaterialMix wall = source.getWallMaterial();
            MaterialMix floor = source.getFloorMaterial();
            target.setWallMaterial(wall != null ? wall.copy() : null);
            target.setFloorMaterial(floor != null ? floor.copy() : null);
            target.setRoofMaterial(source.getRoofMaterial());
            target.setFoundationFillMaterial(source.getFoundationFillMaterial());
            target.setWindowMaterial(source.getWindowMaterial());
            target.setBalconySlabMaterial(source.getBalconySlabMaterial());
        }
        if (mask.roof) {
            target.setRoofType(source.getRoofType());
            target.setRoofPitchRatio(source.getRoofPitchRatio());
            target.setRoofEaves(source.getRoofEaves());
        }
        if (mask.windows) {
            target.setWindowsEnabled(source.isWindowsEnabled());
            target.setWindowWidth(source.getWindowWidth());
            target.setWindowPierWidth(source.getWindowPierWidth());
            target.setWindowHeight(source.getWindowHeight());
            target.setWindowSillHeight(source.getWindowSillHeight());
            target.setWindowBalconyDepth(source.getWindowBalconyDepth());
            target.setBalconySlabMaterial(source.getBalconySlabMaterial());
        }
    }
}
