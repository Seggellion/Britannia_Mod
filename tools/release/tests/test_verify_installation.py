"""Exercise same-version deployment mistakes with real zip files and source ancestry."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('verify_installation', Path(__file__).resolve().parents[1] / 'verify_installation.py')
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


class InstallationPreflightTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.git_temp = tempfile.TemporaryDirectory()
        cls.repository = Path(cls.git_temp.name)
        subprocess.run(['git', 'init', '-q', str(cls.repository)], check=True)
        (cls.repository / 'source.txt').write_text('approved source', encoding='utf-8')
        subprocess.run(['git', '-C', str(cls.repository), 'add', 'source.txt'], check=True)
        subprocess.run(['git', '-C', str(cls.repository), '-c', 'user.name=release-test',
                        '-c', 'user.email=release-test@example.invalid', 'commit', '-qm', 'fixture'], check=True)
        cls.source = subprocess.check_output(['git', '-C', str(cls.repository), 'rev-parse', 'HEAD'], text=True).strip()

    @classmethod
    def tearDownClass(cls):
        cls.git_temp.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.instance = self.root / 'actual-instance'
        self.mods = self.instance / 'mods'
        self.mods.mkdir(parents=True)
        self.candidate = self.root / 'britannia_mod-0.1.8d-all.jar'
        self.make_jar(self.candidate)
        self.installed = self.mods / 'renamed-britannia.jar'
        shutil.copyfile(self.candidate, self.installed)
        self.sha = verify.artifact_identity(self.candidate)['sha256']

    def make_jar(self, path, payload='fixed fence', source=None, dirty='false', bundles=True):
        properties = ('mod.version=0.1.8d\ngit.head=' + (source or self.source)
                      + '\ngit.branch=codex/legitimate-build\ngit.dirty=' + dirty
                      + '\nbuild.timestamp=2026-10-08T22:31:37Z\n')
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr(verify.MOD_CLASS, payload)
            archive.writestr(verify.BUILD_PROPERTIES, properties)
            if bundles:
                for name in sorted(verify.REQUIRED_BUNDLES):
                    archive.writestr(name, 'bundled fixture')

    def run_preflight(self, **changes):
        args = dict(candidate=self.candidate, mods=self.mods, instance=self.instance,
                    expected_sha=self.sha, expected_source=self.source,
                    repository=self.repository, approved_ref='HEAD')
        args.update(changes)
        return verify.preflight(**args)

    def test_matching_files_never_claim_loaded_runtime(self):
        result = self.run_preflight()
        self.assertEqual('FILE_MATCH', result['file_status'])
        self.assertEqual('LOADED_RUNTIME_UNVERIFIED', result['loaded_runtime_status'])

    def test_same_version_different_bytes_is_mismatch(self):
        self.make_jar(self.installed, payload='old normalization')
        result = self.run_preflight()
        self.assertEqual('0.1.8d', result['active_britannia_jars'][0]['mod_version'])
        self.assertEqual('FILE_MISMATCH', result['file_status'])

    def test_wrong_approved_hash(self):
        self.assertIn('Selected candidate hash differs from approved full SHA-256',
                      self.run_preflight(expected_sha='f' * 64)['errors'])

    def test_wrong_approved_source(self):
        self.assertIn('Selected candidate source differs from approved source',
                      self.run_preflight(expected_source='f' * 40)['errors'])

    def test_renamed_duplicate_active_jar(self):
        shutil.copyfile(self.candidate, self.mods / 'another.jar')
        self.assertIn('Expected exactly one active Britannia jar; found 2', self.run_preflight()['errors'])

    def test_disabled_old_jars_do_not_count_as_duplicates(self):
        self.make_jar(self.mods / 'britannia_mod-0.1.8d.old', payload='old normalization')
        result = self.run_preflight()
        self.assertEqual('FILE_MATCH', result['file_status'])
        self.assertEqual(['britannia_mod-0.1.8d.old'], result['disabled_old_files'])

    def test_wrong_instance_is_rejected(self):
        self.assertIn('Target mods directory does not belong to the expected instance',
                      self.run_preflight(instance=self.root / 'other-instance')['errors'])

    def test_dirty_candidate_is_rejected(self):
        self.make_jar(self.candidate, dirty='true')
        self.assertEqual('FILE_MISMATCH', self.run_preflight()['file_status'])

    def test_thin_jar_is_rejected(self):
        self.make_jar(self.candidate, bundles=False)
        self.assertTrue(any('Not a bundled deployable jar' in e for e in self.run_preflight()['errors']))

    def test_missing_active_jar(self):
        self.installed.unlink()
        self.assertIn('Expected exactly one active Britannia jar; found 0', self.run_preflight()['errors'])

    def test_short_hash_is_rejected(self):
        self.assertIn('Expected SHA-256 must have all 64 hexadecimal digits',
                      self.run_preflight(expected_sha=self.sha[:8])['errors'])

    def test_preflight_does_not_change_files(self):
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.run_preflight()
        after = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)


if __name__ == '__main__':
    unittest.main()
