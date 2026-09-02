#!/usr/bin/env python3
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HEADER = '''package com.plot.plugin.earthwork.manager;

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
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
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

/** 土方 ImGui 界面编排。 */
public final class EarthworkUIManager {
    private final EarthworkUiContext ctx;

    public EarthworkUIManager(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        if (ctx.config() == null) {
            return;
        }

        if (ctx.pickSession().isActive()) {
            handlePickSessionTick();
        }
        if (ctx.threePointPickSession().isActive()) {
            handleThreePointPickSessionTick();
        }

        renderToolbar();
        renderActivePlacementControls();

        if (ImGui.beginTabBar("##earthwork_tabs", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.overview"))) {
                renderOverviewTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.adopt"))) {
                renderAdoptTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.edit"))) {
                renderEditTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(PlotI18n.tr("plugin.earthwork.tab.generate"))) {
                renderGenerateTab();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    public void renderDeferredModals() {
        renderDeleteConfirmPopup();
        renderBuildConfirmPopup();
    }

'''

lines = subprocess.check_output(
    ['git', 'show', 'HEAD:src/main/java/com/plot/plugin/EarthworkPlugin.java'],
    cwd=ROOT,
    text=True,
    encoding='utf-8',
).splitlines()
body = '\n'.join(lines[261:2325])

replacements = [
    ('ctx()', 'ctx.host()'),
    ('projectHistory.', 'ctx.projectHistory().'),
    ('pickSession.', 'ctx.pickSession().'),
    ('threePointPickSession.', 'ctx.threePointPickSession().'),
    ('terrainSnapshotCache.', 'ctx.terrainSnapshotCache().'),
    ('previewManager.', 'ctx.previewManager().'),
    ('buildManager.', 'ctx.buildManager().'),
    ('selectedRegions.', 'ctx.selectedRegions().'),
    ('autoBalanceRef.', 'ctx.autoBalanceRef().'),
    ('showGridRef.', 'ctx.showGridRef().'),
    ('showEdgeTreatmentOverlayRef.', 'ctx.showEdgeTreatmentOverlayRef().'),
    ('regionNameBuffer.', 'ctx.regionNameBuffer().'),
    ('project.', 'ctx.project().'),
    ('config.', 'ctx.config().'),
]
for old, new in replacements:
    body = body.replace(old, new)

assigns = [
    ('project = projectHistory', 'ctx.setProject(ctx.projectHistory'),
    ('project = ', 'ctx.setProject('),
    ('selectedRegionId = ', 'ctx.setSelectedRegionId('),
    ('projectStatus = ', 'ctx.setProjectStatus('),
    ('regionNameEditingRegionId = ', 'ctx.setRegionNameEditingRegionId('),
    ('pendingDeleteRegionId = ', 'ctx.setPendingDeleteRegionId('),
    ('deleteConfirmPending = ', 'ctx.setDeleteConfirmPending('),
    ('buildConfirmPending = ', 'ctx.setBuildConfirmPending('),
    ('regionSortMode = ', 'ctx.setRegionSortMode('),
]
for old, new in assigns:
    body = body.replace(old, new)

def close_setter_calls(text: str) -> str:
    setters = (
        'setProjectStatus', 'setSelectedRegionId', 'setRegionNameEditingRegionId',
        'setPendingDeleteRegionId', 'setDeleteConfirmPending', 'setBuildConfirmPending',
        'setRegionSortMode', 'setProject',
    )
    for setter in setters:
        token = f'ctx.{setter}('
        start = 0
        while True:
            idx = text.find(token, start)
            if idx < 0:
                break
            i = idx + len(token)
            depth = 1
            while i < len(text) and depth > 0:
                ch = text[i]
                if ch == '(':
                    depth += 1
                elif ch == ')':
                    depth -= 1
                i += 1
            if depth != 0:
                start = idx + 1
                continue
            if i < len(text) and text[i] == ';':
                text = text[:i] + ')' + text[i:]
                start = i + 2
            else:
                start = i
    return text

def finalize_setters(text: str) -> str:
    replacements = [
        ('ctx.setRegionNameEditingRegionId("";', 'ctx.setRegionNameEditingRegionId("");'),
        ('ctx.setSelectedRegionId(region.getId();', 'ctx.setSelectedRegionId(region.getId());'),
        ('ctx.setPendingDeleteRegionId(region.getId();', 'ctx.setPendingDeleteRegionId(region.getId());'),
        ('ctx.setPendingDeleteRegionId("";', 'ctx.setPendingDeleteRegionId("");'),
        ('ctx.setRegionNameEditingRegionId(region.getId();', 'ctx.setRegionNameEditingRegionId(region.getId());'),
        ('ctx.setSelectedRegionId(ids[regionIndex.get()];', 'ctx.setSelectedRegionId(ids[regionIndex.get()]);'),
        ('ctx.setProject(ctx.projectHistory().undo(ctx.project());',
         'ctx.setProject(ctx.projectHistory().undo(ctx.project()));'),
        ('ctx.setProject(ctx.projectHistory().redo(ctx.project());',
         'ctx.setProject(ctx.projectHistory().redo(ctx.project()));'),
        ('ctx.setRegionSortMode(mode;', 'ctx.setRegionSortMode(mode);'),
        ('ctx.setDeleteConfirmPending(true;', 'ctx.setDeleteConfirmPending(true);'),
        ('ctx.setDeleteConfirmPending(false;', 'ctx.setDeleteConfirmPending(false);'),
        ('ctx.setBuildConfirmPending(true;', 'ctx.setBuildConfirmPending(true);'),
        ('ctx.setBuildConfirmPending(false;', 'ctx.setBuildConfirmPending(false);'),
    ]
    for old, new in replacements:
        text = text.replace(old, new)
    text = re.sub(
        r'ctx\.setProjectStatus\(PlotI18n\.tr\(([^;]+)\);',
        r'ctx.setProjectStatus(PlotI18n.tr(\1));',
        text,
    )
    text = text.replace(
        '            : PlotI18n.tr("plugin.earthwork.adopt_success");\n    }',
        '            : PlotI18n.tr("plugin.earthwork.adopt_success"));\n    }',
    )
    text = text.replace(
        '                        ctx.setSelectedRegionId(ctx.project().getRegions().isEmpty()\n'
        + '                            ? ""\n'
        + '                            : ctx.project().getRegions().keySet().iterator().next();\n',
        '                        ctx.setSelectedRegionId(ctx.project().getRegions().isEmpty()\n'
        + '                            ? ""\n'
        + '                            : ctx.project().getRegions().keySet().iterator().next());\n',
    )
    return text

for _ in range(3):
    body = close_setter_calls(body)

body = body.replace('ctx.projectStatus() =', 'ctx.setProjectStatus(')
body = finalize_setters(body)

body = body.replace(
    'ctx.setProject(ctx.projectHistory().undo(ctx.project());',
    'ctx.setProject(ctx.projectHistory().undo(ctx.project()));')
body = body.replace(
    'ctx.setProject(ctx.projectHistory().redo(ctx.project());',
    'ctx.setProject(ctx.projectHistory().redo(ctx.project()));')

read_map = {
    'selectedRegionId': 'ctx.selectedRegionId()',
    'projectStatus': 'ctx.projectStatus()',
    'regionNameEditingRegionId': 'ctx.regionNameEditingRegionId()',
    'pendingDeleteRegionId': 'ctx.pendingDeleteRegionId()',
    'deleteConfirmPending': 'ctx.deleteConfirmPending()',
    'buildConfirmPending': 'ctx.buildConfirmPending()',
    'regionSortMode': 'ctx.regionSortMode()',
    'autoBalanceRef': 'ctx.autoBalanceRef()',
    'showGridRef': 'ctx.showGridRef()',
    'selectedRegions': 'ctx.selectedRegions()',
    'project': 'ctx.project()',
    'config': 'ctx.config()',
}
for name, replacement in read_map.items():
    body = re.sub(r'(?<![\w.])' + re.escape(name) + r'(?![\w(])', replacement, body)

# fix mistaken ctx.ctx
body = body.replace('ctx.ctx.', 'ctx.')

body = re.sub(
    r'private void clearPreview\(\) \{[^}]+\}',
    'private void clearPreview() { ctx.clearPreview(); }',
    body,
    count=1,
)
body = re.sub(
    r'private void invalidatePreview\(\) \{[^}]+\}',
    'private void invalidatePreview() { ctx.invalidatePreview(); }',
    body,
    count=1,
)

out = HEADER + body + '\n}\n'
out_path = ROOT / 'src/main/java/com/plot/plugin/earthwork/manager/EarthworkUIManager.java'
out_path.write_text(out, encoding='utf-8')
print(f'Wrote {out_path} ({len(out)} chars)')
