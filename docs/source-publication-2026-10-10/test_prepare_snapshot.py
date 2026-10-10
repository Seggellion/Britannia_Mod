"""Focused checks for the public README/licensing export boundary; no game build."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('prepare_snapshot', Path(__file__).with_name('prepare_snapshot.py'))
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)

class PublicFilesTests(unittest.TestCase):
    def test_root_readme_precedes_markdown_and_historical_rules(self):
        self.assertIsNone(exporter.exclusion('README.md', {'README.md': 'historical Markdown exclusion'}))
        for path in ('docs/README.md', 'src/README.md', 'internal.MD'):
            self.assertIsNotNone(exporter.exclusion(path, {}))

    def test_root_licensing_precedes_historical_and_markdown_rules(self):
        for path in ('TEMPLATE_LICENSE.txt', 'LICENSE', 'LICENSE.md', 'COPYING.txt', 'NOTICE.rst'):
            self.assertIsNone(exporter.exclusion(path, {path: 'historic exclusion'}))
        self.assertIsNotNone(exporter.exclusion('docs/LICENSE.md', {}))
        self.assertIsNotNone(exporter.exclusion('license-notes.md', {}))

    def test_missing_readme_fails(self):
        with self.assertRaisesRegex(ValueError, 'README.md'):
            exporter.require_public_files({}, {})

    def test_existing_public_license_cannot_be_removed_or_changed_to_non_blob(self):
        readme = {'README.md': {'type': 'blob'}}
        current = {'TEMPLATE_LICENSE.txt': {'type': 'blob'}}
        with self.assertRaisesRegex(ValueError, 'TEMPLATE_LICENSE.txt'):
            exporter.require_public_files(readme, current)
        with self.assertRaisesRegex(ValueError, 'TEMPLATE_LICENSE.txt'):
            exporter.require_public_files({**readme, 'TEMPLATE_LICENSE.txt': {'type': 'tree'}}, current)
        exporter.require_public_files({**readme, **current}, current)

    def test_actual_committed_source_retains_current_public_legal_files(self):
        source = exporter.entries('HEAD')
        current = exporter.entries('origin/main')
        historical = {'README.md': 'case-insensitive Markdown exclusion', 'TEMPLATE_LICENSE.txt': 'test historical exclusion'}
        kept = {p: v for p, v in source.items() if exporter.exclusion(p, historical) is None}
        exporter.require_public_files(kept, current)
        self.assertEqual(kept['README.md'], source['README.md'])
        self.assertEqual(kept['TEMPLATE_LICENSE.txt'], current['TEMPLATE_LICENSE.txt'])
        self.assertFalse(any(p.startswith('docs/') or (p.lower().endswith('.md') and p != 'README.md' and not exporter.public_licensing_file(p)) for p in kept))

if __name__ == '__main__':
    unittest.main(verbosity=2)
