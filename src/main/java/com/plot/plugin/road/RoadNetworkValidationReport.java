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
