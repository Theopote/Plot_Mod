# Plot 项目 UI 优化建议

> **文档版本**: v1.0  
> **生成日期**: 2024  
> **项目**: Plot - Minecraft CAD 设计工具  
> **UI 框架**: ImGui (Dear ImGui Java Binding)

---

## 目录

1. [项目 UI 现状分析](#1-项目-ui-现状分析)
2. [核心设计问题](#2-核心设计问题)
3. [UI 优化建议](#3-ui-优化建议)
4. [颜色系统优化](#4-颜色系统优化)
5. [主题系统优化](#5-主题系统优化)
6. [组件设计优化](#6-组件设计优化)
7. [布局与间距优化](#7-布局与间距优化)
8. [交互体验优化](#8-交互体验优化)
9. [实施路线图](#9-实施路线图)

---

## 1. 项目 UI 现状分析

### 1.1 技术架构

Plot 项目采用 **ImGui** (Immediate Mode GUI) 作为 UI 框架，这是一个轻量级的游戏/工具界面库，特点是：
- **即时模式渲染**：每帧重绘整个界面
- **无状态设计**：UI 状态由应用程序管理
- **高性能**：适合游戏内嵌工具界面

### 1.2 代码结构

项目 UI 代码分布情况：
- **核心 UI 模块**: 286 个 Java 文件（`src/main/java/com/plot/ui/`）
- **插件 UI 模块**: 40+ 个文件（`src/main/java/com/plot/plugin/*/ui/`）
- **主题系统**: `UITheme`, `ThemeManager`, `PlotStyleScope`
- **颜色系统**: `PluginUiColors`, `PluginSectionColors`
- **组件库**: `UIUtils`, `PluginSectionCard`, `DialogStyleManager`

### 1.3 现有优势

✅ **完善的主题系统**
- 支持深色/浅色主题切换
- 主题配置持久化
- 样式隔离机制（`PlotStyleScope`），避免与其他模组冲突

✅ **统一的颜色规范**
- `PluginUiColors` 提供语义化颜色常量
- 状态颜色清晰（成功、警告、错误）
- 支持主题派生颜色

✅ **模块化组件设计**
- `PluginSectionCard` - 可折叠分区卡片
- `DialogStyleManager` - 对话框统一样式
- `UIUtils` - 通用 UI 工具方法

✅ **完善的国际化支持**
- `PlotI18n` 翻译系统
- 中文为主要语言

---

## 2. 核心设计问题

### 2.1 颜色系统碎片化

**问题描述**：
颜色定义分散在多个文件中，存在重复和不一致：

```java
// UITheme.java
public static final int SUCCESS_COLOR = ImColor.rgba(0.2f, 0.7f, 0.2f, 0.9f);
public static final int WARNING_COLOR = ImColor.rgba(0.9f, 0.7f, 0.2f, 0.9f);

// ThemeColors 内部类又定义了一遍
public static final int SUCCESS_COLOR = ImColor.rgba(0.2f, 0.7f, 0.2f, 0.9f);
public static final int WARNING_COLOR = ImColor.rgba(0.9f, 0.7f, 0.2f, 0.9f);

// PluginUiColors.java 又有不同的定义
public static final int STATUS_OK = ImColor.rgba(128, 255, 128, 255);     // 偏亮绿
public static final int WARNING = ImColor.rgba(255, 160, 96, 255);         // 偏橙
```

**影响**：
- 颜色语义不统一（`STATUS_OK` vs `SUCCESS_COLOR`）
- 颜色值不一致（同为"成功"，一个用浮点数，一个用 RGB 整数）
- 维护困难，修改主题时需要多处同步

### 2.2 间距系统不统一

**问题描述**：
间距常量定义重复且数值不一致：

```java
// UILayout.java
public static final float BUTTON_PADDING = 4.0f;
public static final float ITEM_SPACING = 4.0f;

// DialogStyleManager.java
public static final float ITEM_SPACING = 4.0f;
public static final float FRAME_PADDING = 2.0f;
public static final float PANEL_PADDING = 8.0f;  // ITEM_SPACING * 2.0f

// UITheme.java
public static final float WINDOW_PADDING = 0.0f;
public static final float FRAME_PADDING = 0.0f;
public static final float ITEM_SPACING = 0.0f;
```

**影响**：
- 不同区域视觉不一致
- 难以建立统一的视觉节奏
- 调整设计时需要多处修改

### 2.3 圆角使用不一致

**问题描述**：
圆角设置混乱，缺乏设计规范：

```java
// 工具栏控件：4px 圆角
theme.toolbarControlRounding = 4.0f;

// 通用控件：0px（无圆角）
theme.panelControlRounding = 0.0f;

// 对话框：强制 0px
ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
```

**问题**：
- 工具栏按钮有圆角，但对话框按钮无圆角 → 视觉不统一
- 缺少中间状态（如 2px 微圆角）
- 圆角与无圆角混用导致界面风格混乱

### 2.4 对比度和可读性问题

**深色主题对比度不足**：

```java
// 按钮正常状态
theme.buttonNormal = ImColor.rgba(0.25f, 0.25f, 0.25f, 1.0f);    // 灰度 64/255
theme.buttonHovered = ImColor.rgba(0.30f, 0.30f, 0.30f, 1.0f);   // 灰度 77/255
theme.buttonActive = ImColor.rgba(0.35f, 0.35f, 0.35f, 1.0f);    // 灰度 89/255
// 差异仅 13 灰度单位，不够明显

// 文本颜色
theme.text = ImColor.rgba(0.9f, 0.9f, 0.9f, 1.0f);              // 灰度 230/255
theme.mutedText = ImColor.rgba(0.65f, 0.65f, 0.65f, 1.0f);      // 灰度 166/255
// 在深色背景上可读性尚可，但对比不够清晰
```

**浅色主题问题更严重**：

```java
// 按钮正常状态
theme.buttonNormal = ImColor.rgba(0.75f, 0.75f, 0.75f, 1.0f);    // 灰度 191/255
theme.buttonHovered = ImColor.rgba(0.82f, 0.82f, 0.82f, 1.0f);   // 灰度 209/255
// 在浅色背景（242/255）上对比度极低
```

### 2.5 组件设计不够精致

**PluginSectionCard 设计问题**：

```java
// 色条宽度过窄
private static final float ACCENT_WIDTH = 3f;  // 仅 3px，不够显眼

// 内边距不够
private static final float BODY_PAD_X = 8f;
private static final float BODY_PAD_Y = 6f;    // 6px 过于紧凑

// 箭头视觉重心调整
float chevronY = textY + (open ? 0f : -fontSize * 0.12f);
// 仅上移 12%，调整幅度过小
```

**对话框设计问题**：

```java
// 按钮紧凑但缺少呼吸感
public static final float FRAME_PADDING = 2.0f;  // 仅 2px，过于紧凑
public static final float ITEM_SPACING = 4.0f;   // 4px 间距不够

// 关闭按钮占用标题栏，不够优雅
public static boolean renderTopRightCloseButton(String idSuffix) {
    // 使用 DrawList 手绘关闭按钮，实现复杂
}
```

### 2.6 缺少微交互和动画

**问题**：
- 按钮点击无反馈动画
- 卡片展开/折叠无过渡效果
- 颜色切换无渐变
- 缺少悬停动效（除了颜色变化）

**影响**：
- 界面感觉生硬、死板
- 缺少现代 UI 的流畅感
- 用户操作反馈不明确

---

## 3. UI 优化建议

### 3.1 设计原则

基于 Plot 作为 **专业 CAD 工具** 的定位，UI 设计应遵循：

#### **专业性 (Professional)**
- **清晰的信息层级**：重要信息突出，次要信息弱化
- **精准的数据呈现**：工程数据、坐标、尺寸等清晰易读
- **一致的视觉语言**：按钮、输入框、卡片等元素风格统一

#### **清晰性 (Clarity)**
- **高对比度**：文本与背景对比度 ≥ 4.5:1 (WCAG AA 标准)
- **合理的留白**：避免元素拥挤，保持呼吸感
- **明确的状态反馈**：悬停、激活、选中状态清晰可辨

#### **艺术性 (Artistic)**
- **精致的细节**：合理使用圆角、阴影、渐变
- **和谐的色彩**：色彩搭配符合美学原则
- **流畅的动效**：微妙的过渡动画提升体验

### 3.2 设计参考

推荐参考以下专业工具的 UI 设计：

#### **CAD/工程软件**
- **AutoCAD 2024**: 经典深色主题，高对比度，清晰的工具面板
- **Rhino 7**: 简洁的浅色主题，优雅的图标设计
- **Blender 3.x**: 现代化深色 UI，优秀的颜色系统

#### **设计工具**
- **Figma**: 精致的组件设计，流畅的交互动效
- **Sketch**: 清晰的信息层级，优雅的配色方案

#### **代码编辑器**
- **VS Code**: 完善的主题系统，优秀的可读性
- **JetBrains IDEs**: 精致的深色主题，清晰的状态反馈

---

## 4. 颜色系统优化

### 4.1 建立统一的颜色语义体系

**新建 `ColorPalette.java` 作为唯一颜色定义源**：

```java
package com.plot.ui.theme;

import imgui.ImColor;

/**
 * Plot 统一颜色调色板
 * 所有颜色定义的唯一来源
 */
public final class ColorPalette {
    
    // ===== 语义颜色 =====
    
    /** 主强调色 - 用于重要按钮、链接、高亮 */
    public static final class Primary {
        public static final int DEFAULT = ImColor.rgba(30, 144, 255, 255);     // DodgerBlue
        public static final int LIGHT = ImColor.rgba(100, 180, 255, 255);      // 亮蓝
        public static final int DARK = ImColor.rgba(20, 100, 200, 255);        // 深蓝
    }
    
    /** 成功状态色 */
    public static final class Success {
        public static final int DEFAULT = ImColor.rgba(76, 175, 80, 255);      // Material Green
        public static final int LIGHT = ImColor.rgba(129, 199, 132, 255);
        public static final int DARK = ImColor.rgba(56, 142, 60, 255);
        public static final int BG = ImColor.rgba(76, 175, 80, 26);            // 10% 透明度背景
    }
    
    /** 警告状态色 */
    public static final class Warning {
        public static final int DEFAULT = ImColor.rgba(255, 152, 0, 255);      // Material Orange
        public static final int LIGHT = ImColor.rgba(255, 183, 77, 255);
        public static final int DARK = ImColor.rgba(245, 124, 0, 255);
        public static final int BG = ImColor.rgba(255, 152, 0, 26);
    }
    
    /** 错误状态色 */
    public static final class Error {
        public static final int DEFAULT = ImColor.rgba(244, 67, 54, 255);      // Material Red
        public static final int LIGHT = ImColor.rgba(239, 83, 80, 255);
        public static final int DARK = ImColor.rgba(211, 47, 47, 255);
        public static final int BG = ImColor.rgba(244, 67, 54, 26);
    }
    
    /** 信息提示色 */
    public static final class Info {
        public static final int DEFAULT = ImColor.rgba(33, 150, 243, 255);     // Material Blue
        public static final int LIGHT = ImColor.rgba(100, 181, 246, 255);
        public static final int DARK = ImColor.rgba(25, 118, 210, 255);
        public static final int BG = ImColor.rgba(33, 150, 243, 26);
    }
    
    // ===== 中性色系统 (深色主题) =====
    
    public static final class Neutral {
        // 背景色阶
        public static final int BG_DARKEST = ImColor.rgba(18, 18, 20, 255);    // 最深背景
        public static final int BG_DARKER = ImColor.rgba(24, 24, 27, 255);     // 深背景
        public static final int BG_DEFAULT = ImColor.rgba(30, 30, 33, 255);    // 标准背景
        public static final int BG_LIGHTER = ImColor.rgba(39, 39, 42, 255);    // 浅背景
        public static final int BG_LIGHTEST = ImColor.rgba(48, 48, 52, 255);   // 最浅背景
        
        // 表面色阶（卡片、面板）
        public static final int SURFACE_LOW = ImColor.rgba(39, 39, 42, 255);
        public static final int SURFACE = ImColor.rgba(48, 48, 52, 255);
        public static final int SURFACE_HIGH = ImColor.rgba(58, 58, 62, 255);
        
        // 边框/分隔线
        public static final int BORDER_SUBTLE = ImColor.rgba(68, 68, 72, 255);  // 低调边框
        public static final int BORDER = ImColor.rgba(82, 82, 89, 255);         // 标准边框
        public static final int BORDER_STRONG = ImColor.rgba(113, 113, 122, 255); // 强调边框
        
        // 文本色阶
        public static final int TEXT_PRIMARY = ImColor.rgba(250, 250, 250, 255);   // 主要文本
        public static final int TEXT_SECONDARY = ImColor.rgba(212, 212, 216, 255); // 次要文本
        public static final int TEXT_TERTIARY = ImColor.rgba(161, 161, 170, 255);  // 三级文本
        public static final int TEXT_DISABLED = ImColor.rgba(113, 113, 122, 255);  // 禁用文本
    }
    
    // ===== 中性色系统 (浅色主题) =====
    
    public static final class NeutralLight {
        // 背景色阶
        public static final int BG_DARKEST = ImColor.rgba(250, 250, 250, 255);
        public static final int BG_DARKER = ImColor.rgba(244, 244, 245, 255);
        public static final int BG_DEFAULT = ImColor.rgba(255, 255, 255, 255);
        public static final int BG_LIGHTER = ImColor.rgba(248, 248, 248, 255);
        public static final int BG_LIGHTEST = ImColor.rgba(240, 240, 240, 255);
        
        // 表面色阶
        public static final int SURFACE_LOW = ImColor.rgba(248, 248, 248, 255);
        public static final int SURFACE = ImColor.rgba(255, 255, 255, 255);
        public static final int SURFACE_HIGH = ImColor.rgba(244, 244, 245, 255);
        
        // 边框/分隔线
        public static final int BORDER_SUBTLE = ImColor.rgba(228, 228, 231, 255);
        public static final int BORDER = ImColor.rgba(212, 212, 216, 255);
        public static final int BORDER_STRONG = ImColor.rgba(161, 161, 170, 255);
        
        // 文本色阶
        public static final int TEXT_PRIMARY = ImColor.rgba(24, 24, 27, 255);
        public static final int TEXT_SECONDARY = ImColor.rgba(63, 63, 70, 255);
        public static final int TEXT_TERTIARY = ImColor.rgba(113, 113, 122, 255);
        public static final int TEXT_DISABLED = ImColor.rgba(161, 161, 170, 255);
    }
    
    // ===== 功能色 =====
    
    /** 专业工具特定颜色 */
    public static final class Functional {
        // CAD 专用色
        public static final int GRID_LINE = ImColor.rgba(80, 80, 85, 128);
        public static final int SNAP_POINT = ImColor.rgba(255, 215, 0, 255);       // 金色吸附点
        public static final int SELECTION = ImColor.rgba(30, 144, 255, 180);       // 选择框
        public static final int PREVIEW = ImColor.rgba(255, 165, 0, 180);          // 预览状态
        
        // 道路设计专用色
        public static final int ROAD_CENTERLINE = ImColor.rgba(255, 255, 0, 255);  // 中心线
        public static final int ROAD_BOUNDARY = ImColor.rgba(255, 100, 100, 255);  // 边界线
        public static final int TUNNEL = ImColor.rgba(138, 43, 226, 200);          // 隧道
        public static final int BRIDGE = ImColor.rgba(70, 130, 180, 200);          // 桥梁
    }
    
    private ColorPalette() {
        // 防止实例化
    }
}
```

### 4.2 重构现有颜色定义

**步骤**：

1. **迁移 `PluginUiColors` 到 `ColorPalette`**
```java
// 旧代码
public static final int HINT_GRAY = ImColor.rgba(128, 128, 128, 255);
public static final int STATUS_OK = ImColor.rgba(128, 255, 128, 255);

// 新代码
public static final int HINT_GRAY = ColorPalette.Neutral.TEXT_TERTIARY;
public static final int STATUS_OK = ColorPalette.Success.DEFAULT;
```

2. **统一状态颜色命名**
```java
// 删除重复定义，统一引用
SUCCESS_COLOR -> ColorPalette.Success.DEFAULT
WARNING_COLOR -> ColorPalette.Warning.DEFAULT
ERROR_COLOR -> ColorPalette.Error.DEFAULT
PRIMARY_COLOR -> ColorPalette.Primary.DEFAULT
```

3. **创建颜色工具方法**
```java
public final class ColorUtils {
    /**
     * 根据主题类型获取中性色
     */
    public static int getNeutralColor(ThemeManager.Theme theme, String level) {
        boolean isDark = theme == ThemeManager.Theme.DARK;
        return switch (level) {
            case "bg" -> isDark ? ColorPalette.Neutral.BG_DEFAULT : ColorPalette.NeutralLight.BG_DEFAULT;
            case "surface" -> isDark ? ColorPalette.Neutral.SURFACE : ColorPalette.NeutralLight.SURFACE;
            case "border" -> isDark ? ColorPalette.Neutral.BORDER : ColorPalette.NeutralLight.BORDER;
            case "text" -> isDark ? ColorPalette.Neutral.TEXT_PRIMARY : ColorPalette.NeutralLight.TEXT_PRIMARY;
            default -> throw new IllegalArgumentException("Unknown level: " + level);
        };
    }
    
    /**
     * 调整颜色亮度
     */
    public static int adjustBrightness(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        r = Math.min(255, (int)(r * factor));
        g = Math.min(255, (int)(g * factor));
        b = Math.min(255, (int)(b * factor));
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * 创建半透明版本
     */
    public static int withAlpha(int color, float alpha) {
        int rgb = color & 0x00FFFFFF;
        int a = Math.min(255, Math.max(0, (int)(alpha * 255)));
        return (a << 24) | rgb;
    }
}
```

### 4.3 颜色使用规范

**按钮颜色规范**：

```java
// 主要按钮（Primary Button）
Normal:  ColorPalette.Primary.DEFAULT
Hovered: ColorPalette.Primary.LIGHT
Active:  ColorPalette.Primary.DARK

// 次要按钮（Secondary Button）
Normal:  ColorPalette.Neutral.SURFACE_HIGH
Hovered: ColorPalette.Neutral.SURFACE (亮化 10%)
Active:  ColorPalette.Neutral.SURFACE (暗化 10%)

// 危险按钮（Danger Button）
Normal:  ColorPalette.Error.DEFAULT
Hovered: ColorPalette.Error.LIGHT
Active:  ColorPalette.Error.DARK
```

**状态提示颜色规范**：

```java
// 成功提示
Background: ColorPalette.Success.BG
Border:     ColorPalette.Success.DEFAULT
Text:       ColorPalette.Success.DARK

// 警告提示
Background: ColorPalette.Warning.BG
Border:     ColorPalette.Warning.DEFAULT
Text:       ColorPalette.Warning.DARK

// 错误提示
Background: ColorPalette.Error.BG
Border:     ColorPalette.Error.DEFAULT
Text:       ColorPalette.Error.DARK
```

---

## 5. 主题系统优化

### 5.1 优化深色主题配色

**当前问题**：
- 按钮状态对比度不足
- 文本可读性需要提升
- 整体偏灰，缺少层次感

**优化方案**：

```java
private static ThemeColors createDarkTheme() {
    ThemeColors theme = new ThemeColors();
    
    // === 背景色系统 ===
    theme.background = ColorPalette.Neutral.BG_DEFAULT;           // rgb(30, 30, 33)
    theme.panelBackground = ColorPalette.Neutral.SURFACE;         // rgb(48, 48, 52)
    theme.canvasBackground = ColorPalette.Neutral.BG_DARKEST;     // rgb(18, 18, 20)
    
    // === 文本颜色 ===
    theme.text = ColorPalette.Neutral.TEXT_PRIMARY;               // rgb(250, 250, 250) 更亮
    theme.foreground = theme.text;
    theme.mutedText = ColorPalette.Neutral.TEXT_SECONDARY;        // rgb(212, 212, 216)
    theme.tooltipText = theme.text;
    theme.tooltipBackground = ColorUtils.withAlpha(ColorPalette.Neutral.BG_DARKEST, 0.95f);
    
    // === 边框系统 ===
    theme.border = ColorPalette.Neutral.BORDER;                   // rgb(82, 82, 89) 更清晰
    theme.separatorColor = ColorPalette.Neutral.BORDER_SUBTLE;    // 分隔线稍淡
    
    // === 按钮颜色 (提升对比度) ===
    theme.buttonNormal = ColorPalette.Neutral.SURFACE_LOW;        // rgb(39, 39, 42)
    theme.buttonHovered = ColorPalette.Neutral.SURFACE;           // rgb(48, 48, 52) 明显变亮
    theme.buttonActive = ColorPalette.Neutral.SURFACE_HIGH;       // rgb(58, 58, 62) 更亮
    
    // 选中状态使用主强调色
    theme.buttonSelected = ColorPalette.Primary.DEFAULT;          // rgb(30, 144, 255)
    theme.buttonSelectedHovered = ColorPalette.Primary.LIGHT;     // rgb(100, 180, 255)
    theme.buttonSelectedActive = ColorPalette.Primary.DARK;       // rgb(20, 100, 200)
    
    // 按钮边框
    theme.buttonBorder = ColorPalette.Neutral.BORDER;
    theme.buttonActiveBorder = ColorPalette.Primary.DEFAULT;
    
    // === 输入框颜色 ===
    theme.inputBackground = ColorPalette.Neutral.BG_DARKER;       // rgb(24, 24, 27) 更深
    theme.inputBackgroundHovered = ColorPalette.Neutral.BG_DEFAULT;
    theme.inputBackgroundActive = ColorPalette.Neutral.SURFACE_LOW;
    theme.inputText = theme.text;
    theme.inputBorder = ColorPalette.Neutral.BORDER_SUBTLE;
    
    // === 强调色 ===
    theme.accent = ColorPalette.Primary.DEFAULT;
    
    // === 滑块颜色 ===
    theme.sliderGrab = ColorPalette.Primary.DEFAULT;              // 使用主色
    theme.sliderGrabActive = ColorPalette.Primary.LIGHT;
    
    // === 控件背景 ===
    theme.controlBackground = ColorPalette.Neutral.SURFACE_LOW;
    
    // === 标签页 ===
    theme.tabNormal = ColorPalette.Neutral.BG_DEFAULT;
    theme.tabHovered = ColorPalette.Neutral.SURFACE_LOW;
    theme.tabActive = ColorPalette.Primary.DARK;                  // 激活用主色
    theme.tabText = theme.text;
    theme.tabBorder = ColorPalette.Neutral.BORDER_SUBTLE;
    
    // === 状态文本颜色 ===
    theme.successText = ColorPalette.Success.LIGHT;
    theme.warningText = ColorPalette.Warning.LIGHT;
    theme.errorText = ColorPalette.Error.LIGHT;
    theme.infoText = ColorPalette.Info.LIGHT;
    
    // 初始化派生颜色
    theme.initialize();
    
    return theme;
}
```

### 5.2 优化浅色主题配色

**当前问题**：
- 整体对比度过低
- 按钮状态难以区分
- 缺少视觉重点

**优化方案**：

```java
private static ThemeColors createLightTheme() {
    ThemeColors theme = new ThemeColors();
    
    // === 背景色系统 ===
    theme.background = ColorPalette.NeutralLight.BG_DEFAULT;      // rgb(255, 255, 255)
    theme.panelBackground = ColorPalette.NeutralLight.SURFACE;    // rgb(255, 255, 255)
    theme.canvasBackground = ColorPalette.NeutralLight.BG_LIGHTER; // rgb(248, 248, 248)
    
    // === 文本颜色 ===
    theme.text = ColorPalette.NeutralLight.TEXT_PRIMARY;          // rgb(24, 24, 27) 深色文本
    theme.foreground = theme.text;
    theme.mutedText = ColorPalette.NeutralLight.TEXT_SECONDARY;   // rgb(63, 63, 70)
    theme.tooltipText = theme.text;
    theme.tooltipBackground = ColorUtils.withAlpha(ColorPalette.NeutralLight.SURFACE, 0.98f);
    
    // === 边框系统 ===
    theme.border = ColorPalette.NeutralLight.BORDER;              // rgb(212, 212, 216)
    theme.separatorColor = ColorPalette.NeutralLight.BORDER_SUBTLE;
    
    // === 按钮颜色 (提升对比度) ===
    theme.buttonNormal = ColorPalette.NeutralLight.SURFACE_HIGH;  // rgb(244, 244, 245) 灰色按钮
    theme.buttonHovered = ColorPalette.NeutralLight.BG_LIGHTER;   // rgb(240, 240, 240) 变深
    theme.buttonActive = ColorPalette.NeutralLight.BORDER_SUBTLE; // rgb(228, 228, 231) 更深
    
    // 选中状态
    theme.buttonSelected = ColorPalette.Primary.DEFAULT;
    theme.buttonSelectedHovered = ColorPalette.Primary.LIGHT;
    theme.buttonSelectedActive = ColorPalette.Primary.DARK;
    
    // 按钮边框
    theme.buttonBorder = ColorPalette.NeutralLight.BORDER;
    theme.buttonActiveBorder = ColorPalette.Primary.DEFAULT;
    
    // === 输入框颜色 ===
    theme.inputBackground = ColorPalette.NeutralLight.BG_LIGHTER; // rgb(248, 248, 248)
    theme.inputBackgroundHovered = ColorPalette.NeutralLight.BG_DARKEST; // rgb(250, 250, 250)
    theme.inputBackgroundActive = ColorPalette.NeutralLight.SURFACE;
    theme.inputText = theme.text;
    theme.inputBorder = ColorPalette.NeutralLight.BORDER;
    
    // === 强调色 ===
    theme.accent = ColorPalette.Primary.DEFAULT;
    
    // === 滑块颜色 ===
    theme.sliderGrab = ColorPalette.Primary.DEFAULT;
    theme.sliderGrabActive = ColorPalette.Primary.DARK;
    
    // === 控件背景 ===
    theme.controlBackground = ColorPalette.NeutralLight.SURFACE_HIGH;
    
    // === 标签页 ===
    theme.tabNormal = ColorPalette.NeutralLight.BG_LIGHTER;
    theme.tabHovered = ColorPalette.NeutralLight.BORDER_SUBTLE;
    theme.tabActive = ColorPalette.Primary.LIGHT;                 // 激活用浅主色
    theme.tabText = theme.text;
    theme.tabBorder = ColorPalette.NeutralLight.BORDER;
    
    // === 状态文本颜色 ===
    theme.successText = ColorPalette.Success.DARK;
    theme.warningText = ColorPalette.Warning.DARK;
    theme.errorText = ColorPalette.Error.DARK;
    theme.infoText = ColorPalette.Info.DARK;
    
    // 初始化派生颜色
    theme.initialize();
    
    return theme;
}
```

### 5.3 新增"高对比度"主题

**为视觉障碍用户或强光环境提供高对比度选项**：

```java
/**
 * 创建高对比度深色主题 (WCAG AAA 级别)
 */
private static ThemeColors createHighContrastDarkTheme() {
    ThemeColors theme = new ThemeColors();
    
    // 纯黑背景
    theme.background = ImColor.rgba(0, 0, 0, 255);
    theme.panelBackground = ImColor.rgba(16, 16, 16, 255);
    
    // 纯白文本
    theme.text = ImColor.rgba(255, 255, 255, 255);
    theme.mutedText = ImColor.rgba(200, 200, 200, 255);
    
    // 高对比度按钮
    theme.buttonNormal = ImColor.rgba(64, 64, 64, 255);
    theme.buttonHovered = ImColor.rgba(96, 96, 96, 255);
    theme.buttonActive = ImColor.rgba(128, 128, 128, 255);
    
    // 明亮的强调色
    theme.accent = ImColor.rgba(0, 200, 255, 255);            // 亮青色
    theme.buttonSelected = theme.accent;
    
    // 加粗边框
    theme.border = ImColor.rgba(160, 160, 160, 255);
    theme.windowBorderSize = 2.0f;                            // 更粗的边框
    theme.frameBorderSize = 2.0f;
    
    return theme;
}
```

---

## 6. 组件设计优化

### 6.1 PluginSectionCard 优化

**优化目标**：
- 更清晰的视觉层级
- 更舒适的内边距
- 更优雅的展开/折叠动画

**优化方案**：

```java
public final class PluginSectionCard {
    // === 布局常量优化 ===
    
    // 增加标题内边距，提升呼吸感
    private static final float HEADER_PAD_Y = 8f;              // 4f → 8f (增加)
    
    // 加宽色条，更显眼
    private static final float ACCENT_WIDTH = 4f;              // 3f → 4f
    
    // 增加内容内边距
    private static final float BODY_PAD_X = 12f;               // 8f → 12f
    private static final float BODY_PAD_Y = 10f;               // 6f → 10f
    
    // 增加标题与内容间距
    private static final float CONTENT_TOP_GAP = 6f;           // 新增
    
    // 卡片圆角
    private static final float CARD_ROUNDING = 6f;             // 新增
    
    // 卡片间距
    private static final float CARD_SPACING = 8f;              // 新增
    
    // 阴影（可选，ImGui 需手动实现）
    private static final float SHADOW_OFFSET = 2f;
    private static final int SHADOW_COLOR = ImColor.rgba(0, 0, 0, 20);
    
    // === 渲染优化 ===
    
    public static Result renderResult(
            String id,
            String title,
            String summaryOrNull,
            SectionTone tone,
            boolean defaultOpen,
            Runnable content) {
        
        boolean open = OPEN_STATE.computeIfAbsent(id, ignored -> defaultOpen);
        PluginSectionColors colors = PluginSectionColors.current();
        int accent = colors.accentFor(tone);
        
        // 使用动画高度（平滑过渡）
        float targetHeight = calculateTargetHeight(id, open, content);
        float currentHeight = animateHeight(id, targetHeight);
        
        int pushedColors = 0;
        int pushedVars = 0;
        try {
            // 卡片圆角
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, CARD_ROUNDING);
            pushedVars++;
            
            // 卡片背景色（增加微妙的高光）
            int bgColor = adjustBrightness(colors.surface, open ? 1.05f : 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ChildBg, bgColor);
            pushedColors++;
            
            // 边框颜色（悬停时高亮）
            int borderColor = ImGui.isItemHovered() 
                ? adjustBrightness(colors.border, 1.3f)
                : colors.border;
            ImGui.pushStyleColor(ImGuiCol.Border, borderColor);
            pushedColors++;
            
            // 边框宽度
            ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.5f);  // 1f → 1.5f
            pushedVars++;
            
            // 内边距
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, BODY_PAD_X, BODY_PAD_Y);
            pushedVars++;
            
            // 渲染卡片
            boolean childOpen = ImGui.beginChild(
                id,
                0f,
                currentHeight,
                true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
            
            try {
                if (!childOpen) {
                    return new Result(open, false);
                }
                
                // 绘制装饰性色条
                drawAccentBar(accent, currentHeight, ACCENT_WIDTH);
                
                // 渲染标题（带动画）
                HeaderInteraction header = renderHeaderAnimated(id, title, summaryOrNull, open);
                open = header.open();
                OPEN_STATE.put(id, open);
                
                // 渲染内容（带淡入淡出动画）
                if (open && content != null) {
                    ImGui.dummy(0f, CONTENT_TOP_GAP);
                    
                    // 淡入动画
                    float alpha = getExpandAlpha(id);
                    ImGui.pushStyleVar(ImGuiStyleVar.Alpha, alpha);
                    content.run();
                    ImGui.popStyleVar();
                }
                
            } finally {
                ImGui.endChild();
            }
            
            // 悬停/激活状态检测
            boolean hoveredOrActive = ImGui.isItemHovered() || ImGui.isItemActive();
            
        } finally {
            if (pushedVars > 0) ImGui.popStyleVar(pushedVars);
            if (pushedColors > 0) ImGui.popStyleColor(pushedColors);
        }
        
        // 卡片间距
        ImGui.dummy(0f, CARD_SPACING);
        
        return new Result(open, hoveredOrActive);
    }
    
    // === 动画系统 ===
    
    private static final Map<String, Float> HEIGHT_ANIMATION = new HashMap<>();
    private static final Map<String, Float> ALPHA_ANIMATION = new HashMap<>();
    private static final float ANIMATION_SPEED = 0.15f;  // 插值速度
    
    /**
     * 高度动画插值
     */
    private static float animateHeight(String id, float targetHeight) {
        float current = HEIGHT_ANIMATION.getOrDefault(id, targetHeight);
        float interpolated = lerp(current, targetHeight, ANIMATION_SPEED);
        HEIGHT_ANIMATION.put(id, interpolated);
        return interpolated;
    }
    
    /**
     * 展开淡入动画
     */
    private static float getExpandAlpha(String id) {
        boolean open = OPEN_STATE.getOrDefault(id, false);
        float targetAlpha = open ? 1.0f : 0.0f;
        float current = ALPHA_ANIMATION.getOrDefault(id, targetAlpha);
        float interpolated = lerp(current, targetAlpha, ANIMATION_SPEED * 2f);
        ALPHA_ANIMATION.put(id, interpolated);
        return interpolated;
    }
    
    /**
     * 线性插值
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(t, 1.0f);
    }
    
    // === 渲染标题优化 ===
    
    private static HeaderInteraction renderHeaderAnimated(
            String id, String title, String summaryOrNull, boolean open) {
        
        // 箭头旋转动画 (▶ → ▼)
        String chevron = open ? "▼" : "▶";
        
        // 标题区域
        ImGui.dummy(ACCENT_WIDTH, 0f);
        ImGui.sameLine(0f, 8f);  // 增加间距 4f → 8f
        
        // 可点击区域（使用 Selectable 获得悬停效果）
        boolean clicked = ImGui.selectable("##sec_hdr_" + id, false, 0, 0, 0);
        
        // 获取绘制区域
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX();
        float maxY = ImGui.getItemRectMaxY();
        
        var draw = ImGui.getWindowDrawList();
        float fontSize = ImGui.getFontSize();
        float textY = minY + (maxY - minY - fontSize) * 0.5f;
        
        // 箭头位置（优化对齐）
        float chevronY = textY + (open ? 1f : -fontSize * 0.15f);  // 增加调整幅度
        
        // 文本颜色（悬停时高亮）
        int textColor = ImGui.isItemHovered()
            ? ImGui.getColorU32(ImGuiCol.Text)  // 高亮
            : ColorUtils.adjustBrightness(ImGui.getColorU32(ImGuiCol.Text), 0.9f);
        
        // 绘制箭头
        draw.addText(minX, chevronY, textColor, chevron);
        
        // 绘制标题
        float titleX = minX + ImGui.calcTextSize(chevron).x + 8f;  // 增加间距
        draw.addText(titleX, textY, textColor, title);
        
        // 绘制摘要（右对齐，使用次要文本颜色）
        if (summaryOrNull != null && !summaryOrNull.isBlank()) {
            int summaryColor = ImGui.getColorU32(ImGuiCol.TextDisabled);
            float summaryW = ImGui.calcTextSize(summaryOrNull).x;
            draw.addText(maxX - summaryW - 4f, textY, summaryColor, summaryOrNull);
        }
        
        // 点击切换
        if (clicked) {
            open = !open;
        }
        
        return new HeaderInteraction(open, clicked);
    }
    
    /**
     * 绘制加粗的装饰色条
     */
    private static void drawAccentBar(int accent, float childH, float width) {
        float x = ImGui.getWindowPosX();
        float y = ImGui.getWindowPosY();
        
        // 主色条
        ImGui.getWindowDrawList().addRectFilled(
            x, y, 
            x + width, y + childH, 
            accent,
            CARD_ROUNDING,  // 圆角
            ImDrawFlags.RoundCornersLeft  // 仅左侧圆角
        );
        
        // 可选：添加渐变效果
        int accentLight = ColorUtils.adjustBrightness(accent, 1.3f);
        ImGui.getWindowDrawList().addRectFilledMultiColor(
            x, y,
            x + width * 0.3f, y + childH,
            accentLight, accent, accent, accentLight
        );
    }
}
```

### 6.2 对话框设计优化

**优化 `DialogStyleManager`**：

```java
public class DialogStyleManager {
    
    // === 优化后的布局常量 ===
    
    /** 控件垂直间距 */
    public static final float ITEM_SPACING = 6.0f;             // 4f → 6f
    
    /** 控件水平间距 */
    public static final float ITEM_SPACING_H = 8.0f;           // 4f → 8f
    
    /** 框架内边距（提升控件高度） */
    public static final float FRAME_PADDING_X = 8.0f;          // 新增
    public static final float FRAME_PADDING_Y = 4.0f;          // 2f → 4f
    
    /** 对话框内边距 */
    public static final float PANEL_PADDING = 16.0f;           // 8f → 16f
    
    /** 表单行间距 */
    public static final float ROW_GAP = 8.0f;                  // 4f → 8f
    
    /** 小分组间距 */
    public static final float SUBSECTION_GAP = 12.0f;          // 8f → 12f
    
    /** 大分组间距 */
    public static final float SECTION_GAP = 20.0f;             // 12f → 20f
    
    /** Footer 顶部留白 */
    public static final float FOOTER_TOP_GAP = 16.0f;          // 0f → 16f
    
    /** Footer 按钮间距 */
    public static final float FOOTER_BUTTON_GAP = 8.0f;        // 4f → 8f
    
    /** 标签宽度 */
    public static final float LABEL_WIDTH = 100.0f;            // 84f → 100f
    
    /** 按钮最小宽度 */
    public static final float BUTTON_MIN_WIDTH = 80.0f;        // 68f → 80f
    
    /** 按钮最大宽度 */
    public static final float BUTTON_MAX_WIDTH = 140.0f;       // 120f → 140f
    
    /** 按钮高度 */
    public static final float BUTTON_HEIGHT = 32.0f;           // 新增
    
    /** 对话框圆角 */
    public static final float DIALOG_ROUNDING = 8.0f;          // 新增
    
    // === 优化样式应用 ===
    
    public static DialogStyleScope applyDialogStyle() {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        int colorCount = 0;
        int varCount = 0;
        
        // 推送颜色样式
        ImGui.pushStyleColor(ImGuiCol.WindowBg, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.TitleBg, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
        colorCount++;
        
        // 输入框颜色（使用更深的背景）
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        colorCount++;
        
        // 按钮颜色
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        colorCount++;
        
        // 其他控件颜色
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
        colorCount++;
        
        // Header 颜色（用于折叠标题）
        ImGui.pushStyleColor(ImGuiCol.Header, theme.tabNormal);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, theme.tabHovered);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, theme.tabActive);
        colorCount++;
        
        // 分隔线
        ImGui.pushStyleColor(ImGuiCol.Separator, theme.separatorColor);
        colorCount++;
        
        // 文本颜色
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.TextDisabled, theme.mutedText);
        colorCount++;
        
        // 推送变量样式
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PANEL_PADDING, PANEL_PADDING);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, DIALOG_ROUNDING);  // 添加圆角
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 4.0f);              // 子窗口小圆角
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, DIALOG_ROUNDING);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 4.0f);              // 控件圆角
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 4.0f);               // 滑块圆角
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 4.0f);
        varCount++;
        
        // 间距
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ITEM_SPACING_H, ITEM_SPACING);
        varCount++;
        
        // 框架内边距（影响控件高度）
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FRAME_PADDING_X, FRAME_PADDING_Y);
        varCount++;
        
        // 边框
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1.0f);
        varCount++;
        
        return new DialogStyleScope(colorCount, varCount);
    }
    
    /**
     * 渲染现代化的关闭按钮（右上角）
     */
    public static boolean renderModernCloseButton(String idSuffix) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        float windowPosX = ImGui.getWindowPosX();
        float windowPosY = ImGui.getWindowPosY();
        float windowWidth = ImGui.getWindowWidth();
        
        float titleBarHeight = ImGui.getFrameHeight();
        float btnSize = titleBarHeight - 4f;  // 留出边距
        float btnX = windowPosX + windowWidth - titleBarHeight + 2f;
        float btnY = windowPosY + 2f;
        
        boolean hovered = ImGui.isMouseHoveringRect(
            btnX, btnY, 
            btnX + btnSize, btnY + btnSize, 
            false);
        boolean clicked = hovered && ImGui.isMouseClicked(0);
        
        var drawList = ImGui.getWindowDrawList();
        
        // 背景（悬停时显示）
        if (hovered) {
            int bgColor = ColorPalette.Error.DEFAULT;  // 悬停时显示红色
            drawList.addRectFilled(
                btnX, btnY,
                btnX + btnSize, btnY + btnSize,
                bgColor,
                4.0f  // 圆角
            );
        }
        
        // 绘制 × 符号
        int textColor = hovered 
            ? ImColor.rgba(255, 255, 255, 255)  // 悬停时白色
            : theme.text;
        
        String closeIcon = "×";
        var textSize = ImGui.calcTextSize(closeIcon);
        float textX = btnX + (btnSize - textSize.x) * 0.5f;
        float textY = btnY + (btnSize - textSize.y) * 0.5f;
        
        drawList.addText(textX, textY, textColor, closeIcon);
        
        return clicked;
    }
}
```

### 6.3 新增通用组件

**创建 `ModernButton.java`**：

```java
package com.plot.ui.component;

import com.plot.ui.theme.ColorPalette;
import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * 现代化按钮组件
 */
public final class ModernButton {
    
    public enum ButtonVariant {
        PRIMARY,    // 主要按钮（蓝色）
        SECONDARY,  // 次要按钮（灰色）
        DANGER,     // 危险按钮（红色）
        SUCCESS,    // 成功按钮（绿色）
        GHOST       // 幽灵按钮（透明背景）
    }
    
    public enum ButtonSize {
        SMALL(24f),
        MEDIUM(32f),
        LARGE(40f);
        
        public final float height;
        
        ButtonSize(float height) {
            this.height = height;
        }
    }
    
    /**
     * 渲染现代化按钮
     */
    public static boolean render(
            String label,
            ButtonVariant variant,
            ButtonSize size,
            boolean disabled,
            String tooltip) {
        
        int normalColor, hoveredColor, activeColor;
        
        switch (variant) {
            case PRIMARY:
                normalColor = ColorPalette.Primary.DEFAULT;
                hoveredColor = ColorPalette.Primary.LIGHT;
                activeColor = ColorPalette.Primary.DARK;
                break;
            case SECONDARY:
                normalColor = ColorPalette.Neutral.SURFACE_HIGH;
                hoveredColor = ColorUtils.adjustBrightness(normalColor, 1.1f);
                activeColor = ColorUtils.adjustBrightness(normalColor, 0.9f);
                break;
            case DANGER:
                normalColor = ColorPalette.Error.DEFAULT;
                hoveredColor = ColorPalette.Error.LIGHT;
                activeColor = ColorPalette.Error.DARK;
                break;
            case SUCCESS:
                normalColor = ColorPalette.Success.DEFAULT;
                hoveredColor = ColorPalette.Success.LIGHT;
                activeColor = ColorPalette.Success.DARK;
                break;
            case GHOST:
                normalColor = ImColor.rgba(0, 0, 0, 0);  // 透明
                hoveredColor = ColorPalette.Neutral.SURFACE_LOW;
                activeColor = ColorPalette.Neutral.SURFACE;
                break;
            default:
                normalColor = ColorPalette.Neutral.SURFACE_HIGH;
                hoveredColor = normalColor;
                activeColor = normalColor;
        }
        
        if (disabled) {
            ImGui.beginDisabled();
        }
        
        int colorCount = 0;
        int varCount = 0;
        
        try {
            // 按钮颜色
            ImGui.pushStyleColor(ImGuiCol.Button, normalColor);
            colorCount++;
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
            colorCount++;
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
            colorCount++;
            
            // 按钮圆角
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6.0f);
            varCount++;
            
            // 按钮内边距
            float padX = size == ButtonSize.SMALL ? 8f : (size == ButtonSize.LARGE ? 16f : 12f);
            float padY = (size.height - ImGui.getFontSize()) * 0.5f;
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, padX, padY);
            varCount++;
            
            // 计算按钮宽度
            float buttonWidth = ImGui.calcTextSize(label).x + padX * 2f + 4f;
            
            boolean clicked = ImGui.button(label, buttonWidth, size.height);
            
            // 悬停提示
            if (ImGui.isItemHovered() && tooltip != null && !tooltip.isEmpty()) {
                UIUtils.setTooltip(tooltip);
            }
            
            return clicked;
            
        } finally {
            if (varCount > 0) ImGui.popStyleVar(varCount);
            if (colorCount > 0) ImGui.popStyleColor(colorCount);
            if (disabled) ImGui.endDisabled();
        }
    }
    
    /**
     * 主要按钮（快捷方法）
     */
    public static boolean primary(String label) {
        return render(label, ButtonVariant.PRIMARY, ButtonSize.MEDIUM, false, null);
    }
    
    /**
     * 次要按钮（快捷方法）
     */
    public static boolean secondary(String label) {
        return render(label, ButtonVariant.SECONDARY, ButtonSize.MEDIUM, false, null);
    }
    
    /**
     * 危险按钮（快捷方法）
     */
    public static boolean danger(String label) {
        return render(label, ButtonVariant.DANGER, ButtonSize.MEDIUM, false, null);
    }
}
```

**创建 `StatusBadge.java`**：

```java
package com.plot.ui.component;

import com.plot.ui.theme.ColorPalette;
import imgui.ImGui;

/**
 * 状态徽章组件
 */
public final class StatusBadge {
    
    public enum BadgeType {
        SUCCESS,
        WARNING,
        ERROR,
        INFO,
        NEUTRAL
    }
    
    /**
     * 渲染状态徽章
     */
    public static void render(String text, BadgeType type) {
        int bgColor, borderColor, textColor;
        
        switch (type) {
            case SUCCESS:
                bgColor = ColorPalette.Success.BG;
                borderColor = ColorPalette.Success.DEFAULT;
                textColor = ColorPalette.Success.DARK;
                break;
            case WARNING:
                bgColor = ColorPalette.Warning.BG;
                borderColor = ColorPalette.Warning.DEFAULT;
                textColor = ColorPalette.Warning.DARK;
                break;
            case ERROR:
                bgColor = ColorPalette.Error.BG;
                borderColor = ColorPalette.Error.DEFAULT;
                textColor = ColorPalette.Error.DARK;
                break;
            case INFO:
                bgColor = ColorPalette.Info.BG;
                borderColor = ColorPalette.Info.DEFAULT;
                textColor = ColorPalette.Info.DARK;
                break;
            case NEUTRAL:
            default:
                bgColor = ColorPalette.Neutral.SURFACE_HIGH;
                borderColor = ColorPalette.Neutral.BORDER;
                textColor = ColorPalette.Neutral.TEXT_PRIMARY;
                break;
        }
        
        var drawList = ImGui.getWindowDrawList();
        
        float padding = 6f;
        float rounding = 4f;
        float borderWidth = 1.5f;
        
        var textSize = ImGui.calcTextSize(text);
        float badgeWidth = textSize.x + padding * 2f;
        float badgeHeight = textSize.y + padding;
        
        float cursorX = ImGui.getCursorScreenPosX();
        float cursorY = ImGui.getCursorScreenPosY();
        
        // 背景
        drawList.addRectFilled(
            cursorX, cursorY,
            cursorX + badgeWidth, cursorY + badgeHeight,
            bgColor,
            rounding
        );
        
        // 边框
        drawList.addRect(
            cursorX, cursorY,
            cursorX + badgeWidth, cursorY + badgeHeight,
            borderColor,
            rounding,
            0,
            borderWidth
        );
        
        // 文本
        drawList.addText(
            cursorX + padding,
            cursorY + padding * 0.5f,
            textColor,
            text
        );
        
        // 移动光标
        ImGui.dummy(badgeWidth, badgeHeight);
    }
}
```

---

## 7. 布局与间距优化

### 7.1 建立 8px 网格系统

**创建 `LayoutGrid.java`**：

```java
package com.plot.ui.layout;

/**
 * Plot UI 网格系统
 * 基于 8px 基础单位，所有间距都是 8 的倍数
 */
public final class LayoutGrid {
    
    /** 基础单位：8px */
    public static final float UNIT = 8f;
    
    // === 间距系统 ===
    
    /** 超小间距：4px (0.5 单位) */
    public static final float SPACE_XS = UNIT * 0.5f;
    
    /** 小间距：8px (1 单位) */
    public static final float SPACE_SM = UNIT;
    
    /** 中等间距：16px (2 单位) */
    public static final float SPACE_MD = UNIT * 2f;
    
    /** 大间距：24px (3 单位) */
    public static final float SPACE_LG = UNIT * 3f;
    
    /** 超大间距：32px (4 单位) */
    public static final float SPACE_XL = UNIT * 4f;
    
    /** 特大间距：48px (6 单位) */
    public static final float SPACE_XXL = UNIT * 6f;
    
    // === 组件尺寸 ===
    
    /** 小按钮高度：24px (3 单位) */
    public static final float BUTTON_HEIGHT_SM = UNIT * 3f;
    
    /** 中等按钮高度：32px (4 单位) */
    public static final float BUTTON_HEIGHT_MD = UNIT * 4f;
    
    /** 大按钮高度：40px (5 单位) */
    public static final float BUTTON_HEIGHT_LG = UNIT * 5f;
    
    /** 输入框高度：32px (4 单位) */
    public static final float INPUT_HEIGHT = UNIT * 4f;
    
    /** 工具栏高度：48px (6 单位) */
    public static final float TOOLBAR_HEIGHT = UNIT * 6f;
    
    /** 标题栏高度：40px (5 单位) */
    public static final float TITLEBAR_HEIGHT = UNIT * 5f;
    
    // === 圆角系统 ===
    
    /** 无圆角 */
    public static final float RADIUS_NONE = 0f;
    
    /** 小圆角：2px */
    public static final float RADIUS_SM = 2f;
    
    /** 中等圆角：4px */
    public static final float RADIUS_MD = 4f;
    
    /** 大圆角：6px */
    public static final float RADIUS_LG = 6f;
    
    /** 超大圆角：8px */
    public static final float RADIUS_XL = 8f;
    
    /** 圆形：9999px */
    public static final float RADIUS_FULL = 9999f;
    
    // === 边框宽度 ===
    
    /** 细边框：1px */
    public static final float BORDER_THIN = 1f;
    
    /** 中等边框：1.5px */
    public static final float BORDER_MEDIUM = 1.5f;
    
    /** 粗边框：2px */
    public static final float BORDER_THICK = 2f;
    
    // === 阴影 ===
    
    /** 小阴影偏移 */
    public static final float SHADOW_SM_OFFSET = 2f;
    
    /** 中等阴影偏移 */
    public static final float SHADOW_MD_OFFSET = 4f;
    
    /** 大阴影偏移 */
    public static final float SHADOW_LG_OFFSET = 8f;
    
    /** 阴影透明度 */
    public static final float SHADOW_ALPHA = 0.15f;
    
    private LayoutGrid() {
        // 防止实例化
    }
}
```

### 7.2 重构现有布局常量

**统一使用 `LayoutGrid`**：

```java
// 旧代码
public static final float ITEM_SPACING = 4.0f;
public static final float PANEL_PADDING = 8.0f;
public static final float SECTION_GAP = 12.0f;

// 新代码
public static final float ITEM_SPACING = LayoutGrid.SPACE_SM;      // 8px
public static final float PANEL_PADDING = LayoutGrid.SPACE_MD;     // 16px
public static final float SECTION_GAP = LayoutGrid.SPACE_LG;       // 24px
```

### 7.3 响应式布局指南

**定义不同面板宽度的布局规则**：

```java
public final class ResponsiveLayout {
    
    /** 窄面板：< 300px */
    public static final float NARROW_THRESHOLD = 300f;
    
    /** 中等面板：300px - 600px */
    public static final float MEDIUM_THRESHOLD = 600f;
    
    /** 宽面板：> 600px */
    public static final float WIDE_THRESHOLD = 600f;
    
    /**
     * 根据可用宽度调整布局
     */
    public static LayoutConfig getLayoutConfig(float availWidth) {
        if (availWidth < NARROW_THRESHOLD) {
            return LayoutConfig.NARROW;
        } else if (availWidth < MEDIUM_THRESHOLD) {
            return LayoutConfig.MEDIUM;
        } else {
            return LayoutConfig.WIDE;
        }
    }
    
    public enum LayoutConfig {
        NARROW(
            LayoutGrid.SPACE_XS,   // itemSpacing
            LayoutGrid.SPACE_SM,   // padding
            LayoutGrid.BUTTON_HEIGHT_SM,
            1                       // columns
        ),
        MEDIUM(
            LayoutGrid.SPACE_SM,
            LayoutGrid.SPACE_MD,
            LayoutGrid.BUTTON_HEIGHT_MD,
            2
        ),
        WIDE(
            LayoutGrid.SPACE_MD,
            LayoutGrid.SPACE_LG,
            LayoutGrid.BUTTON_HEIGHT_LG,
            3
        );
        
        public final float itemSpacing;
        public final float padding;
        public final float buttonHeight;
        public final int columns;
        
        LayoutConfig(float itemSpacing, float padding, float buttonHeight, int columns) {
            this.itemSpacing = itemSpacing;
            this.padding = padding;
            this.buttonHeight = buttonHeight;
            this.columns = columns;
        }
    }
}
```

---

## 8. 交互体验优化

### 8.1 添加悬停动效

**优化按钮悬停效果**：

```java
public final class HoverEffects {
    
    private static final Map<String, Float> HOVER_ANIMATIONS = new HashMap<>();
    private static final float ANIMATION_SPEED = 0.2f;
    
    /**
     * 渲染带悬停动效的按钮
     */
    public static boolean animatedButton(String id, String label, float width, float height) {
        boolean hovered = ImGui.isItemHovered();
        
        // 悬停动画（0.0 → 1.0）
        float targetScale = hovered ? 1.0f : 0.0f;
        float currentScale = HOVER_ANIMATIONS.getOrDefault(id, 0.0f);
        float animatedScale = lerp(currentScale, targetScale, ANIMATION_SPEED);
        HOVER_ANIMATIONS.put(id, animatedScale);
        
        // 插值颜色
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        int normalColor = theme.buttonNormal;
        int hoveredColor = theme.buttonHovered;
        int buttonColor = lerpColor(normalColor, hoveredColor, animatedScale);
        
        // 插值边框宽度
        float borderWidth = 1.0f + animatedScale * 0.5f;  // 1px → 1.5px
        
        // 应用样式
        ImGui.pushStyleColor(ImGuiCol.Button, buttonColor);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, borderWidth);
        
        boolean clicked = ImGui.button(label, width, height);
        
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        
        return clicked;
    }
    
    /**
     * 线性插值颜色
     */
    private static int lerpColor(int colorA, int colorB, float t) {
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;
        int aA = (colorA >> 24) & 0xFF;
        
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;
        int aB = (colorB >> 24) & 0xFF;
        
        int r = (int)(rA + (rB - rA) * t);
        int g = (int)(gA + (gB - gA) * t);
        int b = (int)(bA + (bB - bA) * t);
        int a = (int)(aA + (aB - aA) * t);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(t, 1.0f);
    }
}
```

### 8.2 提升通知系统

**创建 `ToastNotification.java`**：

```java
package com.plot.ui.component;

import com.plot.ui.theme.ColorPalette;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toast 通知系统
 */
public final class ToastNotification {
    
    private static final List<Toast> activeToasts = new ArrayList<>();
    private static final float TOAST_WIDTH = 300f;
    private static final float TOAST_HEIGHT = 60f;
    private static final float TOAST_SPACING = 8f;
    private static final float TOAST_PADDING = 12f;
    private static final float TOAST_DURATION = 3000f;  // 3秒
    private static final float FADE_DURATION = 300f;    // 淡出 300ms
    
    public enum ToastType {
        SUCCESS, WARNING, ERROR, INFO
    }
    
    private static class Toast {
        String message;
        ToastType type;
        long createdTime;
        float yOffset;
        
        Toast(String message, ToastType type) {
            this.message = message;
            this.type = type;
            this.createdTime = System.currentTimeMillis();
            this.yOffset = 0f;
        }
        
        float getAlpha() {
            long elapsed = System.currentTimeMillis() - createdTime;
            if (elapsed < TOAST_DURATION) {
                return 1.0f;
            } else {
                float fadeProgress = (elapsed - TOAST_DURATION) / FADE_DURATION;
                return Math.max(0f, 1.0f - fadeProgress);
            }
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - createdTime > TOAST_DURATION + FADE_DURATION;
        }
    }
    
    /**
     * 显示 Toast 通知
     */
    public static void show(String message, ToastType type) {
        activeToasts.add(new Toast(message, type));
    }
    
    /**
     * 渲染所有 Toast（每帧调用）
     */
    public static void render() {
        if (activeToasts.isEmpty()) {
            return;
        }
        
        float viewportWidth = ImGui.getMainViewport().getSizeX();
        float viewportHeight = ImGui.getMainViewport().getSizeY();
        
        float startX = viewportWidth - TOAST_WIDTH - 20f;
        float startY = viewportHeight - 20f;
        
        // 移除过期的 Toast
        Iterator<Toast> iterator = activeToasts.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired()) {
                iterator.remove();
            }
        }
        
        // 渲染每个 Toast
        float currentY = startY;
        for (Toast toast : activeToasts) {
            renderToast(toast, startX, currentY);
            currentY -= (TOAST_HEIGHT + TOAST_SPACING);
        }
    }
    
    private static void renderToast(Toast toast, float x, float y) {
        float alpha = toast.getAlpha();
        if (alpha <= 0f) {
            return;
        }
        
        // 获取颜色
        int bgColor, borderColor, iconColor;
        String icon;
        
        switch (toast.type) {
            case SUCCESS:
                bgColor = ColorUtils.withAlpha(ColorPalette.Success.BG, alpha);
                borderColor = ColorUtils.withAlpha(ColorPalette.Success.DEFAULT, alpha);
                iconColor = ColorUtils.withAlpha(ColorPalette.Success.DEFAULT, alpha);
                icon = "✓";
                break;
            case WARNING:
                bgColor = ColorUtils.withAlpha(ColorPalette.Warning.BG, alpha);
                borderColor = ColorUtils.withAlpha(ColorPalette.Warning.DEFAULT, alpha);
                iconColor = ColorUtils.withAlpha(ColorPalette.Warning.DEFAULT, alpha);
                icon = "⚠";
                break;
            case ERROR:
                bgColor = ColorUtils.withAlpha(ColorPalette.Error.BG, alpha);
                borderColor = ColorUtils.withAlpha(ColorPalette.Error.DEFAULT, alpha);
                iconColor = ColorUtils.withAlpha(ColorPalette.Error.DEFAULT, alpha);
                icon = "✕";
                break;
            case INFO:
            default:
                bgColor = ColorUtils.withAlpha(ColorPalette.Info.BG, alpha);
                borderColor = ColorUtils.withAlpha(ColorPalette.Info.DEFAULT, alpha);
                iconColor = ColorUtils.withAlpha(ColorPalette.Info.DEFAULT, alpha);
                icon = "ℹ";
                break;
        }
        
        // 设置窗口位置
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(TOAST_WIDTH, TOAST_HEIGHT);
        
        // 窗口标志
        int flags = ImGuiWindowFlags.NoDecoration 
                  | ImGuiWindowFlags.NoMove 
                  | ImGuiWindowFlags.NoSavedSettings
                  | ImGuiWindowFlags.NoFocusOnAppearing
                  | ImGuiWindowFlags.NoNav;
        
        ImGui.pushStyleColor(ImGuiCol.WindowBg, bgColor);
        ImGui.pushStyleColor(ImGuiCol.Border, borderColor);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 8f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 2f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, TOAST_PADDING, TOAST_PADDING);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, alpha);
        
        if (ImGui.begin("##toast_" + toast.hashCode(), flags)) {
            // 图标
            ImGui.textColored(iconColor, icon);
            ImGui.sameLine();
            
            // 消息文本
            ImGui.pushTextWrapPos(TOAST_WIDTH - TOAST_PADDING * 2f - 30f);
            ImGui.textWrapped(toast.message);
            ImGui.popTextWrapPos();
        }
        ImGui.end();
        
        ImGui.popStyleVar(4);
        ImGui.popStyleColor(2);
    }
}
```

**使用示例**：

```java
// 显示成功通知
ToastNotification.show("保存成功！", ToastNotification.ToastType.SUCCESS);

// 显示警告
ToastNotification.show("参数超出范围，已自动调整", ToastNotification.ToastType.WARNING);

// 显示错误
ToastNotification.show("无法连接到服务器", ToastNotification.ToastType.ERROR);

// 在主渲染循环中调用
ToastNotification.render();
```

### 8.3 改进输入控件反馈

**优化输入框焦点指示**：

```java
public final class InputFeedback {
    
    /**
     * 渲染带焦点动画的输入框
     */
    public static boolean animatedInput(String id, ImString buffer, float width) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        boolean isActive = ImGui.isItemActive();
        boolean wasFocused = isActive;
        
        // 焦点动画
        float focusScale = wasFocused ? 1.0f : 0.0f;
        
        // 边框颜色插值
        int borderColor = lerpColor(
            theme.inputBorder,
            ColorPalette.Primary.DEFAULT,
            focusScale
        );
        
        // 边框宽度
        float borderWidth = 1.0f + focusScale * 0.5f;
        
        ImGui.pushStyleColor(ImGuiCol.Border, borderColor);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, borderWidth);
        ImGui.setNextItemWidth(width);
        
        boolean changed = ImGui.inputText("##" + id, buffer);
        
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        
        return changed;
    }
    
    /**
     * 渲染带验证状态的输入框
     */
    public static boolean validatedInput(
            String id, 
            ImString buffer, 
            float width,
            boolean isValid,
            String errorMessage) {
        
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        // 验证状态颜色
        int borderColor = isValid 
            ? theme.inputBorder
            : ColorPalette.Error.DEFAULT;
        
        ImGui.pushStyleColor(ImGuiCol.Border, borderColor);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.5f);
        ImGui.setNextItemWidth(width);
        
        boolean changed = ImGui.inputText("##" + id, buffer);
        
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        
        // 显示错误消息
        if (!isValid && errorMessage != null) {
            ImGui.textColored(ColorPalette.Error.DEFAULT, errorMessage);
        }
        
        return changed;
    }
}
```

---

## 9. 实施路线图

### 阶段一：基础优化（1-2 周）

**目标**：建立统一的颜色和间距系统

✅ **任务清单**：
1. 创建 `ColorPalette.java`，定义统一颜色体系
2. 创建 `LayoutGrid.java`，定义 8px 网格系统
3. 创建 `ColorUtils.java`，提供颜色工具方法
4. 重构 `UITheme` 中的深色/浅色主题配色
5. 更新 `PluginUiColors` 引用新的颜色系统
6. 更新 `DialogStyleManager` 间距常量

**验收标准**：
- 所有颜色定义来自 `ColorPalette`
- 所有间距使用 `LayoutGrid` 常量
- 深色主题按钮对比度提升 30%
- 浅色主题可读性明显改善

### 阶段二：组件优化（2-3 周）

**目标**：提升核心组件视觉质量

✅ **任务清单**：
1. 优化 `PluginSectionCard`：
   - 增加内边距和色条宽度
   - 添加高度动画和淡入淡出效果
   - 优化箭头对齐和悬停效果
   
2. 优化对话框系统：
   - 增加 `DialogStyleManager` 间距
   - 添加对话框圆角
   - 优化关闭按钮设计

3. 创建新组件：
   - `ModernButton` - 现代化按钮
   - `StatusBadge` - 状态徽章
   - `ToastNotification` - Toast 通知系统

**验收标准**：
- 卡片展开/折叠有平滑动画
- 对话框布局更舒适，间距合理
- 新组件在实际界面中应用

### 阶段三：交互优化（1-2 周）

**目标**：提升交互体验和动效

✅ **任务清单**：
1. 实现 `HoverEffects` 悬停动画系统
2. 优化输入框焦点指示（`InputFeedback`）
3. 集成 `ToastNotification` 到主界面
4. 添加按钮点击涟漪效果（可选）
5. 优化滑块交互反馈

**验收标准**：
- 所有按钮有悬停动画
- 输入框焦点状态清晰
- Toast 通知动画流畅
- 整体交互感觉现代、流畅

### 阶段四：全局应用（2-3 周）

**目标**：在所有模块中应用新设计

✅ **任务清单**：
1. 更新道路设计模块 UI（`plugin.road.ui.*`）
2. 更新建筑模块 UI（`plugin.building.ui.*`）
3. 更新电力线模块 UI（`plugin.powerline.ui.*`）
4. 更新土方模块 UI（`plugin.earthwork.ui.*`）
5. 更新图案模块 UI（`plugin.pattern.ui.*`）
6. 更新核心工具栏和面板（`ui.toolbar.*`, `ui.panel.*`）

**验收标准**：
- 所有模块视觉风格统一
- 颜色使用符合新规范
- 间距遵循 8px 网格
- 无遗漏的旧样式

### 阶段五：测试与文档（1 周）

**目标**：确保质量并编写文档

✅ **任务清单**：
1. 编写 UI 组件使用文档
2. 创建设计规范文档（Design System）
3. 编写主题切换测试用例
4. 进行可用性测试
5. 收集用户反馈并迭代

**验收标准**：
- 完整的 UI 组件文档
- 设计规范清晰易懂
- 主题切换无异常
- 用户反馈积极

---

## 附录

### A. 对比度计算工具

```java
public final class ContrastChecker {
    
    /**
     * 计算相对亮度 (WCAG 2.1 标准)
     */
    private static double getRelativeLuminance(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;
        
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
    
    /**
     * 计算对比度
     * @return 对比度 (1.0 - 21.0)
     */
    public static double getContrastRatio(int color1, int color2) {
        double lum1 = getRelativeLuminance(color1);
        double lum2 = getRelativeLuminance(color2);
        
        double lighter = Math.max(lum1, lum2);
        double darker = Math.min(lum1, lum2);
        
        return (lighter + 0.05) / (darker + 0.05);
    }
    
    /**
     * 检查是否符合 WCAG AA 标准（≥ 4.5:1）
     */
    public static boolean meetsWCAG_AA(int textColor, int bgColor) {
        return getContrastRatio(textColor, bgColor) >= 4.5;
    }
    
    /**
     * 检查是否符合 WCAG AAA 标准（≥ 7.0:1）
     */
    public static boolean meetsWCAG_AAA(int textColor, int bgColor) {
        return getContrastRatio(textColor, bgColor) >= 7.0;
    }
}
```

### B. 颜色生成工具

```java
public final class ColorGenerator {
    
    /**
     * 从基础颜色生成色阶
     * @param baseColor 基础颜色
     * @param steps 色阶数量
     * @return 色阶数组（从深到浅）
     */
    public static int[] generateColorScale(int baseColor, int steps) {
        int[] scale = new int[steps];
        
        for (int i = 0; i < steps; i++) {
            float factor = 0.5f + (i / (float)(steps - 1)) * 1.0f;  // 0.5 - 1.5
            scale[i] = ColorUtils.adjustBrightness(baseColor, factor);
        }
        
        return scale;
    }
    
    /**
     * 生成互补色
     */
    public static int getComplementaryColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        return (a << 24) | ((255 - r) << 16) | ((255 - g) << 8) | (255 - b);
    }
    
    /**
     * 生成三等分配色
     */
    public static int[] getTriadicColors(int color) {
        // HSV 转换实现（略）
        // 返回色相相差 120° 的三个颜色
        return new int[]{color, /* color2 */, /* color3 */};
    }
}
```

### C. 参考资源

**设计系统参考**：
- [Material Design 3](https://m3.material.io/)
- [Ant Design](https://ant.design/)
- [Carbon Design System](https://carbondesignsystem.com/)
- [Fluent 2 Design](https://fluent2.microsoft.design/)

**可访问性指南**：
- [WCAG 2.1](https://www.w3.org/WAI/WCAG21/quickref/)
- [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/)

**ImGui 资源**：
- [ImGui GitHub](https://github.com/ocornut/imgui)
- [ImGui Manual](https://github.com/ocornut/imgui/wiki)
- [imgui-java](https://github.com/SpaiR/imgui-java)

**颜色工具**：
- [Coolors.co](https://coolors.co/) - 配色方案生成
- [Paletton](https://paletton.com/) - 色彩理论工具
- [Adobe Color](https://color.adobe.com/) - 专业配色工具

---

## 总结

本优化建议文档针对 Plot 项目的 UI 系统提出了全面的改进方案，核心要点：

### 🎯 核心改进

1. **统一颜色系统**：建立 `ColorPalette` 作为唯一颜色源，消除碎片化
2. **8px 网格系统**：通过 `LayoutGrid` 建立一致的视觉节奏
3. **提升对比度**：深色/浅色主题对比度符合 WCAG AA 标准
4. **精致组件设计**：优化卡片、按钮、对话框等核心组件
5. **流畅交互动效**：添加悬停、焦点、展开/折叠动画

### 💡 设计原则

- **专业性**：清晰的信息层级，精准的数据呈现
- **清晰性**：高对比度，合理留白，明确状态反馈
- **艺术性**：精致细节，和谐色彩，流畅动效

### 📅 实施建议

建议分 5 个阶段逐步推进，总耗时约 **8-11 周**：
1. 基础优化（1-2 周）
2. 组件优化（2-3 周）
3. 交互优化（1-2 周）
4. 全局应用（2-3 周）
5. 测试与文档（1 周）

通过系统化的优化，Plot 项目的 UI 将从"功能可用"提升到"专业精致"的水平，为用户提供更优秀的 CAD 设计体验。

---

**文档结束**
