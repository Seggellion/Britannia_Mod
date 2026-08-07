"""
Serialise Minecraft model JSON the way Blockbench does, so generated files diff cleanly against
hand-authored ones: tab indent, and short leaf containers (coordinate triples, uv rects, the
rotation object, a single face) kept on one line.
"""
import json


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


def write(path, value):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(dumps(value))
        fh.write("\n")
