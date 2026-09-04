package com.plot.plugin.building.preset;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.AccessorySpec;
import com.plot.plugin.building.model.spec.BalconySpec;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.FoundationSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.CanopySpec;
import com.plot.plugin.building.model.spec.ParapetSpec;
import com.plot.plugin.building.model.spec.RoofSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;

import java.util.List;

/**
 * 内置建筑类型 Preset 的参数模板（不含轮廓几何）。
 * <p>
 * 目录规模受 {@link BuildingPresetCatalog#MAX_BUILTIN_PRESETS} 冻结；勿为新业态追加模板。
 */
public final class BuildingPresetTemplates {
    private BuildingPresetTemplates() {
    }

    public static MassingSpec massing(int floors, int floorHeight) {
        // Preset 模板不含几何；真正构建时由 BuildingPresetCatalog 调用 MassingSpec.create(...)。
        throw new UnsupportedOperationException(
            "use MassingSpec.create(floors, floorHeight, footprint, plates)");
    }

    public static EnvelopeSpec envelope(int wallThickness, String wallMaterial, String floorMaterial) {
        return new EnvelopeSpec(
            wallThickness,
            MaterialMix.single(wallMaterial),
            MaterialMix.single(floorMaterial));
    }

    public static FacadeSpec facade(int spacing, int width, int height, int sillHeight) {
        return new FacadeSpec(
            new WindowPatternSpec(spacing, width, height, sillHeight),
            List.of(),
            List.of());
    }

    public static FacadeSpec facadeNoWindows() {
        return facade(0, 1, 2, 1);
    }

    public static RoofSpec flatRoof() {
        return new RoofSpec(BuildingFootprint.RoofType.FLAT, 4, "minecraft:gray_concrete");
    }

    public static RoofSpec gableRoof() {
        return new RoofSpec(BuildingFootprint.RoofType.GABLE, 3, "minecraft:dark_oak_planks");
    }

    public static FoundationSpec defaultFoundation() {
        return new FoundationSpec(BuildingFootprint.DEFAULT_FOUNDATION_FILL, null);
    }

    public static AccessorySpec none() {
        return AccessorySpec.none();
    }

    public static AccessorySpec parapet(int height) {
        return new AccessorySpec(
            new ParapetSpec(true, height, null),
            List.of(),
            List.of());
    }

    public static AccessorySpec apartmentBalconies() {
        return new AccessorySpec(
            ParapetSpec.disabled(),
            List.of(),
            List.of(
                new BalconySpec(0, 0.5, 1, 3, 2, null, null),
                new BalconySpec(2, 0.5, 1, 3, 2, null, null)
            ));
    }

    public static AccessorySpec entranceCanopy() {
        return new AccessorySpec(
            ParapetSpec.disabled(),
            List.of(new CanopySpec(0, 0.5, 0, 4, 2, 3, null)),
            List.of());
    }
}
