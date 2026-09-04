package com.plot.plugin.building.preset;

import com.plot.plugin.building.model.spec.AccessorySpec;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.FootprintSpec;
import com.plot.plugin.building.model.spec.FoundationSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.RoofSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 建筑类型 Preset 目录：Preset → 默认 {@link BuildingDefinition}（保留轮廓几何）。
 */
public final class BuildingPresetCatalog {
    private BuildingPresetCatalog() {
    }

    public record BuildingPreset(
            String id,
            Function<FootprintSpec, BuildingDefinition> definitionFactory) {

        public BuildingDefinition build(FootprintSpec footprint) {
            Objects.requireNonNull(footprint, "footprint");
            return definitionFactory.apply(footprint);
        }
    }

    private static final Map<String, BuildingPreset> PRESETS = buildPresets();

    public static List<BuildingPreset> all() {
        return List.copyOf(PRESETS.values());
    }

    public static BuildingPreset find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return PRESETS.get(id.trim());
    }

    public static BuildingDefinition buildDefinition(String presetId, FootprintSpec footprint) {
        BuildingPreset preset = find(presetId);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown building preset: " + presetId);
        }
        return preset.build(footprint);
    }

    private static Map<String, BuildingPreset> buildPresets() {
        Map<String, BuildingPreset> presets = new LinkedHashMap<>();
        presets.put("residential_lowrise", preset(
            "residential_lowrise",
            residentialLowrise()));
        presets.put("apartment", preset("apartment", apartment()));
        presets.put("office", preset("office", office()));
        presets.put("warehouse", preset("warehouse", warehouse()));
        presets.put("school", preset("school", school()));
        presets.put("commercial", preset("commercial", commercial()));
        presets.put("tower", preset("tower", tower()));
        presets.put("villa", preset("villa", villa()));
        presets.put("industrial", preset("industrial", industrial()));
        return Collections.unmodifiableMap(presets);
    }

    private static BuildingPreset preset(String id, PresetParts parts) {
        return new BuildingPreset(id, footprint -> new BuildingDefinition(
            footprint,
            MassingSpec.create(
                parts.floors(),
                parts.floorHeight(),
                footprint.outerPoints(),
                List.of()),
            parts.envelope(),
            parts.facade(),
            parts.roof(),
            parts.foundation(),
            parts.accessory()));
    }

    private record PresetParts(
            int floors,
            int floorHeight,
            EnvelopeSpec envelope,
            FacadeSpec facade,
            RoofSpec roof,
            FoundationSpec foundation,
            AccessorySpec accessory) {
    }

    private static PresetParts residentialLowrise() {
        return new PresetParts(
            2, 3,
            BuildingPresetTemplates.envelope(1, "minecraft:bricks", "minecraft:oak_planks"),
            BuildingPresetTemplates.facade(4, 1, 2, 1),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.none());
    }

    private static PresetParts apartment() {
        return new PresetParts(
            6, 3,
            BuildingPresetTemplates.envelope(1, "minecraft:stone_bricks", "minecraft:oak_planks"),
            BuildingPresetTemplates.facade(3, 1, 2, 1),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.apartmentBalconies());
    }

    private static PresetParts office() {
        return new PresetParts(
            8, 4,
            BuildingPresetTemplates.envelope(1, "minecraft:quartz_block", "minecraft:smooth_stone"),
            BuildingPresetTemplates.facade(2, 2, 3, 1),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.parapet(1));
    }

    private static PresetParts warehouse() {
        return new PresetParts(
            1, 7,
            BuildingPresetTemplates.envelope(2, "minecraft:iron_block", "minecraft:smooth_stone"),
            BuildingPresetTemplates.facadeNoWindows(),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.none());
    }

    private static PresetParts school() {
        return new PresetParts(
            3, 4,
            BuildingPresetTemplates.envelope(1, "minecraft:stone_bricks", "minecraft:oak_planks"),
            BuildingPresetTemplates.facade(3, 2, 3, 1),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.entranceCanopy());
    }

    private static PresetParts commercial() {
        return new PresetParts(
            2, 4,
            BuildingPresetTemplates.envelope(1, "minecraft:stone_bricks", "minecraft:polished_andesite"),
            BuildingPresetTemplates.facade(2, 2, 3, 0),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.entranceCanopy());
    }

    private static PresetParts tower() {
        return new PresetParts(
            12, 3,
            BuildingPresetTemplates.envelope(1, "minecraft:quartz_block", "minecraft:gray_concrete"),
            BuildingPresetTemplates.facade(3, 1, 2, 1),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.parapet(2));
    }

    private static PresetParts villa() {
        return new PresetParts(
            2, 4,
            BuildingPresetTemplates.envelope(1, "minecraft:brick", "minecraft:oak_planks"),
            BuildingPresetTemplates.facade(5, 1, 2, 1),
            BuildingPresetTemplates.gableRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.none());
    }

    private static PresetParts industrial() {
        return new PresetParts(
            1, 6,
            BuildingPresetTemplates.envelope(2, "minecraft:deepslate_bricks", "minecraft:polished_deepslate"),
            BuildingPresetTemplates.facade(8, 1, 2, 2),
            BuildingPresetTemplates.flatRoof(),
            BuildingPresetTemplates.defaultFoundation(),
            BuildingPresetTemplates.none());
    }

    /** Preset id 列表，供 UI 与 AI 工具链使用。 */
    public static List<String> ids() {
        return new ArrayList<>(PRESETS.keySet());
    }
}
