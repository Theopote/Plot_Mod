package com.plot.plugin.powerline.design.parametric;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.placement.PolePlacementBase;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 从世界/地形解析单杆位与多杆位建造包络。 */
public final class TowerBuildEnvelopeResolver {
    private TowerBuildEnvelopeResolver() {
    }

    public static Optional<TowerBuildEnvelope> tryFromClientPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return Optional.empty();
        }
        World world = client.world;
        int topExclusive = world.getBottomY() + world.getDimension().height();
        double groundY = client.player.getY();
        return Optional.of(new TowerBuildEnvelope(
            world.getBottomY(),
            topExclusive,
            groundY,
            TowerBuildEnvelope.DEFAULT_TOP_SAFETY_MARGIN));
    }

    public static TowerBuildEnvelope forSite(Vec2d planPoint, TerrainSampler terrain) {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain is required");
        }
        PolePlacementBase placement = PolePlacementBase.resolve(planPoint, terrain);
        return TowerBuildEnvelope.forSite(
            terrain.worldBottomY(),
            terrain.worldTopExclusiveY(),
            placement.buildBaseY(),
            TowerBuildEnvelope.DEFAULT_TOP_SAFETY_MARGIN);
    }

    public static TowerLineBuildEnvelope fromPoleSites(List<PowerPoleSite> sites, TerrainSampler terrain) {
        if (sites == null || sites.isEmpty()) {
            throw new IllegalArgumentException("sites must not be empty");
        }
        List<TowerBuildEnvelope> envelopes = new ArrayList<>(sites.size());
        for (PowerPoleSite site : sites) {
            envelopes.add(forSite(site.getPlanPosition(), terrain));
        }
        return TowerLineBuildEnvelope.fromSiteEnvelopes(envelopes);
    }

    public static Optional<TowerLineBuildEnvelope> tryFromFootprint(
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            com.plot.api.world.ICoordinateService coordinates) {
        if (footprint == null || terrain == null || coordinates == null) {
            return Optional.empty();
        }
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(footprint, coordinates);
        if (sites.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromPoleSites(sites, terrain));
    }
}
