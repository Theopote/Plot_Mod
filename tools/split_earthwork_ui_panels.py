#!/usr/bin/env python3
"""将 EarthworkUIManager 拆分为 earthwork/ui/*Panel。"""
from __future__ import annotations

import re
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/plot/plugin/earthwork/manager/EarthworkUIManager.java"
UI_DIR = ROOT / "src/main/java/com/plot/plugin/earthwork/ui"

IMPORTS = """\
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
"""

METHOD_GROUPS: dict[str, list[str]] = {
    "EarthworkToolbarPanel": ["renderToolbar", "renderActivePlacementControls"],
    "EarthworkOverviewPanel": [
        "renderOverviewTab",
        "renderSiteOverlapWarnings",
        "renderDeleteConfirmPopup",
    ],
    "EarthworkAdoptPanel": [
        "renderAdoptTab",
        "updateSelectedRegions",
        "startPickSession",
        "handlePickSessionTick",
        "adoptSelectedRegions",
    ],
    "EarthworkEditPanel": [
        "renderEditTab",
        "renderSurfaceModeSettings",
        "renderMatchExistingSettings",
        "renderMultiPlaneSettings",
        "renderZoneTypeSettings",
        "renderPhaseCZoneSettings",
        "renderZoneEdgeSettings",
        "hasRetainingWallEdgeOverride",
        "renderEdgeTreatmentLegend",
        "renderBoundaryEdgeOverrides",
        "setEdgeOverride",
        "renderSelectedZoneOverlapWarnings",
        "renderBalanceScopeSettings",
        "renderOverlapResolutionSettings",
        "renderBalanceMethodSettings",
        "renderCompositionSettings",
        "renderRetainingEdgeSettings",
        "renderRegionGeometrySettings",
        "renderExclusionZoneSettings",
        "renderExclusionZoneRow",
        "addHoleToRegion",
        "addHoleToExclusion",
        "addExclusionFromSelection",
        "extractRegionOutlineFromSelection",
        "extractBreaklinePointsFromSelection",
        "renderRoadCorridorSettings",
        "importRoadCorridorOutline",
        "importRoadCenterlineBreakline",
        "bakeRoadCorridorElevations",
        "renderBreaklineRow",
        "indexOfZone",
        "renderBuildingPadSettings",
        "renderExcavationPitSettings",
        "renderMaterialPropertiesSettings",
        "renderFlatSurfaceSettings",
        "renderFixedSlopeSettings",
        "renderThreePointSurfaceSettings",
        "renderFitSlopeSettings",
        "initializeSurfaceDefaults",
        "renderGlobalGridSettings",
        "startThreePointPick",
        "handleThreePointPickSessionTick",
    ],
    "EarthworkGeneratePanel": [
        "renderGenerateTab",
        "renderGridPreview",
        "renderBuildConfirmPopup",
        "renderTerrainSnapshotInfo",
        "formatTerrainSnapshotTime",
        "renderProjectBalanceReport",
        "resolveAllocationEndpoint",
    ],
    "EarthworkUiWidgets": [
        "renderRegionSelector",
        "renderMaterialButton",
        "locateRegion",
        "syncSelectedRegionAfterHistory",
        "getClientWorld",
    ],
    "EarthworkUiLookups": [
        "listAvailableBuildings",
        "createBuildingFootprintLookup",
        "createRoadSurfaceLookup",
        "listAvailableRoadEdges",
    ],
}

RENDER_RENAMES: dict[str, dict[str, str]] = {
    "EarthworkAdoptPanel": {"handlePickSessionTick": "tickPickSession"},
    "EarthworkEditPanel": {"handleThreePointPickSessionTick": "tickThreePointPickSession"},
}

RENDER_PUBLIC: dict[str, list[str]] = {
    "EarthworkOverviewPanel": ["renderDeleteConfirmPopup"],
    "EarthworkGeneratePanel": ["renderBuildConfirmPopup"],
}

TAB_RENDER_METHODS = {
    "renderOverviewTab",
    "renderAdoptTab",
    "renderEditTab",
    "renderGenerateTab",
}


def parse_methods(source: str) -> dict[str, str]:
    lines = source.splitlines()
    methods: dict[str, str] = {}
    i = 0
    while i < len(lines):
        m = re.match(
            r"    private (static )?"
            r"(?:void|String|List<[^>]+>|BuildingFootprintLookup|RoadSurfaceLookup|boolean|int|World) "
            r"(\w+)\(",
            lines[i],
        )
        if not m:
            i += 1
            continue

        name = m.group(2)
        start = i
        depth = 0
        started = False
        while i < len(lines):
            line = lines[i]
            if not started:
                if "{" in line:
                    started = True
            if started:
                depth += line.count("{") - line.count("}")
                if depth <= 0:
                    methods[name] = "\n".join(lines[start : i + 1])
                    break
            i += 1
        i += 1

    return methods


def transform_body(body: str, panel: str) -> str:
    if panel in {"EarthworkUiWidgets", "EarthworkUiLookups"}:
        return body

    body = body.replace("clearPreview()", "ctx.clearPreview()")
    body = body.replace("invalidatePreview()", "ctx.invalidatePreview()")
    body = body.replace("renderRegionSelector()", "EarthworkUiWidgets.renderRegionSelector(ctx)")
    body = body.replace(
        "renderMaterialButton(", "EarthworkUiWidgets.renderMaterialButton(ctx, "
    )
    body = body.replace("locateRegion(", "EarthworkUiWidgets.locateRegion(ctx, ")
    body = body.replace(
        "syncSelectedRegionAfterHistory()",
        "EarthworkUiWidgets.syncSelectedRegionAfterHistory(ctx)",
    )
    body = body.replace("getClientWorld()", "EarthworkUiWidgets.getClientWorld()")
    body = body.replace(
        "createBuildingFootprintLookup()",
        "EarthworkUiLookups.createBuildingFootprintLookup()",
    )
    body = body.replace(
        "createRoadSurfaceLookup()", "EarthworkUiLookups.createRoadSurfaceLookup()"
    )
    body = body.replace(
        "listAvailableBuildings()", "EarthworkUiLookups.listAvailableBuildings()"
    )
    body = body.replace(
        "listAvailableRoadEdges()", "EarthworkUiLookups.listAvailableRoadEdges()"
    )

  # drop wrapper helpers if present
    body = re.sub(
        r"\n    private void clearPreview\(\) \{ ctx\.clearPreview\(\); \}\n", "\n", body
    )
    body = re.sub(
        r"\n    private void invalidatePreview\(\) \{ ctx\.invalidatePreview\(\); \}\n",
        "\n",
        body,
    )

    renames = RENDER_RENAMES.get(panel, {})
    for old, new in renames.items():
        body = body.replace(f"private void {old}(", f"public void {new}(")

    for method in RENDER_PUBLIC.get(panel, []):
        body = body.replace(f"private void {method}(", f"public void {method}(")

    return body


def wrap_panel(panel: str, methods: dict[str, str], method_names: list[str]) -> str:
    bodies: list[str] = []
    tab_body = None

    for method_name in method_names:
        raw = methods[method_name]
        transformed = transform_body(raw, panel)

        if method_name in TAB_RENDER_METHODS:
            tab_body = transformed
            continue

        if panel == "EarthworkUiWidgets" or panel == "EarthworkUiLookups":
            transformed = transformed.replace("private ", "public static ", 1)
            if panel == "EarthworkUiWidgets":
                if method_name in {
                    "renderRegionSelector",
                    "syncSelectedRegionAfterHistory",
                }:
                    transformed = re.sub(
                        r"public static void (\w+)\(\)",
                        r"public static void \1(EarthworkUiContext ctx)",
                        transformed,
                        count=1,
                    )
                elif method_name in {"renderMaterialButton", "locateRegion"}:
                    transformed = re.sub(
                        r"public static void (\w+)\(",
                        r"public static void \1(EarthworkUiContext ctx, ",
                        transformed,
                        count=1,
                    )
            bodies.append(transformed)
            continue

        bodies.append(transformed.replace("private ", "private ", 1))

    if panel in {"EarthworkUiWidgets", "EarthworkUiLookups"}:
        class_doc = {
            "EarthworkUiWidgets": "土方 UI 共享控件与辅助方法。",
            "EarthworkUiLookups": "土方 UI 建筑/道路查询辅助。",
        }[panel]
        indented = "\n\n".join(
            "\n".join("    " + line if line else "" for line in block.splitlines())
            for block in bodies
        )
        return f"""package com.plot.plugin.earthwork.ui;

{IMPORTS}

/** {class_doc} */
public final class {panel} {{
    private {panel}() {{
    }}

{indented}
}}
"""

    render_method = ""
    if tab_body:
        inner = re.sub(
            r"private void render\w+Tab\(\) \{", "", tab_body, count=1
        ).rstrip()
        if inner.endswith("    }"):
            inner = inner[: -len("    }")].rstrip()
        render_method = f"""
    public void render() {{
{textwrap.indent(inner.strip(), '        ')}
    }}
"""

    extra_public = ""
    if panel == "EarthworkToolbarPanel":
        render_method = """
    public void render() {
        renderToolbar();
        renderActivePlacementControls();
    }
"""

    class_docs = {
        "EarthworkToolbarPanel": "土方插件顶部工具栏与落地进度控制。",
        "EarthworkOverviewPanel": "土方总览 Tab：区域列表、重叠警告与删除确认。",
        "EarthworkAdoptPanel": "土方认领 Tab：从画布选区认领 grading 区域。",
        "EarthworkEditPanel": "土方编辑 Tab：区域几何、分区与坡面设置。",
        "EarthworkGeneratePanel": "土方生成 Tab：预览计算、网格示意与落地确认。",
    }

    method_blocks = "\n\n".join(bodies)
    return f"""package com.plot.plugin.earthwork.ui;

{IMPORTS}

/** {class_docs[panel]} */
public final class {panel} {{
    private final EarthworkUiContext ctx;

    public {panel}(EarthworkUiContext ctx) {{
        this.ctx = ctx;
    }}
{render_method}
{method_blocks}
}}
"""


def write_ui_manager() -> None:
    content = '''package com.plot.plugin.earthwork.manager;

import com.plot.plugin.earthwork.ui.EarthworkAdoptPanel;
import com.plot.plugin.earthwork.ui.EarthworkEditPanel;
import com.plot.plugin.earthwork.ui.EarthworkGeneratePanel;
import com.plot.plugin.earthwork.ui.EarthworkOverviewPanel;
import com.plot.plugin.earthwork.ui.EarthworkToolbarPanel;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;

/** 土方 ImGui 界面编排。 */
public final class EarthworkUIManager {
    private final EarthworkUiContext ctx;
    private final EarthworkToolbarPanel toolbarPanel;
    private final EarthworkOverviewPanel overviewPanel;
    private final EarthworkAdoptPanel adoptPanel;
    private final EarthworkEditPanel editPanel;
    private final EarthworkGeneratePanel generatePanel;

    public EarthworkUIManager(EarthworkUiContext ctx) {
        this.ctx = ctx;
        this.toolbarPanel = new EarthworkToolbarPanel(ctx);
        this.overviewPanel = new EarthworkOverviewPanel(ctx);
        this.adoptPanel = new EarthworkAdoptPanel(ctx);
        this.editPanel = new EarthworkEditPanel(ctx);
        this.generatePanel = new EarthworkGeneratePanel(ctx);
    }

    public void render() {
        if (ctx.config() == null) {
            return;
        }

        if (ctx.pickSession().isActive()) {
            adoptPanel.tickPickSession();
        }
        if (ctx.threePointPickSession().isActive()) {
            editPanel.tickThreePointPickSession();
        }

        toolbarPanel.render();

        if (ImGui.beginTabBar("##earthwork_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.overview"))) {
                overviewPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.adopt"))) {
                adoptPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.edit"))) {
                editPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.generate"))) {
                generatePanel.render();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    public void renderDeferredModals() {
        overviewPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
    }
}
'''
    out = ROOT / "src/main/java/com/plot/plugin/earthwork/manager/EarthworkUIManager.java"
    out.write_text(content, encoding="utf-8")
    print(f"Wrote {out}")


def main() -> None:
    source = SRC.read_text(encoding="utf-8")
    methods = parse_methods(source)

    assigned = {m for group in METHOD_GROUPS.values() for m in group}
    missing = assigned - set(methods)
    if missing:
        raise SystemExit(f"Missing methods: {sorted(missing)}")

    UI_DIR.mkdir(parents=True, exist_ok=True)
    for panel, names in METHOD_GROUPS.items():
        out = UI_DIR / f"{panel}.java"
        out.write_text(wrap_panel(panel, methods, names), encoding="utf-8")
        print(f"Wrote {out}")

    write_ui_manager()


if __name__ == "__main__":
    main()
