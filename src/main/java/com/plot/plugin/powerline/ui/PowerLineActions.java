package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.PowerLineGenerateCommand;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.core.tool.BaseTool;
import com.plot.core.tool.ToolManager;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.PowerLinePathUtils;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.road.terrain.MinecraftTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.ui.canvas.Canvas;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 电力线路插件应用层动作。 */
public final class PowerLineActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PowerLineActions");

    private final PluginContext host;
    private final PowerLinePluginState state;
    private final Object projectLock;
    private PowerLineGenerator generator;

    public PowerLineActions(PluginContext host, PowerLinePluginState state, Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
    }

    public void setGenerator(PowerLineGenerator generator) {
        this.generator = generator;
    }

    public void updateSelectedPaths() {
        state.getSelectedPaths().clear();
        state.getSelectedPaths().addAll(
            PowerLinePathUtils.findAdoptableLines(host.appState().getSelectedShapes()));
    }

    public void adoptSelectedPaths() {
        if (state.getSelectedPaths().isEmpty()) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.adopt_no_selection"));
            return;
        }

        state.getProjectHistory().push(state.getProject());
        int adopted = 0;
        int skipped = 0;
        List<String> adoptedIds = new ArrayList<>();
        boolean curveRejected = false;

        for (Shape shape : state.getSelectedPaths()) {
            if (PowerLinePathUtils.isRejectedCurve(shape)) {
                skipped++;
                curveRejected = true;
                continue;
            }
            if (!PowerLinePathUtils.isAdoptableLine(shape)) {
                skipped++;
                continue;
            }
            try {
                List<Vec2d> points = PowerLinePathUtils.extractPathPoints(shape);
                PowerLineFootprint line = new PowerLineFootprint(points);
                line.setName(PlotI18n.tr("plugin.powerline.default_name", adopted + 1));
                state.getProject().addLine(line);
                adoptedIds.add(line.getId());
                adopted++;
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }

        state.getSelectedPaths().clear();
        if (adopted > 0) {
            state.getSelection().selectAll(adoptedIds);
            clearPreview();
        }
        if (adopted == 0) {
            state.setProjectStatus(curveRejected
                ? PlotI18n.tr("plugin.powerline.adopt_reject_curve")
                : PlotI18n.tr("plugin.powerline.adopt_no_selection"));
        } else if (skipped > 0) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.adopt_success_partial", adopted, skipped));
        } else if (adopted > 1) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.adopt_success_batch", adopted));
        } else {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.adopt_success"));
        }
    }

    public boolean calculatePreview(PowerLineFootprint line) {
        World world = getClientWorld();
        if (world == null || generator == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_world_unavailable"));
            return false;
        }
        if (line == null) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.select_line_hint"));
            return false;
        }

        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }

        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        PowerLineGenerationResult result;
        try {
            result = generator.generate(line, terrain, designResolver());
        } catch (Exception e) {
            LOGGER.error("电力线路预览生成失败: {}", e.getMessage(), e);
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_empty_result"));
            return false;
        }

        if (result.blockCount() == 0) {
            state.setLastGenerationResult(null);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.generate_empty_result"));
            return false;
        }

        state.setLastGenerationResult(result);
        projectGhosts(result);
        state.setProjectStatus(PlotI18n.tr(
            "plugin.powerline.preview_ready",
            result.poleCount,
            String.format("%.1f", result.wireLength),
            result.warnings.size()));
        return true;
    }

    private void projectGhosts(PowerLineGenerationResult result) {
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager == null) {
            return;
        }
        java.util.LinkedHashMap<net.minecraft.util.math.BlockPos, String> ghosts = new java.util.LinkedHashMap<>();
        for (BlockRecord record : result.placementRecords.values()) {
            ghosts.put(record.pos, record.newBlockId);
        }
        ghostBlockManager.addGhostBlocks(ghosts);
    }

    public void clearPreview() {
        com.plot.api.world.IGhostBlockService ghostBlockManager = host.ghosts();
        if (ghostBlockManager != null) {
            ghostBlockManager.clearAllGhostBlocks();
        }
        state.setLastGenerationResult(null);
    }

    public void buildInWorld() {
        PowerLineGenerationResult resultSnapshot;
        synchronized (projectLock) {
            PowerLineGenerationResult last = state.getLastGenerationResult();
            if (last == null || last.placementRecords.isEmpty()) {
                state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_no_blocks"));
                return;
            }
            resultSnapshot = last;
        }

        com.plot.api.world.PlacementReadiness readiness = host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            state.setProjectStatus(readiness.message());
            return;
        }
        if (host.placement().isBusy()) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_in_progress_wait"));
            return;
        }

        List<BlockRecord> records = new ArrayList<>(resultSnapshot.placementRecords.values());
        PowerLineGenerateCommand command = new PowerLineGenerateCommand(records, host.projection(), host.placement());
        state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_in_progress", records.size()));
        command.executeScheduled(() -> {
            host.commands().pushExecuted(command);
            PowerLineGenerateCommand.ExecutionResult result = command.getLastExecutionResult();
            if (result != null && result.isFullSuccess()) {
                state.setProjectStatus(PlotI18n.tr("plugin.powerline.build_success", result.success()));
            } else if (result != null) {
                state.setProjectStatus(PlotI18n.tr(
                    "plugin.powerline.build_partial",
                    result.success(),
                    result.total()));
            }
            clearPreview();
        });
    }

    public void locateLine(PowerLineFootprint line) {
        if (line == null || line.getPathPoints().isEmpty()) {
            return;
        }
        Vec2d centroid = computeCentroid(line.getPathPoints());
        Canvas canvas = com.plot.ui.canvas.CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(centroid);
            state.getSelection().select(line.getId(), false);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.locate_success", line.getName()));
        }
    }

    private static Vec2d computeCentroid(List<Vec2d> points) {
        double x = 0.0;
        double y = 0.0;
        for (Vec2d point : points) {
            x += point.x;
            y += point.y;
        }
        return new Vec2d(x / points.size(), y / points.size());
    }

    public boolean hasMinSpacingWarning(PowerLineFootprint line) {
        if (line == null) {
            return false;
        }
        List<Vec2d> mandatory = collectMandatoryPoles(line);
        for (int i = 1; i < mandatory.size(); i++) {
            if (mandatory.get(i - 1).distance(mandatory.get(i)) < line.getMinPoleSpacing()) {
                return true;
            }
        }
        return false;
    }

    private List<Vec2d> collectMandatoryPoles(PowerLineFootprint line) {
        List<Vec2d> path = line.getPathPoints();
        List<Vec2d> mandatory = new ArrayList<>();
        mandatory.add(path.getFirst());
        for (int i = 1; i < path.size() - 1; i++) {
            if (PowerPoleLayoutUtils.isCorner(path, i, line.getCornerAngleThreshold())) {
                mandatory.add(path.get(i));
            }
        }
        mandatory.add(path.getLast());
        return mandatory;
    }

    public void deleteLines(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        state.getProjectHistory().push(state.getProject());
        for (String id : ids) {
            state.getProject().removeLine(id);
            state.getSelection().retainExisting(state.getProject());
        }
        clearPreview();
        state.setProjectStatus(PlotI18n.tr("plugin.powerline.deleted", ids.size()));
    }

    public PoleDesignResolver designResolver() {
        return new PoleDesignResolver(state.getDesignProject());
    }

    public void onProjectLoaded(String filePath, Path projectsDir, Path designProjectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = projectsDir.resolve(targetFile);
        boolean loaded = loadProjectFile(file);
        loadDesignProjectFile(designProjectsDir.resolve(targetFile));
        if (loaded) {
            state.setCurrentProjectFile(targetFile);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.loaded", filePath));
        }
    }

    public void onProjectSaved(String filePath, Path projectsDir, Path designProjectsDir) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        state.setCurrentProjectFile(ProjectPathResolver.sidecarFileName(filePath));
        boolean saved = saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
        saveDesignProjectFile(designProjectsDir.resolve(state.getCurrentProjectFile()));
        if (saved) {
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.saved", filePath));
        }
    }

    public void persistProject(Path projectsDir, Path designProjectsDir) {
        saveProjectFile(projectsDir.resolve(state.getCurrentProjectFile()));
        saveDesignProjectFile(designProjectsDir.resolve(state.getCurrentProjectFile()));
    }

    public void onProjectLoaded(String filePath, Path projectsDir) {
        onProjectLoaded(filePath, projectsDir, projectsDir.getParent().resolve("pole-designs"));
    }

    public void onProjectSaved(String filePath, Path projectsDir) {
        onProjectSaved(filePath, projectsDir, projectsDir.getParent().resolve("pole-designs"));
    }

    public void persistProject(Path projectsDir) {
        persistProject(projectsDir, projectsDir.getParent().resolve("pole-designs"));
    }

    public boolean loadProjectFile(Path file) {
        try {
            PowerLineProject loaded = PowerLineProject.loadFrom(file);
            state.setProject(loaded);
            state.getProjectHistory().clear();
            state.getSelection().clear();
            if (!state.getProject().getLines().isEmpty()) {
                state.getSelection().select(state.getProject().getLines().keySet().iterator().next(), false);
            }
            resetAfterProjectLoad();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载电力线路项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.load_failed", file.getFileName()));
            return false;
        }
    }

    public void loadProjectForCurrentProject(Path projectsDir, Path designProjectsDir, String defaultProjectFile) {
        Project current = host.appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath(), projectsDir, designProjectsDir);
            return;
        }
        if (loadProjectFile(projectsDir.resolve(defaultProjectFile))) {
            state.setCurrentProjectFile(defaultProjectFile);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.default_loaded"));
        }
        loadDesignProjectFile(designProjectsDir.resolve(defaultProjectFile));
    }

    public void loadProjectForCurrentProject(Path projectsDir, String defaultProjectFile) {
        loadProjectForCurrentProject(
            projectsDir,
            projectsDir.getParent().resolve("pole-designs"),
            defaultProjectFile);
    }

    private void resetAfterProjectLoad() {
        state.setLineNameEditingId("");
        state.getSelectedPaths().clear();
        state.setPoleDesignerOpen(false);
        state.setPoleDesignerEditingId("");
        clearPreview();
    }

    private boolean loadDesignProjectFile(Path file) {
        try {
            state.setDesignProject(PowerLineDesignProject.loadFrom(file));
            state.getDesignContentFingerprint().reset();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载杆塔设计工程失败: {}", e.getMessage(), e);
            state.setDesignProject(new PowerLineDesignProject());
            return false;
        }
    }

    private boolean saveDesignProjectFile(Path file) {
        if (file == null || state.getDesignProject() == null) {
            return false;
        }
        try {
            String json = state.getDesignProject().toJson();
            if (state.getDesignContentFingerprint().isUnchanged(json, file)) {
                return true;
            }
            state.getDesignProject().saveTo(file);
            state.getDesignContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存杆塔设计工程失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public void savePoleDesign(PoleDesign design) {
        if (design == null) {
            return;
        }
        state.getDesignProject().addDesign(design.copy());
        state.setProjectStatus(PlotI18n.tr("plugin.powerline.design.saved", design.getName()));
    }

    private boolean saveProjectFile(Path file) {
        if (file == null || state.getProject() == null) {
            return false;
        }
        try {
            String json = state.getProject().toJson();
            if (state.getContentFingerprint().isUnchanged(json, file)) {
                return true;
            }
            state.getProject().saveTo(file);
            state.getContentFingerprint().markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存电力线路项目失败: {}", e.getMessage(), e);
            state.setProjectStatus(PlotI18n.tr("plugin.powerline.project.save_failed", file.getFileName()));
            return false;
        }
    }

    public void activatePathPickTool() {
        ToolManager toolManager = host.tools();
        var selectTool = toolManager.getTool("select");
        if (!(selectTool instanceof BaseTool baseTool)) {
            return;
        }
        state.getSelectedPaths().clear();
        toolManager.setActiveTool(selectTool);
        host.appState().setCurrentTool(baseTool);
        state.setProjectStatus(PlotI18n.tr("plugin.powerline.pick_started"));
    }

    private World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
