package com.plot.plugin.powerline.style;

/** Style 画廊分类（装饰向，非工程规范）。 */
public enum StyleCategory {
    UTILITY,
    TRANSMISSION,
    INDUSTRIAL,
    FANTASY;

    public String sectionKey() {
        return "plugin.powerline.style.category." + name().toLowerCase();
    }
}
