"""Minimal, type-faithful NBT reader and writer for Minecraft structure files.

Written for the housing authoring pass, where a structure has to be edited without opening
Minecraft. Correctness here rests on one property, which `roundtrips()` checks and every
caller should assert before writing anything: reading a file and writing it straight back
must reproduce the original bytes exactly. A codec that cannot do that has lost information
somewhere, and a structure edited through it would be wrong in ways nobody would notice until
the house was placed.

Tags are represented as (type_id, payload) pairs so that a TAG_Byte never comes back as a
TAG_Int on the way out. Compounds are dicts of name -> tag, preserving insertion order;
lists are (element_type_id, [payload, ...]).
"""
import gzip
import struct

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

_FIXED = {
    TAG_BYTE: ('>b', 1),
    TAG_SHORT: ('>h', 2),
    TAG_INT: ('>i', 4),
    TAG_LONG: ('>q', 8),
    TAG_FLOAT: ('>f', 4),
    TAG_DOUBLE: ('>d', 8),
}


class _Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def take(self, n):
        v = self.b[self.i:self.i + n]
        self.i += n
        return v

    def u1(self):
        v = self.b[self.i]
        self.i += 1
        return v

    def u2(self):
        v = struct.unpack_from('>H', self.b, self.i)[0]
        self.i += 2
        return v

    def i4(self):
        v = struct.unpack_from('>i', self.b, self.i)[0]
        self.i += 4
        return v

    def string(self):
        return self.take(self.u2()).decode('utf-8', 'surrogatepass')


def _read_payload(r, tag):
    if tag in _FIXED:
        fmt, size = _FIXED[tag]
        return struct.unpack('>' + fmt[1], r.take(size))[0]
    if tag == TAG_BYTE_ARRAY:
        return bytearray(r.take(r.i4()))
    if tag == TAG_STRING:
        return r.string()
    if tag == TAG_LIST:
        element = r.u1()
        count = r.i4()
        if count <= 0:
            # An empty list still records an element type, and it is not always TAG_End.
            return (element, [])
        return (element, [_read_payload(r, element) for _ in range(count)])
    if tag == TAG_COMPOUND:
        out = {}
        while True:
            child = r.u1()
            if child == TAG_END:
                return out
            name = r.string()
            out[name] = (child, _read_payload(r, child))
    if tag == TAG_INT_ARRAY:
        return [r.i4() for _ in range(r.i4())]
    if tag == TAG_LONG_ARRAY:
        count = r.i4()
        return [struct.unpack('>q', r.take(8))[0] for _ in range(count)]
    raise ValueError('unknown tag id %d at offset %d' % (tag, r.i))


def _write_payload(out, tag, value):
    if tag in _FIXED:
        fmt, _ = _FIXED[tag]
        out += struct.pack(fmt, value)
        return
    if tag == TAG_BYTE_ARRAY:
        out += struct.pack('>i', len(value)) + bytes(value)
        return
    if tag == TAG_STRING:
        encoded = value.encode('utf-8', 'surrogatepass')
        out += struct.pack('>H', len(encoded)) + encoded
        return
    if tag == TAG_LIST:
        element, items = value
        out += bytes([element]) + struct.pack('>i', len(items))
        for item in items:
            _write_payload(out, element, item)
        return
    if tag == TAG_COMPOUND:
        for name, (child, payload) in value.items():
            encoded = name.encode('utf-8', 'surrogatepass')
            out += bytes([child]) + struct.pack('>H', len(encoded)) + encoded
            _write_payload(out, child, payload)
        out += bytes([TAG_END])
        return
    if tag == TAG_INT_ARRAY:
        out += struct.pack('>i', len(value))
        for item in value:
            out += struct.pack('>i', item)
        return
    if tag == TAG_LONG_ARRAY:
        out += struct.pack('>i', len(value))
        for item in value:
            out += struct.pack('>q', item)
        return
    raise ValueError('unknown tag id %d' % tag)


def parse(raw):
    """(root_name, compound) from uncompressed NBT bytes."""
    r = _Reader(raw)
    if r.u1() != TAG_COMPOUND:
        raise ValueError('not an NBT compound')
    return r.string(), _read_payload(r, TAG_COMPOUND)


def serialise(root_name, compound):
    out = bytearray([TAG_COMPOUND])
    encoded = root_name.encode('utf-8', 'surrogatepass')
    out += struct.pack('>H', len(encoded)) + encoded
    _write_payload(out, TAG_COMPOUND, compound)
    return bytes(out)


def read_file(path):
    """(root_name, compound, was_gzipped) -- structure exports are gzip, shipped data copies raw."""
    raw = open(path, 'rb').read()
    gzipped = raw[:2] == b'\x1f\x8b'
    name, compound = parse(gzip.decompress(raw) if gzipped else raw)
    return name, compound, gzipped


def roundtrips(path):
    """True when reading and rewriting this file reproduces its uncompressed bytes exactly."""
    raw = open(path, 'rb').read()
    plain = gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw
    name, compound = parse(plain)
    return serialise(name, compound) == plain
