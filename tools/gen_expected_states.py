"""
Emit the full block state space for the architectural blocks, for validate_resources.py --states.

This mirrors what the Java registry declares. Keep the three lists below in step with
BlockRegistry: a block registered as MirrorableWindowBlock belongs in MIRRORABLE_WINDOWS, and a
plain DoubleWallBlock belongs in WALLS. If they drift, the validator reports states with no
variant, which is the same thing the game would log as a missing model.

Usage:
    python tools/gen_expected_states.py > build/expected_states.json
    python tools/validate_resources.py --states build/expected_states.json --scope structure/plaster
"""
import itertools
import json
import sys

DIRECTIONS = ["north", "east", "south", "west"]
SHAPES = ["straight", "corner", "t_junction"]
BOOLEANS = ["false", "true"]
HALVES = ["lower", "upper"]

# DoubleWallBlock: facing x shape x branch_right x half
WALLS = [
    "plaster_ornate_wall_upper", "plaster_ornate_wall_1", "plaster_ornate_wall_2",
    "plaster_small_window", "plaster_wall_support_diagonal_east",
    "plaster_wall_support_diagonal_south", "plaster_wall_support_open",
    "plaster_wall_blank", "plaster_wall_and_support_blank", "plaster_archway",
    "plaster_and_stone_window",
    "ornate_sandstone_wall", "regular_sandstone_wall", "sandstone_block_wall",
    "ornate_sandstone_window", "sandstone_window", "sandstone_post",
    "ornate_sandstone_post", "sandstone_battlement", "sandstone_column",
]

# MirrorableWindowBlock: the above, plus mirrored
MIRRORABLE_WINDOWS = ["plaster_wall_large_window", "ornate_wall_large_window"]

# WoodSupportFloorBlock and BannisterBlock: facing x shape x branch_right
FLOORS = ["wood_support_floor", "bannister"]

# Blocks with facing only
SIMPLE_FACING = []


def main():
    states = {}

    for name in WALLS:
        states[name] = [
            ["facing=" + f, "shape=" + s, "branch_right=" + b, "half=" + h]
            for f, s, b, h in itertools.product(DIRECTIONS, SHAPES, BOOLEANS, HALVES)
        ]

    for name in MIRRORABLE_WINDOWS:
        states[name] = [
            ["facing=" + f, "shape=" + s, "branch_right=" + b, "mirrored=" + m, "half=" + h]
            for f, s, b, m, h in itertools.product(DIRECTIONS, SHAPES, BOOLEANS, BOOLEANS, HALVES)
        ]

    for name in FLOORS:
        states[name] = [
            ["facing=" + f, "shape=" + s, "branch_right=" + b]
            for f, s, b in itertools.product(DIRECTIONS, SHAPES, BOOLEANS)
        ]

    for name in SIMPLE_FACING:
        states[name] = [["facing=" + f] for f in DIRECTIONS]

    json.dump(states, sys.stdout, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
