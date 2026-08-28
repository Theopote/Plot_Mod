package com.plot.plugin.road;

import java.util.List;

/**
 * 生成 Tab 工程检查结果（供 UI 摘要列表）。
 */
public record RoadNetworkValidationReport(List<Item> items) {

    public RoadNetworkValidationReport {
        items = List.copyOf(items);
    }

    public boolean hasWarnings() {
        for (Item item : items) {
            if (item.level() == Level.WARNING || item.level() == Level.ERROR) {
                return true;
            }
        }
        return false;
    }

    public boolean hasIntersectionWork() {
        for (Item item : items) {
            if (item.level() == Level.OK) {
                continue;
            }
            if ("plugin.road.validation.intersections_pending".equals(item.messageKey())
                    || "plugin.road.validation.intersections_incomplete".equals(item.messageKey())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasErrors() {
        for (Item item : items) {
            if (item.level() == Level.ERROR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hard blockers that must be resolved before world placement.
     */
    public boolean blocksBuild() {
        for (Item item : items) {
            if (item.level() == Level.ERROR) {
                return true;
            }
            if ("plugin.road.validation.intersections_pending".equals(item.messageKey())
                    || "plugin.road.validation.topology_issues".equals(item.messageKey())) {
                return true;
            }
        }
        return false;
    }

    public List<Item> nonOkItems() {
        return items.stream().filter(item -> item.level() != Level.OK).toList();
    }

    public enum Level {
        OK,
        WARNING,
        ERROR
    }

    public record Item(Level level, String messageKey, Object[] args) {

        public static Item ok(String messageKey, Object... args) {
            return new Item(Level.OK, messageKey, args);
        }

        public static Item warning(String messageKey, Object... args) {
            return new Item(Level.WARNING, messageKey, args);
        }

        public static Item error(String messageKey, Object... args) {
            return new Item(Level.ERROR, messageKey, args);
        }
    }
}
