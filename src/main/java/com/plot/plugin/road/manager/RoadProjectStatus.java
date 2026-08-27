package com.plot.plugin.road.manager;

/**
 * 道路插件操作状态消息（供 UI 与各 Manager 共享）。
 */
public final class RoadProjectStatus {
    private RoadStatus current = RoadStatus.empty();

    public RoadStatus getStatus() {
        return current;
    }

    public String get() {
        return current.message();
    }

    public void clear() {
        current = RoadStatus.empty();
    }

    public void set(String message) {
        set(RoadStatus.Severity.INFO, message);
    }

    public void set(RoadStatus.Severity severity, String message) {
        current = RoadStatus.of(severity, message);
    }

    public void set(RoadStatus status) {
        current = status != null ? status : RoadStatus.empty();
    }

    public void info(String message) {
        set(RoadStatus.Severity.INFO, message);
    }

    public void success(String message) {
        set(RoadStatus.Severity.SUCCESS, message);
    }

    public void warning(String message) {
        set(RoadStatus.Severity.WARNING, message);
    }

    public void error(String message) {
        set(RoadStatus.Severity.ERROR, message);
    }

    public void progress(String message) {
        set(RoadStatus.Severity.PROGRESS, message);
    }

    public boolean isEmpty() {
        return current.isEmpty();
    }
}
