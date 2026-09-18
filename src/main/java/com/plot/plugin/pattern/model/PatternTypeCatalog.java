package com.plot.plugin.pattern.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 程序化图案类型的 UI 分组目录。新增 {@link ProceduralPatternConfig.PatternType} 时必须归入某一组，
 * 避免 flat combo 无限变长。
 */
public final class PatternTypeCatalog {

    public record Category(String labelKey, List<ProceduralPatternConfig.PatternType> types) {
    }

    private static final List<Category> CATEGORIES = List.of(
        new Category("plugin.pattern.type_category.basic", List.of(
            ProceduralPatternConfig.PatternType.CHECKERBOARD,
            ProceduralPatternConfig.PatternType.STRIPES,
            ProceduralPatternConfig.PatternType.DIAMOND,
            ProceduralPatternConfig.PatternType.HEXAGONAL)),
        new Category("plugin.pattern.type_category.masonry", List.of(
            ProceduralPatternConfig.PatternType.RUNNING_BOND,
            ProceduralPatternConfig.PatternType.HERRINGBONE,
            ProceduralPatternConfig.PatternType.CROSSHATCH,
            ProceduralPatternConfig.PatternType.WINDMILL,
            ProceduralPatternConfig.PatternType.FISH_SCALE)),
        new Category("plugin.pattern.type_category.composition", List.of(
            ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS,
            ProceduralPatternConfig.PatternType.RADIAL,
            ProceduralPatternConfig.PatternType.FRAME)),
        new Category("plugin.pattern.type_category.random", List.of(
            ProceduralPatternConfig.PatternType.MOSAIC,
            ProceduralPatternConfig.PatternType.SCATTER)));

    private PatternTypeCatalog() {
    }

    public static List<Category> categories() {
        return CATEGORIES;
    }

    /** UI 展示顺序下的全部类型（与 {@link ProceduralPatternConfig.PatternType#values()} 顺序无关）。 */
    public static List<ProceduralPatternConfig.PatternType> allTypesInDisplayOrder() {
        List<ProceduralPatternConfig.PatternType> types = new ArrayList<>();
        for (Category category : CATEGORIES) {
            types.addAll(category.types());
        }
        return List.copyOf(types);
    }
}
