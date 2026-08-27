package com.plot.plugin.road.manager;

/**
 * 道路插件状态栏消息（含严重级别，供工具栏着色与图标映射）。
 */
public record RoadStatus(Severity severity, String message) {

    public enum Severity {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        PROGRESS
    }

    public static RoadStatus empty() {
        return new RoadStatus(Severity.INFO, "");
    }

    public static RoadStatus of(Severity severity, String message) {
        return new RoadStatus(severity, message != null ? message : "");
    }

    public boolean isEmpty() {
        return message == null || message.isBlank();
    }
}
