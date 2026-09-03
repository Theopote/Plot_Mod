package com.plot.core.material;

/**
 * 土方材料兼容性矩阵：源 spoil 类别 → 目标填方类别。
 */
public final class EarthMaterialCompatibilityMatrix {

    private EarthMaterialCompatibilityMatrix() {
    }

    public static MaterialCompatibility compatibility(
            EarthMaterialClass spoilClass,
            EarthMaterialClass fillClass) {
        EarthMaterialClass spoil = spoilClass != null ? spoilClass : EarthMaterialClass.UNKNOWN;
        EarthMaterialClass fill = fillClass != null ? fillClass : EarthMaterialClass.UNKNOWN;

        if (spoil.isExportOnlySpoil()) {
            return MaterialCompatibility.FORBIDDEN;
        }
        if (spoil == EarthMaterialClass.UNKNOWN || fill == EarthMaterialClass.UNKNOWN) {
            return MaterialCompatibility.ALLOWED;
        }

        return switch (spoil) {
            case TOPSOIL -> switch (fill) {
                case TOPSOIL -> MaterialCompatibility.ALLOWED;
                case COMMON_FILL -> MaterialCompatibility.CONDITIONAL;
                default -> MaterialCompatibility.FORBIDDEN;
            };
            case COMMON_FILL -> switch (fill) {
                case COMMON_FILL -> MaterialCompatibility.ALLOWED;
                case TOPSOIL, STRUCTURAL_FILL -> MaterialCompatibility.CONDITIONAL;
                default -> MaterialCompatibility.FORBIDDEN;
            };
            case STRUCTURAL_FILL -> switch (fill) {
                case STRUCTURAL_FILL -> MaterialCompatibility.ALLOWED;
                case COMMON_FILL -> MaterialCompatibility.CONDITIONAL;
                default -> MaterialCompatibility.FORBIDDEN;
            };
            case ROCK, UNSUITABLE, UNKNOWN -> MaterialCompatibility.FORBIDDEN;
        };
    }

    public static boolean canTransfer(EarthMaterialClass spoilClass, EarthMaterialClass fillClass) {
        return compatibility(spoilClass, fillClass).allowsTransfer();
    }
}
