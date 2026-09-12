package com.plot.plugin.powerline.ui;

import net.minecraft.client.MinecraftClient;

/** 将异步放置回调中的 UI 状态更新调度回 Minecraft 客户端线程。 */
public final class PowerLineUiExecutor {
    private PowerLineUiExecutor() {
    }

    public static void runOnClientThread(Runnable action) {
        if (action == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            action.run();
            return;
        }
        if (client.isOnThread()) {
            action.run();
        } else {
            client.execute(action);
        }
    }
}
