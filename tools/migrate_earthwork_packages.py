#!/usr/bin/env python3
"""批量迁移 com.plot.plugin.earthwork 根包类到子包（terrain/、design/ 等）。"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_ROOT = ROOT / "src"

# 类名 -> 目标子包（相对 com.plot.plugin.earthwork）
MOVES: dict[str, str] = {
    # terrain
    "TerrainSnapshot": "terrain",
    "TerrainSnapshotCache": "terrain",
    "TerrainSurfaceSampler": "terrain",
    "TerrainBoundaryBlender": "terrain",
    # design
    "DesignTerrainComposer": "design",
    "DesignSurfaceResolver": "design",
    "GradingSurfaceResolver": "design",
    "MultiPlaneSurfaceEvaluator": "design",
    "ExcavationPitSurfaceEvaluator": "design",
    "RoadCorridorSurfaceResolver": "design",
    "RoadCorridorBaker": "design",
    "BuildingFootprintResolver": "design",
    "BuildingFootprintLookup": "design",
    "RoadSurfaceLookup": "design",
    # solver
    "EarthworkBalanceUtils": "solver",
    "SiteWideBalanceAdjuster": "solver",
    "ZoneAllocationBalanceAdjuster": "solver",
    "EarthworkAllocationMatrix": "solver",
    # volume
    "EarthworkVolumeReport": "volume",
    "SiteEarthworkReport": "volume",
    "EarthworkProjectReport": "volume",
    # grading
    "DesignTerrainGrid": "grading",
    "DesignTerrainCell": "grading",
    "SlopeBenchProfile": "grading",
    "GradingPlane": "grading",
    "BreaklineClassifier": "grading",
    "ZoneOverlapAnalyzer": "grading",
    # geometry
    "ZoneBoundarySlopeApplicator": "geometry",
    "ZoneBoundaryRetainingEdgeAdapter": "geometry",
    "RetainingEdgeBreaklineAdapter": "geometry",
    "EarthworkGeometryUtils": "geometry",
    # voxel
    "RetainingWallGenerator": "voxel",
}

OLD_PKG = "com.plot.plugin.earthwork"
PACKAGE_LINE = re.compile(r"^package com\.plot\.plugin\.earthwork;\s*$", re.MULTILINE)


def target_package(subpkg: str) -> str:
    return f"{OLD_PKG}.{subpkg}"


def move_sources() -> list[Path]:
    moved: list[Path] = []
    base = SRC_ROOT / "main/java/com/plot/plugin/earthwork"
    for class_name, subpkg in MOVES.items():
        src = base / f"{class_name}.java"
        if not src.exists():
            print(f"SKIP missing: {src}")
            continue
        dest_dir = base / subpkg
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / f"{class_name}.java"
        if dest.exists():
            print(f"SKIP exists: {dest}")
            continue
        shutil.move(str(src), str(dest))
        text = dest.read_text(encoding="utf-8")
        text = PACKAGE_LINE.sub(f"package {target_package(subpkg)};", text, count=1)
        dest.write_text(text, encoding="utf-8")
        moved.append(dest)
        print(f"MOVED {class_name} -> {subpkg}/")
    return moved


def update_imports() -> None:
    # 长类名优先，避免子串误替换
    names = sorted(MOVES.keys(), key=len, reverse=True)
    java_files = list(SRC_ROOT.rglob("*.java"))
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        original = text
        for class_name in names:
            subpkg = MOVES[class_name]
            new_import = f"import {target_package(subpkg)}.{class_name};"
            old_import = f"import {OLD_PKG}.{class_name};"
            text = text.replace(old_import, new_import)
            # 无 import 的完全限定名（跳过已迁移文件内的同包引用由编译器处理）
            text = re.sub(
                rf"\b{re.escape(OLD_PKG)}\.{re.escape(class_name)}\b",
                f"{target_package(subpkg)}.{class_name}",
                text,
            )
        if text != original:
            path.write_text(text, encoding="utf-8")
            print(f"UPDATED imports: {path.relative_to(ROOT)}")


def fix_wildcard_files() -> None:
    """为仍使用 earthwork.* 通配符的文件补充显式 import。"""
    wildcard_import = f"import {OLD_PKG}.*;"
    java_files = list(SRC_ROOT.rglob("*.java"))
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        if wildcard_import not in text:
            continue
        extra: list[str] = []
        for class_name, subpkg in sorted(MOVES.items()):
            pkg = target_package(subpkg)
            if f"import {pkg}.{class_name};" in text:
                continue
            # 简单启发：类名作为标识符出现且非 import 行
            if re.search(rf"(?<!\.)\b{re.escape(class_name)}\b", text):
                extra.append(f"import {pkg}.{class_name};")
        if not extra:
            continue
        block = "\n".join(sorted(extra))
        text = text.replace(wildcard_import, wildcard_import + "\n" + block)
        path.write_text(text, encoding="utf-8")
        print(f"WILDCARD fix: {path.relative_to(ROOT)}")


def fix_internal_imports() -> None:
    """为移动后的类补充跨子包 import。"""
    java_files = list((SRC_ROOT / "main/java").rglob("*.java"))
    java_files += list((SRC_ROOT / "test/java").rglob("*.java"))
    names = sorted(MOVES.keys(), key=len, reverse=True)

    for path in java_files:
        text = path.read_text(encoding="utf-8")
        pkg_match = re.search(r"^package\s+([\w.]+);\s*$", text, re.MULTILINE)
        if not pkg_match:
            continue
        current_pkg = pkg_match.group(1)

        needed: list[str] = []
        for class_name in names:
            class_pkg = target_package(MOVES[class_name])
            if class_pkg == current_pkg:
                continue
            import_line = f"import {class_pkg}.{class_name};"
            if import_line in text:
                continue
            if re.search(rf"(?<!\.)\b{re.escape(class_name)}\b", text):
                needed.append(import_line)

        if not needed:
            continue

        needed = sorted(set(needed))
        # 插入到 package 声明之后
        insert_at = pkg_match.end()
        block = "\n" + "\n".join(needed)
        text = text[:insert_at] + block + text[insert_at:]
        path.write_text(text, encoding="utf-8")
        print(f"IMPORTS added: {path.relative_to(ROOT)} ({len(needed)})")


def main() -> None:
    move_sources()
    update_imports()
    fix_internal_imports()
    fix_wildcard_files()
    print("Done.")


if __name__ == "__main__":
    main()
