package com.plot.ui.panel.layer;

final class LayerNameRules {
    static final int MAX_LENGTH = 50;

    private LayerNameRules() {
    }

    static String normalize(String rawName) {
        return rawName == null ? "" : rawName.trim();
    }

    static boolean isWithinLength(String name) {
        return name != null && name.length() <= MAX_LENGTH;
    }

    static boolean hasNoControlCharacters(String name) {
        return name != null && name.codePoints().noneMatch(Character::isISOControl);
    }
}