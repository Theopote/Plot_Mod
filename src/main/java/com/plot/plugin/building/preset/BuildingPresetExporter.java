package com.plot.plugin.building.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.plot.plugin.building.model.spec.BuildingDefinition;

/**
 * 将 {@link BuildingDefinition} 导出为 AI / 外部工具可消费的紧凑 JSON 摘要。
 */
public final class BuildingPresetExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BuildingPresetExporter() {
    }

    public static String toJsonSummary(BuildingDefinition definition) {
        if (definition == null) {
            return "{}";
        }
        JsonObject root = new JsonObject();
        root.addProperty("id", definition.id());
        root.addProperty("floors", definition.massing().floors());
        root.addProperty("floorHeight", definition.massing().floorHeight());
        root.addProperty("wallThickness", definition.envelope().wallThickness());
        root.addProperty("windowSpacing", definition.facade().defaultWindowPattern().spacing());
        root.addProperty("windowWidth", definition.facade().defaultWindowPattern().width());
        root.addProperty("windowHeight", definition.facade().defaultWindowPattern().height());
        root.addProperty("windowSillHeight", definition.facade().defaultWindowPattern().sillHeight());
        root.addProperty("roofType", definition.roof().type().name());
        root.addProperty("roofPitchRatio", definition.roof().pitchRatio());
        root.addProperty("parapet", definition.accessory().parapet().enabled());
        root.addProperty("balconyCount", definition.accessory().balconies().size());
        root.addProperty("canopyCount", definition.accessory().canopies().size());
        root.addProperty("openingCount", definition.facade().openings().size());
        return GSON.toJson(root);
    }
}
