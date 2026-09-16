package com.plot.plugin.pattern.pipeline;

final class PatternGridMath {
    private PatternGridMath() {
    }

    static int floorDiv(double value, double divisor) {
        return (int) Math.floor(value / divisor);
    }

    static int positiveMod(int value, int modulus) {
        if (modulus <= 0) {
            return 0;
        }
        int mod = value % modulus;
        return mod < 0 ? mod + modulus : mod;
    }
}
