package com.plot.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.level.ServerWorldProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 管理 Plot 界面打开期间对世界天气的临时调整。
 * 打开界面时记录当前天气并强制晴天（关闭天气循环），关闭界面时恢复打开前的天气与循环状态。
 */
public final class WorldWeatherManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/WorldWeatherManager");
    /** 强制晴天时写入的晴天持续刻数；因同时关闭 doWeatherCycle，实际不会到期切换。 */
    private static final int FORCED_CLEAR_DURATION_TICKS = 6000;

    private static class Holder {
        private static final WorldWeatherManager INSTANCE = new WorldWeatherManager();
    }

    private boolean savedWeatherValid = false;
    private boolean savedRaining = false;
    private boolean savedThundering = false;
    private int savedClearWeatherTime = 0;
    private int savedRainTime = 0;
    private int savedThunderTime = 0;
    private boolean savedWeatherCycle = true;

    private WorldWeatherManager() {
    }

    public static WorldWeatherManager getInstance() {
        return Holder.INSTANCE;
    }

    /** 记录当前天气与天气循环开关，并强制晴天，供关闭界面时恢复。 */
    public void saveAndForceClear() {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        ServerWorldProperties props = getServerWorldProperties(server);
        if (props != null) {
            savedRaining = props.isRaining();
            savedThundering = props.isThundering();
            savedClearWeatherTime = props.getClearWeatherTime();
            savedRainTime = props.getRainTime();
            savedThunderTime = props.getThunderTime();
            savedWeatherCycle = readWeatherCycle();
            savedWeatherValid = true;
            LOGGER.debug(
                    "已保存天气: raining={}, thundering={}, clear={}, rain={}, thunder={}, cycle={}",
                    savedRaining,
                    savedThundering,
                    savedClearWeatherTime,
                    savedRainTime,
                    savedThunderTime,
                    savedWeatherCycle);
        } else {
            World world = MinecraftClient.getInstance().world;
            if (world == null) {
                savedWeatherValid = false;
                return;
            }
            savedRaining = world.isRaining();
            savedThundering = world.isThundering();
            savedClearWeatherTime = FORCED_CLEAR_DURATION_TICKS;
            savedRainTime = 0;
            savedThunderTime = 0;
            savedWeatherCycle = true;
            savedWeatherValid = true;
            LOGGER.debug(
                    "已保存客户端天气快照: raining={}, thundering={}",
                    savedRaining,
                    savedThundering);
        }

        if (savedWeatherCycle) {
            setWeatherCycle(false);
        }
        forceClearWeather();
    }

    /** 恢复打开界面之前保存的天气与天气循环状态。 */
    public void restoreSavedWeather() {
        if (!savedWeatherValid) {
            return;
        }

        applyWeather(
                savedRaining,
                savedThundering,
                savedClearWeatherTime,
                savedRainTime,
                savedThunderTime);

        if (savedWeatherCycle) {
            setWeatherCycle(true);
        }

        savedWeatherValid = false;
    }

    private void forceClearWeather() {
        applyWeather(false, false, FORCED_CLEAR_DURATION_TICKS, 0, 0);
    }

    private void applyWeather(
            boolean raining,
            boolean thundering,
            int clearWeatherTime,
            int rainTime,
            int thunderTime) {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                int clear = Math.max(0, clearWeatherTime);
                int rain = Math.max(0, rainTime);
                int thunder = Math.max(0, thunderTime);
                server.execute(() -> {
                    if (raining || thundering) {
                        int duration = Math.max(1, Math.max(rain, thunder));
                        overworld.setWeather(0, duration, raining, thundering);
                        ServerWorldProperties props = getServerWorldProperties(server);
                        if (props != null) {
                            props.setRainTime(rain);
                            props.setThunderTime(thunder);
                            props.setClearWeatherTime(0);
                        }
                    } else {
                        overworld.setWeather(Math.max(1, clear), 0, false, false);
                    }
                });
                return;
            }
        }

        if (raining && thundering) {
            sendCommand("weather thunder");
        } else if (raining) {
            sendCommand("weather rain");
        } else {
            sendCommand("weather clear");
        }
    }

    private static ServerWorldProperties getServerWorldProperties(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return null;
        }
        WorldProperties properties = overworld.getLevelProperties();
        if (properties instanceof ServerWorldProperties serverProps) {
            return serverProps;
        }
        return null;
    }

    /** 单人本地世界可直接读取本地服务器上的天气循环规则，联机则默认视为开启。 */
    private boolean readWeatherCycle() {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                return overworld.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE);
            }
        }
        return true;
    }

    /** 单人本地世界直接在服务器线程上修改规则；联机场景回退为聊天指令（尽力而为）。 */
    private void setWeatherCycle(boolean enabled) {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                server.execute(() -> overworld.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(enabled, server));
                return;
            }
        }
        sendCommand(String.format("gamerule doWeatherCycle %b", enabled));
    }

    private void sendCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) {
            return;
        }
        try {
            Objects.requireNonNull(client.getNetworkHandler()).sendChatCommand(command);
        } catch (Exception e) {
            LOGGER.warn("发送命令 '{}' 失败: {}", command, e.getMessage());
        }
    }
}
