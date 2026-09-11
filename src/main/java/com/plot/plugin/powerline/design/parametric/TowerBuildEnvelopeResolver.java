package com.plot.plugin.powerline.design.parametric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.Optional;

/** 从当前客户端玩家位置解析世界建造包络（设计器预览用）。 */
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
}
