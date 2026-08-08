"""
Serialise Minecraft model JSON the way Blockbench does, so generated files diff cleanly against
hand-authored ones: tab indent, and short leaf containers (coordinate triples, uv rects, the
rotation object, a single face) kept on one line.
"""
import collections
import json
import os


def _is_leaf(value):
    if isinstance(value, list):
        return all(isinstance(v, (int, float, str)) for v in value)
    if isinstance(value, dict):
        return all(not isinstance(v, (list, dict)) or _is_leaf(v) for v in value.values()) \
            and len(json.dumps(value)) <= 110
    return False


def _num(value):
    if isinstance(value, float) and value == int(value):
        return str(int(value))
    return json.dumps(value)


def _inline(value):
    if isinstance(value, list):
        return "[" + ", ".join(_inline(v) for v in value) + "]"
    if isinstance(value, dict):
        return "{" + ", ".join('%s: %s' % (json.dumps(k), _inline(v)) for k, v in value.items()) + "}"
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return _num(value)
    return json.dumps(value)


def dumps(value, level=0):
    pad = "\t" * level
    inner = "\t" * (level + 1)

    if _is_leaf(value):
        return _inline(value)

    if isinstance(value, list):
        if not value:
            return "[]"
        body = ",\n".join(inner + dumps(v, level + 1) for v in value)
        return "[\n%s\n%s]" % (body, pad)

    if isinstance(value, dict):
        if not value:
            return "{}"
        body = ",\n".join('%s%s: %s' % (inner, json.dumps(k), dumps(v, level + 1))
                          for k, v in value.items())
        return "{\n%s\n%s}" % (body, pad)

    return _inline(value)


def generated(data, source, script):
    """
    Stamp a generated model so anyone opening it knows edits here are thrown away.

    Minecraft's model deserialiser reads only the keys it knows about and ignores the rest, so the
    banner is inert at runtime.
    """
    out = collections.OrderedDict()
    out["comment"] = ("GENERATED FILE - do not edit. Produced by tools/%s from %s. "
                      "Edit that source model and re-run the generator; changes made here are "
                      "overwritten on the next run." % (script, source))
    for key, value in data.items():
        if key != "comment":
            out[key] = value
    return out


def is_hand_authored(path):
    """
    True when a model exists but does not carry the generated banner - i.e. a human made it.

    Blockbench strips the banner when it re-saves a file, so opening a generated model, editing it
    and saving is all it takes to claim ownership of it. Generators must leave those alone.
    """
    if not os.path.exists(path):
        return False
    try:
        with open(path, encoding="utf-8") as fh:
            return "GENERATED FILE" not in (json.load(fh).get("comment") or "")
    except (ValueError, OSError):
        return False


def write(path, value, skip_if_hand_authored=False):
    """
    Write a model. With skip_if_hand_authored, an existing model that a human has taken over is
    left untouched and False is returned, so hand-authored corners survive a regeneration.
    """
    if skip_if_hand_authored and is_hand_authored(path):
        return False
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(dumps(value))
        fh.write("\n")
    return True
