package com.plot.plugin.pattern.pipeline;

import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSpace;

/**
 * 在 Pattern Space 中将采样点解析为待放置方块 id。
 *
 * @return 方块 id；{@code null} 表示跳过该采样点（如透明像素）
 */
public interface PatternMaterialResolver {
    String resolveMaterial(PatternSpace space, PatternSample sample);
}
