package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.PluginContext;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.PoleDesignAssignmentResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.placement.SingleTowerGhostPreview;
import com.plot.plugin.powerline.placement.SingleTowerOrientation;
import com.plot.plugin.powerline.placement.SingleTowerPlacementSession;
import com.plot.plugin.powerline.placement.SingleTowerPlacementState;
import com.plot.utils.PlotI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.Objects;

/** 单塔放置：激活、悬停预览、确认（骨架阶段仅状态与 Ghost，不写入世界）。 */
public final class SingleTowerPlacementActions {
    private final PluginContext host;
    private final PowerLinePluginState state;
    private final Object projectLock;
    private final SingleTowerPlacementSession session = new SingleTowerPlacementSession();

    public SingleTowerPlacementActions(
            PluginContext host,
            PowerLinePluginState state,
            Object projectLock) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
        this.projectLock = Objects.requireNonNull(projectLock, "projectLock");
    }

    public SingleTowerPlacementSession session() {
        return session;
    }

    public boolean isActive() {
        return session.isActive();
    }

    public SingleTowerPlacementState snapshot() {
        return session.snapshot();
    }

    public void beginPlacement(PowerLineFootprint styleSource) {
        if (styleSource == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.select_line_hint"),
                ProjectStatusSeverity.WARNING);
            return;
        }
        PoleDesign design = resolveDesign(styleSource);
        if (design == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.single_tower.no_design"),
                ProjectStatusSeverity.WARNING);
            return;
        }
        World world = getClientWorld();
        if (world == null) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.generate_world_unavailable"),
                ProjectStatusSeverity.ERROR);
            return;
        }

        synchronized (projectLock) {
            host.ghosts().clearAllGhostBlocks();
            state.setLastGenerationResult(null);
            state.setPreviewKey(null);
            session.begin(design, design.getName(), styleSource);
        }
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.single_tower.placing_hint"),
            ProjectStatusSeverity.INFO);
    }

    public void cancelPlacement() {
        if (!session.isActive()) {
            return;
        }
        session.cancel();
        host.ghosts().clearAllGhostBlocks();
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.single_tower.cancelled"),
            ProjectStatusSeverity.INFO);
    }

    public void tick() {
        if (!session.isActive()) {
            return;
        }
        SingleTowerPlacementSession.Outcome outcome = session.tick(
            host.appState(),
            host.coordinates(),
            this::refreshGhostPreview);
        switch (outcome.getResult()) {
            case NONE -> { }
            case CANCELLED -> {
                host.ghosts().clearAllGhostBlocks();
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.single_tower.cancelled"),
                    ProjectStatusSeverity.INFO);
            }
            case WORLD_UNAVAILABLE -> state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.generate_world_unavailable"),
                ProjectStatusSeverity.ERROR);
            case PLACED -> handlePlaced(outcome.getPlanPoint(), outcome.getRotationQuadrant());
            default -> { }
        }
    }

    private void handlePlaced(Vec2d planPoint, int rotationQuadrant) {
        if (planPoint == null) {
            return;
        }
        // 骨架阶段：记录落点，后续 Phase 3 接入世界建造与 Undo。
        state.setProjectStatus(
            PlotI18n.tr(
                "plugin.powerline.single_tower.placed_skeleton",
                String.format("%.1f, %.1f", planPoint.x, planPoint.y),
                PlotI18n.tr(SingleTowerOrientation.labelKey(rotationQuadrant))),
            ProjectStatusSeverity.SUCCESS);
        host.ghosts().clearAllGhostBlocks();
    }

    public void refreshGhostPreview() {
        if (!session.isActive()) {
            return;
        }
        SingleTowerPlacementState placement = session.snapshot();
        PowerLineFootprint styleSource = session.styleSource();
        if (placement == null || styleSource == null || !placement.hoverValid() || placement.planPoint() == null) {
            host.ghosts().clearAllGhostBlocks();
            return;
        }
        World world = getClientWorld();
        if (world == null) {
            return;
        }
        TerrainSampler terrain = MinecraftTerrainSampler.of(world, host.coordinates());
        PowerLineGenerationResult preview = SingleTowerGhostPreview.generate(
            host,
            placement.design(),
            styleSource,
            placement.planPoint(),
            placement.rotationQuadrant(),
            terrain);
        if (preview == null) {
            host.ghosts().clearAllGhostBlocks();
            return;
        }
        SingleTowerGhostPreview.projectToGhosts(host, preview);
    }

    private PoleDesign resolveDesign(PowerLineFootprint line) {
        PoleDesignResolver resolver = new PoleDesignResolver(state.getDesignProject());
        PowerPoleSite site = new PowerPoleSite(new Vec2d(0, 0));
        site.setRole(TowerRole.SUSPENSION);
        PoleDesignAssignmentResolver assignment = new PoleDesignAssignmentResolver(
            resolver,
            new TowerFamilyResolver());
        PoleDesign design = assignment.resolve(site, line).design();
        if (design != null) {
            return design;
        }
        if (line.hasPoleDesign()) {
            return resolver.find(line.getPoleDesignId());
        }
        return null;
    }

    private World getClientWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }
}
