#!/usr/bin/env python3
"""Batch-13: command errors, tool validation, and core validation i18n."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / "src/main/java/com/plot"
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "error.plot.command.chamfer_failed": ("Chamfer operation failed: %s", "倒角操作失败: %s"),
    "error.plot.command.fillet_failed": ("Fillet operation failed: %s", "圆角操作失败: %s"),
    "error.plot.command.transform_execute_failed": ("Transform command execution failed", "变换命令执行失败"),
    "error.plot.command.transform_undo_failed": ("Transform command undo failed", "变换命令撤销失败"),
    "error.plot.command.clear_canvas_failed": ("Failed to clear canvas", "清除画布失败"),
    "error.plot.command.clear_canvas_undo_failed": ("Failed to undo clear canvas", "撤销清除画布失败"),
    "error.plot.command.clear_canvas_redo_failed": ("Failed to redo clear canvas", "重做清除画布失败"),
    "error.plot.validation.shapes_null": ("Shape list cannot be null", "原始图形列表不能为空"),
    "error.plot.validation.transform_params_null": ("Transform parameters cannot be null", "变换参数不能为空"),
    "error.plot.validation.transform_mode_null": ("Transform mode cannot be null", "变换模式不能为null"),
    "error.plot.validation.modify_params_null": ("Modify parameters cannot be null", "修改参数不能为空"),
    "error.plot.validation.shape_null": ("Shape cannot be null", "图形对象不能为空"),
    "error.plot.validation.drag_vector_null": ("Drag vector cannot be null", "拖拽向量不能为空"),
    "error.plot.snap.candidates_null": ("Candidate list cannot be null", "候选点列表不能为 null"),
    "error.plot.snap.candidate_null": ("Candidate point cannot be null", "候选点不能为 null"),
    "error.plot.tool.chamfer.distance_out_of_range": (
        "Distance out of range [%s, %s]: %s",
        "距离值超出范围 [%s, %s]: %s",
    ),
    "error.plot.tool.chamfer.invalid_distance": ("Invalid distance value: %s", "无效的距离值: %s"),
    "error.plot.tool.fillet.radius_out_of_range": (
        "Radius out of range [%s, %s]: %s",
        "半径值超出范围 [%s, %s]: %s",
    ),
    "error.plot.tool.fillet.invalid_radius": ("Invalid radius value: %s", "无效的半径值: %s"),
    "error.plot.validation.divisor_zero": ("Divisor cannot be zero", "除数不能为零"),
    "error.plot.validation.task_null_negative_delay": (
        "Task cannot be null and delay cannot be negative",
        "任务不能为null，延迟不能为负数",
    ),
    "error.plot.validation.plugin_instance_null": ("Plugin instance cannot be null", "插件实例不能为空"),
    "error.plot.style.text.serialize_min_parts": (
        "Invalid serialization format: expected at least 7 parts, got %d",
        "序列化格式错误：需要至少7个部分，实际：%d",
    ),
    "error.plot.draw.commit_failed": ("Failed to commit shape", "图形提交失败"),
    "error.plot.draw.multi_line_submit_failed": ("Failed to submit multi-line drawing", "提交多线失败"),
    "error.plot.draw.submit_sub_line_failed": ("Failed to submit sub-line to AppState", "无法提交子线到AppState"),
    "error.plot.tool.create_drawing_failed": ("Failed to create drawing tool: %s", "无法创建绘图工具: %s"),
    "error.plot.tool.create_modify_failed": ("Failed to create modify tool: %s", "无法创建修改工具: %s"),
}

REPLACEMENTS = {
    "core/command/commands/ChamferCommand.java": [
        ('throw new RuntimeException("倒角操作失败: " + e.getMessage(), e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.chamfer_failed", e.getMessage()), e)'),
    ],
    "core/command/commands/FilletCommand.java": [
        ('throw new RuntimeException("圆角操作失败: " + e.getMessage(), e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.fillet_failed", e.getMessage()), e)'),
    ],
    "core/command/commands/TransformCommand.java": [
        ('Objects.requireNonNull(originalShapes, "原始图形列表不能为空")',
         'Objects.requireNonNull(originalShapes, PlotI18n.error("error.plot.validation.shapes_null"))'),
        ('Objects.requireNonNull(transformParams, "变换参数不能为空")',
         'Objects.requireNonNull(transformParams, PlotI18n.error("error.plot.validation.transform_params_null"))'),
        ('throw new RuntimeException("变换命令执行失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.transform_execute_failed"), e)'),
        ('throw new RuntimeException("变换命令撤销失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.transform_undo_failed"), e)'),
    ],
    "core/command/commands/ClearCanvasCommand.java": [
        ('throw new RuntimeException("清除画布失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.clear_canvas_failed"), e)'),
        ('throw new RuntimeException("撤销清除画布失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.clear_canvas_undo_failed"), e)'),
        ('throw new RuntimeException("重做清除画布失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.command.clear_canvas_redo_failed"), e)'),
    ],
    "core/snap/SnapPriorityEvaluator.java": [
        ('throw new IllegalArgumentException("候选点列表不能为 null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.snap.candidates_null"))'),
        ('throw new IllegalArgumentException("候选点不能为 null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.snap.candidate_null"))'),
    ],
    "core/state/AppState.java": [
        ('throw new IllegalArgumentException("任务不能为null，延迟不能为负数")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.task_null_negative_delay"))'),
    ],
    "core/plugin/EmptyPluginLoader.java": [
        ('throw new IllegalArgumentException("插件实例不能为空")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.plugin_instance_null"))'),
    ],
    "api/geometry/Vec2d.java": [
        ('throw new IllegalArgumentException("除数不能为零")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.divisor_zero"))'),
    ],
    "core/graphics/style/TextStyle.java": [
        ('throw new IllegalArgumentException("序列化数据不能为空")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.empty_data"))'),
        ('throw new IllegalArgumentException("反序列化失败：" + e.getMessage(), e)',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.deserialize_failed") + ": " + e.getMessage(), e)'),
        ('throw new IllegalArgumentException("序列化格式错误：需要至少7个部分，实际：" + parts.length)',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.style.text.serialize_min_parts", parts.length))'),
    ],
    "ui/tools/impl/modify/ChamferTool.java": [
        ('throw new IllegalArgumentException("距离值超出范围 [" + MIN_DISTANCE + ", " + MAX_DISTANCE + "]: " + distance)',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.tool.chamfer.distance_out_of_range", MIN_DISTANCE, MAX_DISTANCE, distance))'),
        ('throw new IllegalArgumentException("无效的距离值: " + value)',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.tool.chamfer.invalid_distance", value))'),
    ],
    "ui/tools/impl/modify/FilletTool.java": [
        ('throw new IllegalArgumentException("半径值超出范围 [" + FilletConstants.MIN_RADIUS + ", " + FilletConstants.MAX_RADIUS + "]: " + radius)',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.tool.fillet.radius_out_of_range", FilletConstants.MIN_RADIUS, FilletConstants.MAX_RADIUS, radius))'),
        ('throw new IllegalArgumentException("无效的半径值: " + value)',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.tool.fillet.invalid_radius", value))'),
    ],
    "ui/tools/impl/drawing/DrawingTool.java": [
        ('throw new RuntimeException("图形提交失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.draw.commit_failed"), e)'),
    ],
    "ui/tools/impl/drawing/LineTool.java": [
        ('throw new RuntimeException("无法提交子线到AppState", reflectionEx)',
         'throw new RuntimeException(PlotI18n.error("error.plot.draw.submit_sub_line_failed"), reflectionEx)'),
        ('throw new RuntimeException("提交多线失败", e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.draw.multi_line_submit_failed"), e)'),
    ],
    "ui/tools/DrawingToolsModule.java": [
        ('throw new RuntimeException("无法创建绘图工具: " + e.getMessage(), e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.tool.create_drawing_failed", e.getMessage()), e)'),
    ],
    "ui/tools/ModifyToolsModule.java": [
        ('throw new RuntimeException("无法创建修改工具: " + e.getMessage(), e)',
         'throw new RuntimeException(PlotI18n.error("error.plot.tool.create_modify_failed", e.getMessage()), e)'),
    ],
    "ui/tools/impl/modify/helper/TransformHandler.java": [
        ('Objects.requireNonNull(shapes, "图形列表不能为空")',
         'Objects.requireNonNull(shapes, PlotI18n.error("error.plot.validation.shapes_null"))'),
        ('Objects.requireNonNull(parameters, "修改参数不能为空")',
         'Objects.requireNonNull(parameters, PlotI18n.error("error.plot.validation.modify_params_null"))'),
        ('Objects.requireNonNull(shape, "图形对象不能为空")',
         'Objects.requireNonNull(shape, PlotI18n.error("error.plot.validation.shape_null"))'),
        ('Objects.requireNonNull(originalShapes, "原始图形列表不能为空")',
         'Objects.requireNonNull(originalShapes, PlotI18n.error("error.plot.validation.shapes_null"))'),
        ('Objects.requireNonNull(transformParams, "变换参数不能为空")',
         'Objects.requireNonNull(transformParams, PlotI18n.error("error.plot.validation.transform_params_null"))'),
        ('Objects.requireNonNull(params, "变换参数不能为空")',
         'Objects.requireNonNull(params, PlotI18n.error("error.plot.validation.transform_params_null"))'),
        ('Objects.requireNonNull(params.getDragVector(), "拖拽向量不能为空")',
         'Objects.requireNonNull(params.getDragVector(), PlotI18n.error("error.plot.validation.drag_vector_null"))'),
        ('Objects.requireNonNull(params.getMode(), "变换模式不能为空")',
         'Objects.requireNonNull(params.getMode(), PlotI18n.error("error.plot.validation.transform_mode_null"))'),
    ],
    "ui/tools/impl/modify/strategy/TransformWithSelectionStrategy.java": [
        ('Objects.requireNonNull(mode, "变换模式不能为null")',
         'Objects.requireNonNull(mode, PlotI18n.error("error.plot.validation.transform_mode_null"))'),
    ],
}

IMPORT = "import com.plot.utils.PlotI18n;\n"


def ensure_import(content: str) -> str:
    if "import com.plot.utils.PlotI18n;" in content:
        return content
    if "package " in content:
        lines = content.split("\n", 1)
        if len(lines) == 2 and lines[1].startswith("\n"):
            return lines[0] + "\n\n" + IMPORT + lines[1].lstrip("\n")
        idx = content.find("\n\n")
        if idx != -1:
            return content[: idx + 2] + IMPORT + content[idx + 2 :]
    return IMPORT + content


def apply_replacements() -> int:
    total = 0
    for rel, pairs in REPLACEMENTS.items():
        path = SRC / rel
        text = path.read_text(encoding="utf-8")
        count = 0
        for old, new in pairs:
            if old not in text:
                print(f"WARN missing in {rel}: {old[:70]}...")
                continue
            text = text.replace(old, new)
            count += 1
        if count:
            text = ensure_import(text)
            path.write_text(text, encoding="utf-8")
            print(f"{rel}: {count} replacements")
            total += count
    return total


def merge_lang(path: Path, lang_idx: int) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    added = 0
    for key, values in KEYS.items():
        if key not in data:
            data[key] = values[lang_idx]
            added += 1
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{path.name}: added {added} keys")


if __name__ == "__main__":
    n = apply_replacements()
    merge_lang(LANG / "en_us.json", 0)
    merge_lang(LANG / "zh_cn.json", 1)
    print(f"Done. Replacements: {n}, keys: {len(KEYS)}")
