package com.plot.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 管理 Plot 界面打开期间对世界时间的临时调整。
 * 打开界面时记录当前时间，关闭界面时恢复到打开前的时间。
 */
public final class WorldTimeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/WorldTimeManager");
    private static final long TICKS_PER_DAY = 24000L;

    private static class Holder {
        private static final WorldTimeManager INSTANCE = new WorldTimeManager();
    }

    private boolean savedTimeValid = false;
    private long savedTimeOfDay = 0L;

    private WorldTimeManager() {
    }

    public static WorldTimeManager getInstance() {
        return Holder.INSTANCE;
    }

    /** 记录当前世界时间，供关闭界面时恢复。 */
    public void saveCurrentTime() {
        World world = MinecraftClient.getInstance().world;
        if (world != null) {
            savedTimeOfDay = world.getTimeOfDay();
            savedTimeValid = true;
            LOGGER.debug("已保存世界时间: {}", savedTimeOfDay);
        } else {
            savedTimeValid = false;
        }
    }

    /** 恢复打开界面之前保存的世界时间。 */
    public void restoreSavedTime() {
        if (!savedTimeValid) {
            return;
        }
        sendSetTimeCommand(savedTimeOfDay);
        savedTimeValid = false;
    }

    /**
     * 获取当前一天内的时间刻度（0~23999），用于同步滑动条显示。
     */
    public float getCurrentDayTime() {
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return 0.0f;
        }
        return (float) Math.floorMod(world.getTimeOfDay(), TICKS_PER_DAY);
    }

    /**
     * 设置一天内的时间刻度（0~23999），保持当前的天数不变。
     */
    public void setDayTime(float dayTimeTicks) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        long clamped = Math.max(0L, Math.min(TICKS_PER_DAY - 1, Math.round(dayTimeTicks)));
        long currentAbsolute = world.getTimeOfDay();
        long day = Math.floorDiv(currentAbsolute, TICKS_PER_DAY);
        long newAbsolute = day * TICKS_PER_DAY + clamped;
        sendSetTimeCommand(newAbsolute);
    }

    private void sendSetTimeCommand(long absoluteTime) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) {
            return;
        }
        try {
            String command = String.format("time set %d", absoluteTime);
            Objects.requireNonNull(client.getNetworkHandler()).sendChatCommand(command);
        } catch (Exception e) {
            LOGGER.warn("发送 time set 命令失败: {}", e.getMessage());
        }
    }
}
