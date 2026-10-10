"""Scoped generation safety/freshness tests; all writes are in temporary fixtures."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_half_walls as generation

FAMILY = 'plaster_wall_and_support_blank'


class ScopedGenerationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.models = self.root / 'models'
        self.states = self.root / 'blockstates'
        self.models.mkdir()
        self.states.mkdir()
        for source, target in ((Path(generation.MODELS), self.models),
                               (Path(generation.BLOCKSTATES), self.states)):
            for name in (FAMILY + '_corner.json', FAMILY + '_half_corner.json', FAMILY + '_half.json'):
                if (source / name).exists():
                    (target / name).write_bytes((source / name).read_bytes())
        (self.models / 'unrelated.json').write_bytes(b'{"protected": true}\n')
        self.addCleanup(patch.stopall)
        patch.object(generation, 'MODELS', str(self.models)).start()
        patch.object(generation, 'BLOCKSTATES', str(self.states)).start()

    def invoke(self, *args):
        with patch.object(sys, 'argv', ['generate_half_walls.py', '--support-corner-only', *args]), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return generation.main()

    def inventory(self):
        return {p.relative_to(self.root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in self.root.rglob('*') if p.is_file()}

    def test_writes_only_selected_derivative_and_is_idempotent(self):
        before = self.inventory()
        self.assertEqual(0, self.invoke())
        after = self.inventory()
        changed = {p for p in before if before[p] != after[p]}
        self.assertLessEqual(changed, {'models/' + FAMILY + '_half_corner.json'})
        self.assertEqual(set(before), set(after))
        self.assertEqual(0, self.invoke())
        self.assertEqual(after, self.inventory())
        self.assertEqual(0, self.invoke('--check'))
        self.assertEqual(after, self.inventory())

    def test_freshness_detects_damage_without_writing(self):
        self.invoke()
        output = self.models / (FAMILY + '_half_corner.json')
        damaged = json.loads(output.read_text())
        damaged['elements'][0]['to'][1] -= 1
        output.write_text(json.dumps(damaged), encoding='utf-8')
        before = self.inventory()
        self.assertEqual(1, self.invoke('--check'))
        self.assertEqual(before, self.inventory())

    def test_selector_drift_fails_before_writes(self):
        path = self.states / (FAMILY + '_half.json')
        doc = json.loads(path.read_text())
        for value in doc['variants'].values():
            value['model'] = 'britannia_mod:block/unexpected'
        path.write_text(json.dumps(doc), encoding='utf-8')
        before = self.inventory()
        with self.assertRaisesRegex(ValueError, 'selectors'):
            self.invoke()
        self.assertEqual(before, self.inventory())

    def test_missing_cap_or_conflicting_scope_fails_before_writes(self):
        path = self.models / (FAMILY + '_corner.json')
        doc = json.loads(path.read_text())
        del doc['elements'][0]['faces']['up']
        path.write_text(json.dumps(doc), encoding='utf-8')
        before = self.inventory()
        with self.assertRaisesRegex(ValueError, 'top caps'):
            self.invoke()
        self.assertEqual(before, self.inventory())
        with self.assertRaises(SystemExit) as error:
            self.invoke('--offset-return-only')
        self.assertEqual(2, error.exception.code)
        self.assertEqual(before, self.inventory())


if __name__ == '__main__':
    unittest.main()
