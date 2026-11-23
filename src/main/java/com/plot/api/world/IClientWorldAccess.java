package com.plot.api.world;

import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 客户端世界会话窄接口：插件经 {@link com.plot.core.context.PluginContext} 获取，
 * 不得直接调用 {@code MinecraftClient.getInstance()}。
 */
public interface IClientWorldAccess {

    /** 当前客户端世界；未进入世界时为 null。 */
    @Nullable
    World currentWorld();

    /**
     * 世界会话键：维度 + 存档名（集成服）或远端地址；无世界时为空串。
     */
    String worldSessionKey();

    /**
     * 在集成服线程上运行任务；非单机或无服务端时返回 false 且不调用 {@code task}。
     */
    boolean runOnIntegratedServer(Consumer<IIntegratedServerScheduler> task);

    static IClientWorldAccess unavailable() {
        return Unavailable.INSTANCE;
    }

    final class Unavailable implements IClientWorldAccess {
        private static final Unavailable INSTANCE = new Unavailable();

        private Unavailable() {
        }

        @Override
        public World currentWorld() {
            return null;
        }

        @Override
        public String worldSessionKey() {
            return "";
        }

        @Override
        public boolean runOnIntegratedServer(Consumer<IIntegratedServerScheduler> task) {
            return false;
        }
    }
}
