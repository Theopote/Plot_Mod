package com.plot.plugin.earthwork.validation;

import com.plot.utils.PlotI18n;

import java.util.List;

/**
 * 土方预览/生成前工程检查结果。
 */
public record EarthworkValidationReport(List<Item> items) {

    public EarthworkValidationReport {
        items = List.copyOf(items);
    }

    public static EarthworkValidationReport empty() {
        return new EarthworkValidationReport(List.of());
    }

    public boolean hasErrors() {
        return items.stream().anyMatch(item -> item.level() == Level.ERROR);
    }

    public boolean blocksPreview() {
        return hasErrors();
    }

    public List<Item> errors() {
        return items.stream().filter(item -> item.level() == Level.ERROR).toList();
    }

    public List<Item> warnings() {
        return items.stream().filter(item -> item.level() == Level.WARNING).toList();
    }

    public List<String> warningKeys() {
        return warnings().stream().map(Item::messageKey).toList();
    }

    public String firstBlockingMessage() {
        for (Item item : items) {
            if (item.level() == Level.ERROR) {
                return item.formatMessage();
            }
        }
        return "";
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

        public String formatMessage() {
            return args != null && args.length > 0
                ? PlotI18n.tr(messageKey, args)
                : PlotI18n.tr(messageKey);
        }
    }
}
