#!/usr/bin/env python3
"""Remove corrupted chunk entries from Minecraft Anvil region (.mca) files."""

from __future__ import annotations

import gzip
import io
import struct
import sys
import zlib
from pathlib import Path


def chunk_coords(region_name: str, index: int) -> tuple[int, int]:
    local_x = index % 32
    local_z = index // 32
    parts = region_name.split(".")
    region_x = int(parts[1])
    region_z = int(parts[2])
    return region_x * 32 + local_x, region_z * 32 + local_z


def read_location(data: bytes, index: int) -> tuple[int, int]:
    offset = index * 4
    value = struct.unpack(">I", data[offset : offset + 4])[0]
    return value >> 8, value & 0xFF


def _read_string(data: bytes, offset: int) -> int:
    if offset + 2 > len(data):
        raise EOFError("string length")
    length = struct.unpack(">H", data[offset : offset + 2])[0]
    offset += 2
    if offset + length > len(data):
        raise EOFError("string payload")
    return offset + length


def _skip_tag_payload(tag_type: int, data: bytes, offset: int) -> int:
    if tag_type == 0:
        return offset
    if tag_type == 1:
        return offset + 1
    if tag_type in (2, 3, 5):
        return offset + (2 if tag_type == 2 else 4 if tag_type == 3 else 4)
    if tag_type in (4, 6):
        return offset + (8 if tag_type == 4 else 8)
    if tag_type == 7:
        if offset + 4 > len(data):
            raise EOFError("byte array length")
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        return offset + 4 + length
    if tag_type == 8:
        return _read_string(data, offset)
    if tag_type == 9:
        if offset + 5 > len(data):
            raise EOFError("list header")
        item_type = data[offset]
        count = struct.unpack(">I", data[offset + 1 : offset + 5])[0]
        offset += 5
        for _ in range(count):
            offset = _skip_tag_payload(item_type, data, offset)
        return offset
    if tag_type == 10:
        while True:
            if offset >= len(data):
                raise EOFError("compound")
            child_type = data[offset]
            offset += 1
            if child_type == 0:
                return offset
            offset = _read_string(data, offset)
            offset = _skip_tag_payload(child_type, data, offset)
    if tag_type == 11:
        if offset + 4 > len(data):
            raise EOFError("int array length")
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        return offset + 4 + length * 4
    if tag_type == 12:
        if offset + 4 > len(data):
            raise EOFError("long array length")
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        return offset + 4 + length * 8
    raise ValueError(f"unknown tag type {tag_type}")


def _validate_nbt(data: bytes) -> None:
    if not data:
        raise EOFError("empty nbt")
    root_type = data[0]
    if root_type != 10:
        raise ValueError(f"expected root compound, got {root_type}")
    offset = _read_string(data, 1)
    _skip_tag_payload(10, data, offset)


def validate_chunk_payload(payload: bytes) -> str | None:
    if len(payload) < 5:
        return "chunk payload too short"
    length, version = struct.unpack(">IB", payload[:5])
    body = payload[5 : 5 + length]
    if len(body) != length:
        return f"truncated payload ({len(body)}/{length} bytes)"
    try:
        if version == 1:
            nbt = gzip.decompress(body)
        elif version == 2:
            nbt = zlib.decompress(body)
        else:
            return f"unsupported compression version {version}"
        _validate_nbt(nbt)
    except Exception as exc:  # noqa: BLE001 - diagnostic tool
        return str(exc)
    return None


def scan_region(path: Path, fix: bool) -> list[tuple[int, int, str]]:
    raw = bytearray(path.read_bytes())
    if len(raw) < 8192:
        return []

    corrupted: list[tuple[int, int, str]] = []
    for index in range(1024):
        sector, count = read_location(raw, index)
        if sector == 0 and count == 0:
            continue

        start = sector * 4096
        end = start + count * 4096
        chunk_x, chunk_z = chunk_coords(path.stem, index)

        if end > len(raw):
            reason = f"header points outside file (sector={sector}, count={count})"
            corrupted.append((chunk_x, chunk_z, reason))
            if fix:
                struct.pack_into(">I", raw, index * 4, 0)
                struct.pack_into(">I", raw, 4096 + index * 4, 0)
            continue

        error = validate_chunk_payload(bytes(raw[start:end]))
        if error is not None:
            corrupted.append((chunk_x, chunk_z, error))
            if fix:
                struct.pack_into(">I", raw, index * 4, 0)
                struct.pack_into(">I", raw, 4096 + index * 4, 0)

    if fix and corrupted:
        backup = path.with_suffix(path.suffix + ".bak")
        if not backup.exists():
            backup.write_bytes(path.read_bytes())
        path.write_bytes(raw)

    return corrupted


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("Usage: fix_corrupt_chunks.py <region-dir> [--fix]", file=sys.stderr)
        return 2

    region_dir = Path(argv[1])
    fix = "--fix" in argv[2:]

    if not region_dir.is_dir():
        print(f"Region directory not found: {region_dir}", file=sys.stderr)
        return 1

    total = 0
    for region_file in sorted(region_dir.glob("*.mca")):
        bad = scan_region(region_file, fix=fix)
        for chunk_x, chunk_z, reason in bad:
            total += 1
            action = "fixed" if fix else "found"
            print(f"{action}: {region_file.name} [{chunk_x}, {chunk_z}] - {reason}")

    if total == 0:
        print("No corrupted chunks found.")
    elif fix:
        print(f"Removed {total} corrupted chunk entr{'y' if total == 1 else 'ies'}.")
    else:
        print(f"Found {total} corrupted chunk entr{'y' if total == 1 else 'ies'}. Re-run with --fix.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
