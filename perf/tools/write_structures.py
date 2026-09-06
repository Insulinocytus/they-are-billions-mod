#!/usr/bin/env python3
"""Write gzip NBT structure files for the performance scenarios."""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10

REPO = Path(__file__).resolve().parents[2]
OPEN_FIELD = REPO / "perf/scenarios/open-field/datapack/data/theyarebillions_perf/structure"
WALLED = REPO / "perf/scenarios/walled-mountain/datapack/data/theyarebillions_perf/structure"


class NbtWriter:
    def __init__(self) -> None:
        self.buf = bytearray()

    def bytes(self) -> bytes:
        return bytes(self.buf)

    def _name(self, name: str) -> None:
        encoded = name.encode("utf-8")
        self.buf.extend(struct.pack(">H", len(encoded)))
        self.buf.extend(encoded)

    def start_named(self, tag: int, name: str) -> None:
        self.buf.append(tag)
        self._name(name)

    def end(self) -> None:
        self.buf.append(TAG_END)

    def int_tag(self, name: str, value: int) -> None:
        self.start_named(TAG_INT, name)
        self.buf.extend(struct.pack(">i", value))

    def string_tag(self, name: str, value: str) -> None:
        self.start_named(TAG_STRING, name)
        self._name(value)

    def start_list(self, name: str, tag: int, count: int) -> None:
        self.start_named(TAG_LIST, name)
        self.buf.append(tag)
        self.buf.extend(struct.pack(">i", count))

    def start_compound(self, name: str) -> None:
        self.start_named(TAG_COMPOUND, name)

    def unnamed_int(self, value: int) -> None:
        self.buf.extend(struct.pack(">i", value))


def write_structure(
        path: Path,
        size: tuple[int, int, int],
        palette: list[str],
        blocks: list[tuple[int, int, int, int]],
) -> None:
    nbt = NbtWriter()
    nbt.start_compound("")
    nbt.int_tag("DataVersion", 3955)
    nbt.start_list("size", TAG_INT, 3)
    for value in size:
        nbt.unnamed_int(value)
    nbt.start_list("palette", TAG_COMPOUND, len(palette))
    for block_id in palette:
        nbt.string_tag("Name", block_id)
        nbt.end()
    nbt.start_list("blocks", TAG_COMPOUND, len(blocks))
    for x, y, z, state in blocks:
        nbt.start_list("pos", TAG_INT, 3)
        nbt.unnamed_int(x)
        nbt.unnamed_int(y)
        nbt.unnamed_int(z)
        nbt.int_tag("state", state)
        nbt.end()
    nbt.start_list("entities", TAG_COMPOUND, 0)
    nbt.end()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(nbt.bytes(), mtime=0))


def origin_blocks() -> list[tuple[int, int, int, int]]:
    blocks = []
    for x in range(5):
        for z in range(5):
            state = 1 if x == 2 and z == 2 else 0
            blocks.append((x, 0, z, state))
    return blocks


def walled_compound_blocks() -> list[tuple[int, int, int, int]]:
    size = 33
    last = size - 1
    blocks: list[tuple[int, int, int, int]] = []
    dig: set[tuple[int, int, int]] = set()
    for i in range(16):
        coord = 1 + i * 2
        dig.add((coord, 2, 0))
        dig.add((coord, 2, last))
        dig.add((0, 2, coord))
        dig.add((last, 2, coord))
    for x in range(size):
        for z in range(size):
            if x not in (0, last) and z not in (0, last):
                continue
            for y in range(6):
                state = 1 if (x, y, z) in dig else 0
                blocks.append((x, y, z, state))
    blocks.append((16, 0, 16, 2))
    return blocks


def mountain_blocks() -> list[tuple[int, int, int, int]]:
    blocks = []
    height = 21
    for y in range(height):
        radius = height - 1 - y
        span = 2 * radius + 1
        origin = 20 - radius
        for dx in range(span):
            for dz in range(span):
                blocks.append((origin + dx, y, origin + dz, 0))
    return blocks


def main() -> None:
    compound = walled_compound_blocks()
    obsidian = sum(1 for block in compound if block[3] == 1)
    if obsidian != 64:
        raise SystemExit(f"expected 64 dig points, got {obsidian}")
    write_structure(
        OPEN_FIELD / "origin.nbt",
        (5, 1, 5),
        ["minecraft:smooth_stone", "minecraft:gold_block"],
        origin_blocks(),
    )
    write_structure(
        WALLED / "walled_compound.nbt",
        (33, 6, 33),
        ["minecraft:cobblestone", "minecraft:obsidian", "minecraft:gold_block"],
        compound,
    )
    write_structure(
        WALLED / "mountain.nbt",
        (41, 21, 41),
        ["minecraft:stone"],
        mountain_blocks(),
    )
    print("wrote", OPEN_FIELD / "origin.nbt")
    print("wrote", WALLED / "walled_compound.nbt")
    print("wrote", WALLED / "mountain.nbt")


if __name__ == "__main__":
    main()
