package com.plot.plugin.road.model;

import java.util.List;

/**
 * 道路网络不变量校验结果。
 */
public record RoadNetworkValidationResult(boolean valid, List<String> violations) {
    public boolean isValid() {
        return valid;
    }
}
