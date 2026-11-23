package com.plot.client;

import com.plot.api.world.IClientWorldAccess;
import com.plot.api.world.IIntegratedServerScheduler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 基于 {@link MinecraftClient} 的 {@link IClientWorldAccess} 实现；仅客户端组合根持有。
 */
public final class MinecraftClientWorldAccess implements IClientWorldAccess {
    public static final MinecraftClientWorldAccess INSTANCE = new MinecraftClientWorldAccess();

    private MinecraftClientWorldAccess() {
    }

    @Override
    public @Nullable World currentWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.world : null;
    }

    @Override
    public String worldSessionKey() {
        World world = currentWorld();
        if (world == null || world.getRegistryKey() == null) {
            return "";
        }
        String dimension = world.getRegistryKey().getValue().toString();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return dimension;
        }
        MinecraftServer integrated = client.getServer();
        if (integrated != null && integrated.getSaveProperties() != null) {
            String levelName = integrated.getSaveProperties().getLevelName();
            if (levelName == null || levelName.isBlank()) {
                levelName = "unnamed";
            }
            return dimension + "|save:" + levelName;
        }
        ServerInfo remote = client.getCurrentServerEntry();
        if (remote != null && remote.address != null && !remote.address.isBlank()) {
            return dimension + "|remote:" + remote.address;
        }
        return dimension;
    }

    @Override
    public boolean runOnIntegratedServer(Consumer<IIntegratedServerScheduler> task) {
        if (task == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return false;
        }
        MinecraftServer server = client.getServer();
        if (server == null) {
            return false;
        }
        IIntegratedServerScheduler scheduler = new MinecraftServerScheduler(server);
        server.execute(() -> task.accept(scheduler));
        return true;
    }

    private static final class MinecraftServerScheduler implements IIntegratedServerScheduler {
        private final MinecraftServer server;

        private MinecraftServerScheduler(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public @Nullable ServerWorld getWorld(RegistryKey<World> worldKey) {
            return server.getWorld(worldKey);
        }

        @Override
        public void execute(Runnable task) {
            if (task != null) {
                server.execute(task);
            }
        }
    }
}
