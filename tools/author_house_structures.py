"""Apply the housing authoring corrections to structure-block exports, in place.

These are edits that would otherwise mean opening the sandbox world, rebuilding by hand and
re-exporting. Doing them here makes them reviewable and repeatable: run it again after a fresh
export from Minecraft and the same corrections are reapplied.

Every edit is idempotent -- a second run reports "already correct" and writes nothing -- and
nothing is written until the whole file has been rebuilt and checked, so a failed run leaves
the export untouched.

    python tools/author_house_structures.py "<export directory>"
    python tools/author_house_structures.py "<export directory>" --check

--check verifies without writing.

What it does and why
--------------------
stone_keep
    Its two door leaves were vanilla minecraft:iron_door. A vanilla iron door carries no
    block entity, so it can never hold a lock, take a house key, or answer to the privacy
    system -- the keep would have shipped as the one house you cannot lock. They become
    britannia_mod:lockable_metal_door, the same block the castle's four double doors use,
    with the same {Locked:1} block-entity data the castle stores.

    The two stone pressure plates just inside the doorway and the two stone buttons on the
    wall either side of it stay exactly where the keep was authored with them. They were the
    iron doors' opening mechanism, and against a lockable door they would once have opened a
    locked keep on redstone from outside -- but that is a housing rule, not a structure
    problem, and LockableDoorBlock now refuses a signal while the door is locked. A house is
    allowed to have a pressure plate in its own doorway.

large_patio
    Its interior floor is 166 britannia_mod:wooden_board_floor. That is decorative flooring
    standing in for the structural floor layer a house ships with, which is a distinction the
    housing system needs to make: the perimeter foundation stays protected, and the interior
    floor is what an owner cuts through to reach a basement. They become
    britannia_mod:wooden_board_floor_foundation, which is the same boards, same models, same
    facing and variation -- only the identity differs. No block moves and no layer is added.
"""
import argparse
import gzip
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_io
from nbt_io import TAG_BYTE, TAG_COMPOUND, TAG_STRING

KEEP_DOOR_FROM = 'minecraft:iron_door'
KEEP_DOOR_TO = 'britannia_mod:lockable_metal_door'
LOCKABLE_DOOR_BE = 'britannia_mod:lockable_door'
# Authored, and kept. Named here only so the verifier can prove they were not touched.
KEEP_REDSTONE = ('minecraft:stone_pressure_plate', 'minecraft:stone_button')

PATIO_FLOOR_FROM = 'britannia_mod:wooden_board_floor'
PATIO_FLOOR_TO = 'britannia_mod:wooden_board_floor_foundation'


# --------------------------------------------------------------------------- helpers

def palette_names(compound):
    return [entry['Name'][1] for entry in compound['palette'][1][1]]


def block_positions(compound, names, wanted):
    out = []
    for block in compound['blocks'][1][1]:
        if names[block['state'][1]] in wanted:
            out.append(tuple(block['pos'][1][1]))
    return out


def rename_palette(compound, names, old, new):
    renamed = 0
    for index, entry in enumerate(compound['palette'][1][1]):
        if entry['Name'][1] == old:
            entry['Name'] = (TAG_STRING, new)
            names[index] = new
            renamed += 1
    return renamed


# --------------------------------------------------------------------------- edits

def author_keep(compound):
    names = palette_names(compound)
    notes = []

    if KEEP_DOOR_FROM in names:
        renamed = rename_palette(compound, names, KEEP_DOOR_FROM, KEEP_DOOR_TO)
        notes.append('renamed %d palette entries %s -> %s' % (renamed, KEEP_DOOR_FROM, KEEP_DOOR_TO))
    elif KEEP_DOOR_TO not in names:
        raise SystemExit('stone_keep has neither an iron door nor a lockable metal door')

    # Give every leaf the block-entity data the castle's doors carry.
    stamped = 0
    for block in compound['blocks'][1][1]:
        if names[block['state'][1]] != KEEP_DOOR_TO:
            continue
        existing = block.get('nbt')
        if existing is not None and existing[1].get('id', (None, None))[1] == LOCKABLE_DOOR_BE:
            continue
        block['nbt'] = (TAG_COMPOUND, {
            'Locked': (TAG_BYTE, 1),
            'id': (TAG_STRING, LOCKABLE_DOOR_BE),
        })
        stamped += 1
    if stamped:
        notes.append('stamped {Locked:1} block-entity data onto %d door halves' % stamped)

    return notes


def author_patio(compound):
    names = palette_names(compound)
    if PATIO_FLOOR_FROM not in names:
        return []

    # The foundation block declares variation 0-13; the floor declares 0-14 but ships only
    # fourteen textures. Refuse rather than write a state the block cannot represent.
    for entry in compound['palette'][1][1]:
        if entry['Name'][1] != PATIO_FLOOR_FROM:
            continue
        properties = entry.get('Properties')
        variation = properties[1].get('variation', (None, '0'))[1] if properties else '0'
        if int(variation) > 13:
            raise SystemExit('large_patio uses wooden_board_floor variation %s, which the '
                             'foundation block cannot represent' % variation)

    renamed = rename_palette(compound, names, PATIO_FLOOR_FROM, PATIO_FLOOR_TO)
    used = sum(1 for block in compound['blocks'][1][1] if names[block['state'][1]] == PATIO_FLOOR_TO)
    return ['renamed %d palette entries (%d blocks) %s -> %s'
            % (renamed, used, PATIO_FLOOR_FROM, PATIO_FLOOR_TO)]


EDITS = {
    'stone_keep.nbt': author_keep,
    'large_patio.nbt': author_patio,
}


# --------------------------------------------------------------------------- verification

def verify(path, before_compound, after_compound):
    """Nothing may move, and nothing may change that the edit did not intend."""
    problems = []

    if before_compound['size'][1][1] != after_compound['size'][1][1]:
        problems.append('structure size changed')

    before_blocks = before_compound['blocks'][1][1]
    after_blocks = after_compound['blocks'][1][1]
    if len(before_blocks) != len(after_blocks):
        problems.append('block count changed: %d -> %d' % (len(before_blocks), len(after_blocks)))
        return problems

    before_names = palette_names(before_compound)
    after_names = palette_names(after_compound)
    for index, (old, new) in enumerate(zip(before_blocks, after_blocks)):
        if old['pos'][1][1] != new['pos'][1][1]:
            problems.append('block %d moved' % index)
            break

    name = os.path.basename(path)
    if name == 'stone_keep.nbt':
        if KEEP_DOOR_FROM in after_names:
            problems.append('an iron door survived')
        doors = block_positions(after_compound, after_names, {KEEP_DOOR_TO})
        if len(doors) != 4:
            problems.append('expected 4 lockable metal door halves, found %d' % len(doors))
        was = set(block_positions(before_compound, before_names, {KEEP_DOOR_FROM, KEEP_DOOR_TO}))
        if set(doors) != was:
            problems.append('door positions changed')
        for block in after_blocks:
            if after_names[block['state'][1]] != KEEP_DOOR_TO:
                continue
            if block.get('nbt', (None, {}))[1].get('id', (None, None))[1] != LOCKABLE_DOOR_BE:
                problems.append('a door leaf carries no lockable block-entity data')
                break
        before_redstone = set(block_positions(before_compound, before_names, set(KEEP_REDSTONE)))
        after_redstone = set(block_positions(after_compound, after_names, set(KEEP_REDSTONE)))
        if before_redstone != after_redstone:
            problems.append("the keep authored redstone was disturbed: %s"
                            % sorted(before_redstone ^ after_redstone))

    if name == 'large_patio.nbt':
        if PATIO_FLOOR_FROM in after_names:
            problems.append('plain wooden board floor survived')
        floors = block_positions(after_compound, after_names, {PATIO_FLOOR_TO})
        was = set(block_positions(before_compound, before_names, {PATIO_FLOOR_FROM, PATIO_FLOOR_TO}))
        if set(floors) != was:
            problems.append('floor positions changed')
        if any(pos[1] != 0 for pos in floors):
            problems.append('floor foundation appeared above y=0')

    return problems


# --------------------------------------------------------------------------- driver

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('export_dir', help='structure-block export directory')
    parser.add_argument('--check', action='store_true', help='verify without writing')
    args = parser.parse_args()

    exit_code = 0
    for filename, edit in sorted(EDITS.items()):
        path = os.path.join(args.export_dir, filename)
        if not os.path.exists(path):
            print('%-20s MISSING at %s' % (filename, path))
            exit_code = 1
            continue

        if not nbt_io.roundtrips(path):
            print('%-20s REFUSED: the codec cannot reproduce this file byte for byte' % filename)
            exit_code = 1
            continue

        original = open(path, 'rb').read()
        gzipped = original[:2] == b'\x1f\x8b'
        plain = gzip.decompress(original) if gzipped else original
        before_name, before = nbt_io.parse(plain)
        _, working = nbt_io.parse(plain)

        notes = edit(working)
        rebuilt = nbt_io.serialise(before_name, working)

        if rebuilt == plain:
            print('%-20s already correct' % filename)
            continue

        problems = verify(path, before, nbt_io.parse(rebuilt)[1])
        if problems:
            print('%-20s REFUSED:' % filename)
            for problem in problems:
                print('    %s' % problem)
            exit_code = 1
            continue

        for note in notes:
            print('%-20s %s' % (filename, note))

        if args.check:
            print('%-20s (--check: not written)' % filename)
            continue

        payload = gzip.compress(rebuilt, mtime=0) if gzipped else rebuilt
        with open(path, 'wb') as handle:
            handle.write(payload)
        print('%-20s written (%d bytes)' % (filename, len(payload)))

    return exit_code


if __name__ == '__main__':
    sys.exit(main())
