package com.plot.plugin.road.manager;

/**
 * 道路插件操作状态消息（供 UI 与各 Manager 共享）。
 */
public final class RoadProjectStatus {
    private String message = "";

    public String get() {
        return message;
    }

    public void set(String message) {
        this.message = message != null ? message : "";
    }

    public boolean isEmpty() {
        return message.isEmpty();
    }
}
