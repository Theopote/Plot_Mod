package com.plot.api.world;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 集成服线程调度句柄：插件预取等路径经此访问服务端世界，不持有 {@code MinecraftClient}。
 */
public interface IIntegratedServerScheduler {

    @Nullable
    ServerWorld getWorld(RegistryKey<World> worldKey);

    void execute(Runnable task);
}
