package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import com.plot.core.plugin.PluginManager;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.BuildingPlugin;
import com.plot.plugin.RoadSystemPlugin;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.*;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


/** 土方 UI 建筑/道路查询辅助。 */
public final class EarthworkUiLookups {
    private EarthworkUiLookups() {
    }

        public static List<BuildingFootprint> listAvailableBuildings() {
            com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("building");
            if (plugin instanceof BuildingPlugin buildingPlugin) {
                return buildingPlugin.listBuildingFootprints();
            }
            return List.of();
        }

        public static BuildingFootprintLookup createBuildingFootprintLookup() {
            return id -> {
                if (id == null || id.isBlank()) {
                    return null;
                }
                com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("building");
                if (plugin instanceof BuildingPlugin buildingPlugin) {
                    return buildingPlugin.getBuildingFootprint(id);
                }
                return null;
            };
        }

        public static RoadSurfaceLookup createRoadSurfaceLookup() {
            return (edgeId, planPoint) -> {
                if (edgeId == null || edgeId.isBlank() || planPoint == null) {
                    return null;
                }
                com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("road_system");
                if (plugin instanceof RoadSystemPlugin roadPlugin) {
                    return roadPlugin.sampleEarthworkDesignY(edgeId, planPoint);
                }
                return null;
            };
        }

        public static List<RoadEarthworkSurfaceSampler.EdgeRef> listAvailableRoadEdges() {
            com.plot.api.plugin.IPlugin plugin = PluginManager.getInstance().getPlugin("road_system");
            if (plugin instanceof RoadSystemPlugin roadPlugin) {
                return roadPlugin.listEarthworkRoadEdges();
            }
            return List.of();
        }
}
